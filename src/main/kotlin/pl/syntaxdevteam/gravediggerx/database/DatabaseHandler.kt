package pl.syntaxdevteam.gravediggerx.database

import org.bukkit.Location
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import pl.syntaxdevteam.core.database.Column
import pl.syntaxdevteam.core.database.DatabaseConfig
import pl.syntaxdevteam.core.database.DatabaseManager
import pl.syntaxdevteam.core.database.DatabaseType
import pl.syntaxdevteam.core.database.TableSchema
import pl.syntaxdevteam.core.logging.Logger
import pl.syntaxdevteam.core.plugin.PluginMetaProvider
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.graves.GraveDataStore
import pl.syntaxdevteam.gravediggerx.graves.GraveIdentity
import pl.syntaxdevteam.gravediggerx.graves.GraveSerializer
import pl.syntaxdevteam.gravediggerx.graves.collection.CollectionState
import pl.syntaxdevteam.gravediggerx.graves.collection.CollectionTx
import pl.syntaxdevteam.gravediggerx.graves.collection.CollectionTxStateMachine
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.SQLException
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("RedundantSamConstructor", "CanBeParameter")
class DatabaseHandler private constructor(
    private val coreLogger: Logger,
    private val logger: LogSink,
    private val pluginName: String,
    private val dataFolder: java.io.File,
    private val configString: (String) -> String?,
    private val configInt: (String) -> Int,
    private val fileStore: GraveStore,
    private val onStorageIoError: () -> Unit
) {
    data class StuckCollectionTx(
        val txId: UUID,
        val graveId: UUID,
        val collectorId: UUID,
        val state: CollectionState,
        val updatedAt: Long,
        val expiresAt: Long
    )

    private data class TxUpdateResult(
        val changed: Int,
        val graveIds: Set<UUID>
    )

    constructor(plugin: GraveDiggerX) : this(
        coreLogger = plugin.logger,
        logger = LogSink(
            debug = { plugin.logger.debug(it) },
            warning = { plugin.logger.warning(it) },
            error = { plugin.logger.err(it) }
        ),
        pluginName = plugin.name,
        dataFolder = plugin.dataFolder,
        configString = { key -> plugin.config.getString(key) },
        configInt = { key -> plugin.config.getInt(key) },
        fileStore = GraveStoreAdapter(GraveDataStore(plugin) { plugin.runtimeMetrics.incrementStorageIoError() }),
        onStorageIoError = { plugin.runtimeMetrics.incrementStorageIoError() }
    )

    internal constructor(
        pluginName: String,
        dataFolder: java.io.File,
        configString: (String) -> String?,
        configInt: (String) -> Int,
        debug: (String) -> Unit = {},
        warning: (String) -> Unit = {},
        error: (String) -> Unit = {}
    ) : this(
        coreLogger = Logger(object : PluginMetaProvider {
            override val name: String = pluginName
            override val version: String = "test"
            override val debugMode: Boolean = false
        }),
        logger = LogSink(debug, warning, error),
        pluginName = pluginName,
        dataFolder = dataFolder,
        configString = configString,
        configInt = configInt,
        fileStore = GraveStoreAdapter(GraveDataStoreShim(dataFolder, LogSink(debug, warning, error)) {}),
        onStorageIoError = {}
    )

    private enum class StorageBackend { FILE, SQL }

    private val configuredType =
        configString("database.type")?.trim()?.lowercase(Locale.ROOT) ?: "yaml"

    private val storageBackend = resolveBackend(configuredType)
    private val dbType: DatabaseType? = parseDatabaseType(configuredType)
    private val dbConfig = if (storageBackend == StorageBackend.SQL) {
        dbType?.let {
            val databaseName = resolveDatabaseName(
                configString("database.sql.dbname")
            )
            DatabaseConfig(
                type = it,
                host = configString("database.sql.host") ?: "localhost",
                port = resolvePort(it, configInt("database.sql.port")),
                database = databaseName,
                username = configString("database.sql.username") ?: "root",
                password = configString("database.sql.password") ?: "",
                filePath = resolveDatabaseFilePath(it, databaseName)
            )
        }
    } else {
        null
    }
    private val dbManager = dbConfig?.let { DatabaseManager(it, coreLogger) }
    private val sqlOperational = AtomicBoolean(false)
    private val claimsFilePath = dataFolder.toPath().resolve("collection_claims.json")
    private val collectionTxFilePath = dataFolder.toPath().resolve("collection_tx.json")
    private val fileClaimsLock = Any()
    private val fileClaims = ConcurrentHashMap.newKeySet<String>()
    private val fileTxLock = Any()
    private val fileCollectionTx = ConcurrentHashMap<UUID, CollectionTx>()
    private val txAuditLogPath = dataFolder.toPath().resolve("tx_audit.log")
    @Volatile
    private var fileTxStateMachine: CollectionTxStateMachine? = null

    fun connect() {
        if (storageBackend != StorageBackend.SQL) {
            logger.debug("Using file-based storage backend ($configuredType), SQL connection skipped.")
            return
        }

        if (ensureSqlReady(logFailure = false)) {
            logger.debug("Using ${dbType?.name?.lowercase(Locale.ROOT)} database backend.")
        } else {
            logger.err("Failed to connect to database, continuing with file storage.")
        }
    }

    fun close() {
        if (storageBackend != StorageBackend.SQL) {
            sqlOperational.set(false)
            return
        }

        try {
            dbManager?.close()
        } catch (e: Exception) {
            logger.warning("Failed to close database connection: ${e.message}")
        } finally {
            sqlOperational.set(false)
        }
    }

    fun ensureSchema() {
        if (storageBackend != StorageBackend.SQL) {
            return
        }

        val manager = dbManager ?: return
        if (!ensureSqlReady()) return
        val schema = TableSchema(
            "graves",
            listOf(
                Column("id", idDefinition()),
                Column("graveKey", "VARCHAR(128) UNIQUE"),
                Column("ownerId", "VARCHAR(36)"),
                Column("ownerName", "VARCHAR(64)"),
                Column("world", "VARCHAR(64)"),
                Column("x", "DOUBLE"),
                Column("y", "DOUBLE"),
                Column("z", "DOUBLE"),
                Column("yaw", "FLOAT"),
                Column("pitch", "FLOAT"),
                Column("createdAt", "BIGINT"),
                Column("storedXp", "INT"),
                Column("payload", payloadColumnType())
            )
        )

        try {
            manager.createTable(schema)
            manager.createTable(
                TableSchema(
                    "grave_collection_claims",
                    listOf(
                        Column("id", idDefinition()),
                        Column("claimKey", "VARCHAR(192) UNIQUE"),
                        Column("graveKey", "VARCHAR(128)"),
                        Column("claimedAt", "BIGINT")
                    )
                )
            )
            manager.createTable(
                TableSchema(
                    "grave_collection_tx",
                    listOf(
                        Column("id", idDefinition()),
                        Column("txId", "VARCHAR(36) UNIQUE"),
                        Column("graveId", "VARCHAR(36)"),
                        Column("collectorId", "VARCHAR(36)"),
                        Column("state", "VARCHAR(16)"),
                        Column("version", "INT"),
                        Column("updatedAt", "BIGINT"),
                        Column("expiresAt", "BIGINT"),
                        Column("lastError", "TEXT")
                    )
                )
            )
        } catch (e: Exception) {
            sqlOperational.set(false)
            logger.err("Failed to create graves table, falling back to file storage. ${e.message}")
        }
    }

    fun replaceAllGraves(graves: Collection<Grave>) {
        if (storageBackend != StorageBackend.SQL) {
            fileStore.saveAllGraves(graves)
            return
        }

        val manager = dbManager ?: return
        if (!ensureSqlReady()) {
            fileStore.saveAllGraves(graves)
            return
        }

        try {
            manager.execute("DELETE FROM graves")
            graves.forEach { grave ->
                val record = grave.toRecord()
                manager.execute(
                    """
                    INSERT INTO graves (
                        graveKey, ownerId, ownerName, world, x, y, z, yaw, pitch, createdAt, storedXp, payload
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    record.graveKey,
                    record.ownerId,
                    record.ownerName,
                    record.world,
                    record.x,
                    record.y,
                    record.z,
                    record.yaw,
                    record.pitch,
                    record.createdAt,
                    record.storedXp,
                    record.payload
                )
            }
        } catch (e: Exception) {
            sqlOperational.set(false)
            logger.err("Failed to save graves in database, switching to file storage. ${e.message}")
            fileStore.saveAllGraves(graves)
        }
    }

    fun loadAllGraves(): List<Grave> {
        if (storageBackend != StorageBackend.SQL) {
            return fileStore.loadAllGraves()
        }

        val manager = dbManager ?: return fileStore.loadAllGraves()
        if (!ensureSqlReady()) {
            return fileStore.loadAllGraves()
        }

        return loadAllGravesFromSql(manager) ?: fileStore.loadAllGraves()
    }

    fun writeGravesToJsonIfConfigured(graves: Collection<Grave>) {
        if (isJsonBackend()) {
            fileStore.saveAllGraves(graves)
        } else {
            replaceAllGraves(graves)
        }
    }

    fun isJsonBackend(): Boolean = configuredType == "json"

    fun tryAcquireCollectionClaim(grave: Grave): Boolean {
        val claimKey = collectionClaimKey(grave)
        return if (storageBackend == StorageBackend.SQL) {
            tryAcquireCollectionClaimSql(claimKey, locationKey(grave.location))
        } else {
            tryAcquireCollectionClaimFile(claimKey)
        }
    }

    fun releaseCollectionClaim(grave: Grave) {
        releaseCollectionClaimByGraveId(grave.graveId)
    }

    fun releaseCollectionClaimByGraveId(graveId: UUID) {
        val claimKey = graveId.toString()
        if (storageBackend == StorageBackend.SQL) {
            releaseCollectionClaimSql(claimKey)
        } else {
            releaseCollectionClaimFile(claimKey)
        }
    }

    fun clearAllCollectionClaims() {
        if (storageBackend == StorageBackend.SQL) {
            val manager = dbManager ?: return
            if (!ensureSqlReady(logFailure = false)) return
            try {
                manager.execute("DELETE FROM grave_collection_claims")
            } catch (e: Exception) {
                logger.warning("Failed to clear SQL collection claims: ${e.message}")
            }
            return
        }

        synchronized(fileClaimsLock) {
            fileClaims.clear()
            saveClaimsToFile()
        }
    }

    fun beginCollectionTx(grave: Grave, collectorId: UUID, ttlMillis: Long): CollectionTx? {
        val now = System.currentTimeMillis()
        val tx = CollectionTx(
            txId = UUID.randomUUID(),
            graveId = grave.graveId,
            collectorId = collectorId,
            state = CollectionState.CLAIMED,
            version = 1,
            updatedAt = now,
            expiresAt = now + ttlMillis
        )
        return if (storageBackend == StorageBackend.SQL) {
            beginCollectionTxSql(tx)
        } else {
            beginCollectionTxFile(tx)
        }
    }

    fun transitionCollectionTx(graveId: UUID, txId: UUID, from: CollectionState, to: CollectionState, error: String? = null): Boolean {
        return if (storageBackend == StorageBackend.SQL) {
            transitionCollectionTxSql(graveId, txId, from, to, error)
        } else {
            transitionCollectionTxFile(graveId, txId, from, to, error)
        }
    }

    fun markCollectedTx(graveId: UUID, txId: UUID): Boolean {
        return transitionCollectionTx(graveId, txId, CollectionState.COLLECTING, CollectionState.COLLECTED)
    }

    fun listStuckCollectionTx(stuckThresholdMs: Long): List<StuckCollectionTx> {
        val now = System.currentTimeMillis()
        val threshold = now - stuckThresholdMs.coerceAtLeast(1000L)
        if (storageBackend == StorageBackend.SQL) {
            val manager = dbManager ?: return emptyList()
            if (!ensureSqlReady(logFailure = false)) return emptyList()
            return runCatching {
                manager.query(
                    """
                        SELECT txId, graveId, collectorId, state, updatedAt, expiresAt
                        FROM grave_collection_tx
                        WHERE state IN (?, ?, ?) AND updatedAt < ?
                        ORDER BY updatedAt ASC
                    """.trimIndent(),
                    CollectionState.CLAIMED.name,
                    CollectionState.COLLECTING.name,
                    CollectionState.FAILED.name,
                    threshold
                ) { rs ->
                    StuckCollectionTx(
                        txId = UUID.fromString(rs.getString("txId")),
                        graveId = UUID.fromString(rs.getString("graveId")),
                        collectorId = UUID.fromString(rs.getString("collectorId")),
                        state = CollectionState.valueOf(rs.getString("state")),
                        updatedAt = rs.getLong("updatedAt"),
                        expiresAt = rs.getLong("expiresAt")
                    )
                }
            }.getOrElse {
                logger.warning("Failed to list stuck SQL collection tx: ${it.message}")
                emptyList()
            }
        }

        synchronized(fileTxLock) {
            return fileTxMachine().all()
                .filter { tx ->
                    (tx.state == CollectionState.CLAIMED || tx.state == CollectionState.COLLECTING || tx.state == CollectionState.FAILED) &&
                        tx.updatedAt < threshold
                }
                .sortedBy { it.updatedAt }
                .map { tx ->
                    StuckCollectionTx(
                        txId = tx.txId,
                        graveId = tx.graveId,
                        collectorId = tx.collectorId,
                        state = tx.state,
                        updatedAt = tx.updatedAt,
                        expiresAt = tx.expiresAt
                    )
                }
        }
    }

    fun unlockCollectionTx(identifier: String, actor: String, reason: String): Int {
        val normalized = identifier.trim()
        val now = System.currentTimeMillis()
        val result = if (storageBackend == StorageBackend.SQL) {
            unlockCollectionTxSql(normalized, now)
        } else {
            unlockCollectionTxFile(normalized, now)
        }
        result.graveIds.forEach { releaseCollectionClaimByGraveId(it) }
        if (result.changed > 0) {
            appendTxAudit(
                actor = actor,
                action = "UNLOCK",
                target = normalized,
                reason = reason,
                changed = result.changed
            )
        }
        return result.changed
    }

    fun recoverExpiredCollectionTx(): Int {
        val now = System.currentTimeMillis()
        val retryThreshold = now - configInt("graves.collection.claim-ttl-ms").toLong().coerceAtLeast(3000L)
        val result = if (storageBackend == StorageBackend.SQL) {
            recoverExpiredCollectionTxSql(now, retryThreshold)
        } else {
            recoverExpiredCollectionTxFile(now, retryThreshold)
        }
        result.graveIds.forEach { releaseCollectionClaimByGraveId(it) }
        if (result.changed > 0) {
            appendTxAudit(
                actor = "system:recovery-job",
                action = "RECOVER_EXPIRED",
                target = "expired-claimed-collecting",
                reason = "TTL expiration moved tx to FAILED_RECOVERABLE",
                changed = result.changed
            )
        }
        return result.changed
    }

    fun countStuckCollectionTx(stuckThresholdMs: Long): Int {
        val now = System.currentTimeMillis()
        val threshold = now - stuckThresholdMs.coerceAtLeast(1000L)
        if (storageBackend == StorageBackend.SQL) {
            val manager = dbManager ?: return 0
            if (!ensureSqlReady(logFailure = false)) return 0
            return runCatching {
                manager.query(
                    """
                        SELECT txId FROM grave_collection_tx
                        WHERE state IN (?, ?) AND updatedAt < ?
                    """.trimIndent(),
                    CollectionState.CLAIMED.name,
                    CollectionState.COLLECTING.name,
                    threshold
                ) { rs -> rs.getString("txId") }.size
            }.getOrElse {
                logger.warning("Failed to count stuck SQL collection tx: ${it.message}")
                0
            }
        }

        synchronized(fileTxLock) {
            val txValues = fileTxMachine().all()
            return txValues.count { tx ->
                (tx.state == CollectionState.CLAIMED || tx.state == CollectionState.COLLECTING) &&
                    tx.updatedAt < threshold
            }
        }
    }

    fun clearCollectionState(grave: Grave) {
        if (storageBackend == StorageBackend.SQL) {
            val manager = dbManager ?: return
            if (!ensureSqlReady(logFailure = false)) return
            runCatching {
                manager.execute("DELETE FROM grave_collection_tx WHERE graveId = ?", grave.graveId.toString())
            }.onFailure {
                logger.warning("Failed to clear collection tx for grave ${grave.graveId}: ${it.message}")
            }
            return
        }

        synchronized(fileTxLock) {
            fileTxMachine().clearGrave(grave.graveId)
        }
    }

    private fun ensureSqlReady(logFailure: Boolean = true): Boolean {
        if (storageBackend != StorageBackend.SQL) {
            return false
        }

        val manager = dbManager ?: return false
        if (sqlOperational.get()) return true
        return try {
            manager.connect()
            sqlOperational.set(true)
            true
        } catch (e: Exception) {
            if (logFailure) {
                logger.debug("Database connection attempt failed: ${e.message}")
            }
            false
        }
    }

    private fun resolveBackend(type: String): StorageBackend = when (type.lowercase(Locale.ROOT)) {
        "mysql", "mariadb", "postgresql", "postgres", "sqlite", "h2" -> StorageBackend.SQL
        else -> StorageBackend.FILE
    }

    private fun resolveDatabaseName(configuredName: String?): String =
        configuredName?.takeUnless { it.isBlank() } ?: pluginName

    private fun resolveDatabaseFilePath(type: DatabaseType, baseName: String): String? = when (type) {
        DatabaseType.SQLITE -> resolveSqliteDatabasePath(baseName)
        DatabaseType.H2 -> resolveH2DatabasePath(baseName)
        else -> null
    }

    private fun resolveH2DatabasePath(name: String): String {
        val trimmed = name.trim()
        val normalized = trimmed.replace("\\", "/")
        val specialPrefixes = listOf("mem:", "file:", "zip:", "ssl:", "tcp:")
        if (specialPrefixes.any { normalized.startsWith(it, ignoreCase = true) }) {
            return trimmed
        }

        if (normalized.startsWith("~/") || normalized.startsWith("./") || normalized.startsWith("/")) {
            return trimmed
        }

        val path = try {
            Path.of(trimmed)
        } catch (_: Exception) {
            null
        }
        if (path?.isAbsolute == true) {
            return path.toString()
        }

        val dataFolderPath = dataFolder.toPath()
        createDirectoryIfMissing(dataFolderPath)
        val databaseRoot = dataFolderPath.resolve("database")
        createDirectoryIfMissing(databaseRoot)

        val resolvedPath = databaseRoot.resolve(trimmed)
        resolvedPath.parent?.let { createDirectoryIfMissing(it) }
        return resolvedPath.toAbsolutePath().toString()
    }

    private fun resolveSqliteDatabasePath(name: String): String {
        val trimmed = name.trim().removeSuffix(".db")
        val dataFolderPath = dataFolder.toPath()
        createDirectoryIfMissing(dataFolderPath)
        val databaseRoot = dataFolderPath.resolve("database")
        createDirectoryIfMissing(databaseRoot)

        val resolvedPath = databaseRoot.resolve("$trimmed.db")
        resolvedPath.parent?.let { createDirectoryIfMissing(it) }
        return resolvedPath.toAbsolutePath().toString()
    }

    private fun loadAllGravesFromSql(
        manager: DatabaseManager,
        attemptRecovery: Boolean = true
    ): List<Grave>? {
        return try {
            val records = manager.query("SELECT * FROM graves ORDER BY createdAt ASC") { rs ->
                GraveRecord(
                    id = rs.getInt("id"),
                    graveKey = rs.getString("graveKey"),
                    ownerId = rs.getString("ownerId"),
                    ownerName = rs.getString("ownerName"),
                    world = rs.getString("world"),
                    x = rs.getDouble("x"),
                    y = rs.getDouble("y"),
                    z = rs.getDouble("z"),
                    yaw = rs.getFloat("yaw"),
                    pitch = rs.getFloat("pitch"),
                    createdAt = rs.getLong("createdAt"),
                    storedXp = rs.getInt("storedXp"),
                    payload = rs.getString("payload")
                )
            }
            records.mapNotNull { GraveSerializer.decodeGraveFromString(it.payload) }
        } catch (e: Exception) {
            if (attemptRecovery && isMissingTable(e)) {
                logger.warning("Graves table missing, attempting to create schema before retrying load.")
                ensureSchema()
                return if (sqlOperational.get()) {
                    loadAllGravesFromSql(manager, attemptRecovery = false)
                } else {
                    null
                }
            }

            sqlOperational.set(false)
            logger.err("Failed to load graves from database, switching to file storage. ${e.message}")
            null
        }
    }

    private fun isMissingTable(exception: Exception): Boolean {
        if (exception !is SQLException) {
            return false
        }

        val errorCode = exception.errorCode
        val sqlState = exception.sqlState?.uppercase(Locale.ROOT)
        if (errorCode == 42104 || sqlState == "42S02") {
            return true
        }

        val message = exception.message?.lowercase(Locale.ROOT) ?: return false
        return "table \"graves\" not found" in message || "table 'graves' not found" in message
    }

    private fun createDirectoryIfMissing(path: Path) {
        try {
            Files.createDirectories(path)
        } catch (ignored: IOException) {
            onStorageIoError.invoke()
            logger.warning("Failed to create directory ${path.toAbsolutePath()}: ${ignored.message}")
        }
    }

    private fun idDefinition(): String = when (dbType) {
        DatabaseType.SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT"
        DatabaseType.POSTGRESQL -> "SERIAL PRIMARY KEY"
        DatabaseType.MSSQL -> "INT IDENTITY(1,1) PRIMARY KEY"
        DatabaseType.H2 -> "INT AUTO_INCREMENT PRIMARY KEY"
        else -> "INT AUTO_INCREMENT PRIMARY KEY"
    }

    private fun payloadColumnType(): String = when (dbType) {
        DatabaseType.MYSQL, DatabaseType.MARIADB -> "LONGTEXT"
        DatabaseType.MSSQL -> "NVARCHAR(MAX)"
        else -> "TEXT"
    }

    private fun parseDatabaseType(type: String): DatabaseType? = when (type.lowercase(Locale.ROOT)) {
        "mysql" -> DatabaseType.MYSQL
        "mariadb" -> DatabaseType.MARIADB
        "postgresql", "postgres" -> DatabaseType.POSTGRESQL
        "sqlite" -> DatabaseType.SQLITE
        "h2" -> DatabaseType.H2
        else -> null
    }

    private fun defaultPort(type: DatabaseType): Int = when (type) {
        DatabaseType.POSTGRESQL -> 5432
        DatabaseType.MYSQL, DatabaseType.MARIADB -> 3306
        DatabaseType.MSSQL -> 1433
        else -> 0
    }

    private fun resolvePort(type: DatabaseType, configuredPort: Int): Int {
        val port = configuredPort.takeIf { it > 0 }
        if (port != null) return port
        return defaultPort(type)
    }

    private fun locationKey(location: Location): String {
        return GraveIdentity.locationKey(location)
    }

    private fun collectionClaimKey(grave: Grave): String =
        GraveIdentity.collectionClaimKey(grave)

    private fun tryAcquireCollectionClaimSql(claimKey: String, graveKey: String): Boolean {
        val manager = dbManager ?: return false
        if (!ensureSqlReady()) return false
        return try {
            manager.execute(
                "INSERT INTO grave_collection_claims (claimKey, graveKey, claimedAt) VALUES (?, ?, ?)",
                claimKey,
                graveKey,
                System.currentTimeMillis()
            )
            true
        } catch (e: Exception) {
            val message = e.message?.lowercase(Locale.ROOT).orEmpty()
            if ("unique" in message || "duplicate" in message || "constraint" in message) {
                return false
            }
            logger.warning("Failed to acquire SQL collection claim for $claimKey: ${e.message}")
            false
        }
    }

    private fun releaseCollectionClaimSql(claimKey: String) {
        val manager = dbManager ?: return
        if (!ensureSqlReady(logFailure = false)) return
        try {
            manager.execute("DELETE FROM grave_collection_claims WHERE claimKey = ?", claimKey)
        } catch (e: Exception) {
            logger.warning("Failed to release SQL collection claim for $claimKey: ${e.message}")
        }
    }

    private fun tryAcquireCollectionClaimFile(claimKey: String): Boolean {
        synchronized(fileClaimsLock) {
            loadClaimsFromFileIfNeeded()
            if (!fileClaims.add(claimKey)) {
                return false
            }
            if (!saveClaimsToFile()) {
                fileClaims.remove(claimKey)
                return false
            }
            return true
        }
    }

    private fun releaseCollectionClaimFile(claimKey: String) {
        synchronized(fileClaimsLock) {
            loadClaimsFromFileIfNeeded()
            if (fileClaims.remove(claimKey)) {
                saveClaimsToFile()
            }
        }
    }

    private fun loadClaimsFromFileIfNeeded() {
        if (fileClaims.isNotEmpty() || !Files.exists(claimsFilePath)) return
        runCatching {
            val content = Files.readString(claimsFilePath)
            if (content.isBlank()) return@runCatching
            val type = object : TypeToken<List<String>>() {}.type
            val values: List<String> = GraveSerializer.gson.fromJson(JsonParser.parseString(content), type)
            fileClaims.addAll(values)
        }.onFailure {
            onStorageIoError.invoke()
            logger.warning("Failed to load collection claims from ${claimsFilePath.toAbsolutePath()}: ${it.message}")
        }
    }

    private fun saveClaimsToFile(): Boolean {
        return try {
            createDirectoryIfMissing(claimsFilePath.parent)
            val tmpPath = claimsFilePath.resolveSibling(claimsFilePath.fileName.toString() + ".tmp")
            Files.writeString(tmpPath, GraveSerializer.gson.toJson(fileClaims.toList()))
            try {
                Files.move(
                    tmpPath,
                    claimsFilePath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(tmpPath, claimsFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: Exception) {
            onStorageIoError.invoke()
            logger.warning("Failed to save collection claims to ${claimsFilePath.toAbsolutePath()}: ${e.message}")
            false
        }
    }

    private fun beginCollectionTxSql(tx: CollectionTx): CollectionTx? {
        val manager = dbManager ?: return null
        if (!ensureSqlReady()) return null
        val now = System.currentTimeMillis()
        return try {
            manager.execute("DELETE FROM grave_collection_tx WHERE graveId = ? AND state != ? AND expiresAt < ?",
                tx.graveId.toString(),
                CollectionState.COLLECTED.name,
                now
            )
            val existing = manager.query(
                "SELECT txId FROM grave_collection_tx WHERE graveId = ? AND state IN (?, ?, ?)",
                tx.graveId.toString(),
                CollectionState.CLAIMED.name,
                CollectionState.COLLECTING.name,
                CollectionState.COLLECTED.name
            ) { rs -> rs.getString("txId") }
            if (existing.isNotEmpty()) {
                return null
            }
            manager.execute(
                """
                    INSERT INTO grave_collection_tx
                    (txId, graveId, collectorId, state, version, updatedAt, expiresAt, lastError)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                tx.txId.toString(),
                tx.graveId.toString(),
                tx.collectorId.toString(),
                tx.state.name,
                tx.version,
                tx.updatedAt,
                tx.expiresAt,
                tx.lastError ?: ""
            )
            tx
        } catch (e: Exception) {
            logger.warning("Failed to begin SQL collection tx for grave ${tx.graveId}: ${e.message}")
            null
        }
    }

    private fun transitionCollectionTxSql(graveId: UUID, txId: UUID, from: CollectionState, to: CollectionState, error: String?): Boolean {
        val manager = dbManager ?: return false
        if (!ensureSqlReady()) return false
        return try {
            val current = manager.query(
                "SELECT state FROM grave_collection_tx WHERE graveId = ? AND txId = ?",
                graveId.toString(),
                txId.toString()
            ) { rs -> rs.getString("state") }.firstOrNull() ?: return false
            if (current != from.name) return false
            manager.execute(
                """
                    UPDATE grave_collection_tx
                    SET state = ?, version = version + 1, updatedAt = ?, lastError = ?
                    WHERE graveId = ? AND txId = ? AND state = ?
                """.trimIndent(),
                to.name,
                System.currentTimeMillis(),
                error ?: "",
                graveId.toString(),
                txId.toString(),
                from.name
            )
            true
        } catch (e: Exception) {
            logger.warning("Failed to transition SQL collection tx ${txId}: ${e.message}")
            false
        }
    }

    private fun beginCollectionTxFile(tx: CollectionTx): CollectionTx? {
        synchronized(fileTxLock) {
            val machine = fileTxMachine()
            val ttl = (tx.expiresAt - tx.updatedAt).coerceAtLeast(1L)
            return machine.begin(
                graveId = tx.graveId,
                collectorId = tx.collectorId,
                ttlMillis = ttl,
                nowMillis = tx.updatedAt,
                txId = tx.txId
            )
        }
    }

    private fun transitionCollectionTxFile(graveId: UUID, txId: UUID, from: CollectionState, to: CollectionState, error: String?): Boolean {
        synchronized(fileTxLock) {
            return fileTxMachine().transition(
                graveId = graveId,
                txId = txId,
                from = from,
                to = to,
                nowMillis = System.currentTimeMillis(),
                error = error
            )
        }
    }

    private fun unlockCollectionTxSql(identifier: String, now: Long): TxUpdateResult {
        val manager = dbManager ?: return TxUpdateResult(0, emptySet())
        if (!ensureSqlReady(logFailure = false)) return TxUpdateResult(0, emptySet())
        return runCatching {
            val uuid = UUID.fromString(identifier).toString()
            val targets = manager.query(
                """
                    SELECT txId, graveId, state FROM grave_collection_tx
                    WHERE (graveId = ? OR txId = ?) AND state IN (?, ?, ?)
                """.trimIndent(),
                uuid,
                uuid,
                CollectionState.CLAIMED.name,
                CollectionState.COLLECTING.name,
                CollectionState.FAILED.name
            ) { rs ->
                Triple(rs.getString("txId"), rs.getString("graveId"), rs.getString("state"))
            }
            targets.forEach { (txId, graveId, _) ->
                manager.execute(
                    """
                    UPDATE grave_collection_tx
                    SET state = ?, version = version + 1, updatedAt = ?, expiresAt = ?, lastError = ?
                    WHERE txId = ? AND graveId = ?
                    """.trimIndent(),
                    CollectionState.FAILED_RECOVERABLE.name,
                    now,
                    now,
                    "manually unlocked",
                    txId,
                    graveId
                )
            }
            TxUpdateResult(
                changed = targets.size,
                graveIds = targets.map { UUID.fromString(it.second) }.toSet()
            )
        }.getOrElse {
            logger.warning("Failed to unlock SQL collection tx $identifier: ${it.message}")
            TxUpdateResult(0, emptySet())
        }
    }

    private fun unlockCollectionTxFile(identifier: String, now: Long): TxUpdateResult {
        val uuid = runCatching { UUID.fromString(identifier) }.getOrNull() ?: return TxUpdateResult(0, emptySet())
        synchronized(fileTxLock) {
            val machine = fileTxMachine()
            val tx = machine.get(uuid)
            if (tx != null) {
                if (tx.state !in setOf(CollectionState.CLAIMED, CollectionState.COLLECTING, CollectionState.FAILED)) return TxUpdateResult(0, emptySet())
                if (!machine.transition(tx.graveId, tx.txId, tx.state, CollectionState.FAILED_RECOVERABLE, now, "manually unlocked")) return TxUpdateResult(0, emptySet())
                return TxUpdateResult(1, setOf(tx.graveId))
            }
            val targets = machine.all().filter { it.graveId == uuid && it.state in setOf(CollectionState.CLAIMED, CollectionState.COLLECTING, CollectionState.FAILED) }
            var changed = 0
            val graveIds = linkedSetOf<UUID>()
            targets.forEach { candidate ->
                if (machine.transition(candidate.graveId, candidate.txId, candidate.state, CollectionState.FAILED_RECOVERABLE, now, "manually unlocked")) {
                    changed++
                    graveIds.add(candidate.graveId)
                }
            }
            return TxUpdateResult(changed, graveIds)
        }
    }

    private fun recoverExpiredCollectionTxSql(now: Long, retryThreshold: Long): TxUpdateResult {
        val manager = dbManager ?: return TxUpdateResult(0, emptySet())
        if (!ensureSqlReady(logFailure = false)) return TxUpdateResult(0, emptySet())
        return runCatching {
            val expired = manager.query(
                """
                    SELECT txId, graveId, state FROM grave_collection_tx
                    WHERE (state IN (?, ?) AND expiresAt < ?) OR (state = ? AND updatedAt < ?)
                """.trimIndent(),
                CollectionState.CLAIMED.name,
                CollectionState.COLLECTING.name,
                now,
                CollectionState.FAILED.name,
                retryThreshold
            ) { rs -> Triple(rs.getString("txId"), rs.getString("graveId"), rs.getString("state")) }
            expired.forEach { (txId, graveId, state) ->
                manager.execute(
                    """
                    UPDATE grave_collection_tx
                    SET state = ?, version = version + 1, updatedAt = ?, lastError = ?
                    WHERE txId = ? AND graveId = ? AND state = ?
                    """.trimIndent(),
                    CollectionState.FAILED_RECOVERABLE.name,
                    now,
                    "auto-recovered after TTL in $state",
                    txId,
                    graveId,
                    state
                )
            }
            TxUpdateResult(
                changed = expired.size,
                graveIds = expired.map { UUID.fromString(it.second) }.toSet()
            )
        }.getOrElse {
            logger.warning("Failed to auto-recover expired SQL collection tx: ${it.message}")
            TxUpdateResult(0, emptySet())
        }
    }

    private fun recoverExpiredCollectionTxFile(now: Long, retryThreshold: Long): TxUpdateResult {
        synchronized(fileTxLock) {
            val machine = fileTxMachine()
            val expired = machine.all().filter {
                ((it.state == CollectionState.CLAIMED || it.state == CollectionState.COLLECTING) && it.expiresAt < now) ||
                    (it.state == CollectionState.FAILED && it.updatedAt < retryThreshold)
            }
            var changed = 0
            val graveIds = linkedSetOf<UUID>()
            expired.forEach { tx ->
                if (machine.transition(tx.graveId, tx.txId, tx.state, CollectionState.FAILED_RECOVERABLE, now, "auto-recovered after TTL")) {
                    changed++
                    graveIds.add(tx.graveId)
                }
            }
            return TxUpdateResult(changed, graveIds)
        }
    }

    private fun appendTxAudit(actor: String, action: String, target: String, reason: String, changed: Int) {
        val line = "${Instant.now()} actor=$actor action=$action target=$target changed=$changed reason=${reason.replace("\n", " ")}"
        runCatching {
            createDirectoryIfMissing(txAuditLogPath.parent)
            Files.writeString(
                txAuditLogPath,
                "$line\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            )
            logger.debug("tx-audit event: $line")
        }.onFailure {
            onStorageIoError.invoke()
            logger.warning("Failed to append tx audit log: ${it.message}")
        }
    }

    private fun loadTxFromFileIfNeeded() {
        if (fileCollectionTx.isNotEmpty() || !Files.exists(collectionTxFilePath)) return
        runCatching {
            val content = Files.readString(collectionTxFilePath)
            if (content.isBlank()) return@runCatching
            val values = JsonParser.parseString(content).asJsonArray
            values.forEach { element ->
                val obj = element.asJsonObject
                val tx = CollectionTx(
                    txId = UUID.fromString(obj.get("txId").asString),
                    graveId = UUID.fromString(obj.get("graveId").asString),
                    collectorId = UUID.fromString(obj.get("collectorId").asString),
                    state = CollectionState.valueOf(obj.get("state").asString),
                    version = obj.get("version").asInt,
                    updatedAt = obj.get("updatedAt").asLong,
                    expiresAt = obj.get("expiresAt").asLong,
                    lastError = obj.get("lastError")?.takeIf { !it.isJsonNull }?.asString
                )
                fileCollectionTx[tx.txId] = tx
            }
        }.onFailure {
            onStorageIoError.invoke()
            logger.warning("Failed to load collection tx from ${collectionTxFilePath.toAbsolutePath()}: ${it.message}")
        }
    }

    private fun fileTxMachine(): CollectionTxStateMachine {
        val existing = fileTxStateMachine
        if (existing != null) return existing
        loadTxFromFileIfNeeded()
        val created = CollectionTxStateMachine(
            persistence = CollectionTxStateMachine.Persistence { snapshot ->
                fileCollectionTx.clear()
                snapshot.forEach { fileCollectionTx[it.txId] = it }
                saveTxToFile()
            },
            initialState = fileCollectionTx.values.toList()
        )
        fileTxStateMachine = created
        return created
    }

    private fun saveTxToFile(): Boolean {
        return try {
            createDirectoryIfMissing(collectionTxFilePath.parent)
            val tmpPath = collectionTxFilePath.resolveSibling(collectionTxFilePath.fileName.toString() + ".tmp")
            val array = fileCollectionTx.values.map { tx ->
                JsonObject().apply {
                    addProperty("txId", tx.txId.toString())
                    addProperty("graveId", tx.graveId.toString())
                    addProperty("collectorId", tx.collectorId.toString())
                    addProperty("state", tx.state.name)
                    addProperty("version", tx.version)
                    addProperty("updatedAt", tx.updatedAt)
                    addProperty("expiresAt", tx.expiresAt)
                    addProperty("lastError", tx.lastError)
                }
            }
            Files.writeString(tmpPath, GraveSerializer.gson.toJson(array))
            try {
                Files.move(
                    tmpPath,
                    collectionTxFilePath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(tmpPath, collectionTxFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: Exception) {
            onStorageIoError.invoke()
            logger.warning("Failed to save collection tx to ${collectionTxFilePath.toAbsolutePath()}: ${e.message}")
            false
        }
    }

    private fun Grave.toRecord(): GraveRecord {
        val location = this.location
        return GraveRecord(
            graveKey = locationKey(location),
            ownerId = ownerId.toString(),
            ownerName = ownerName,
            world = location.world?.name ?: "world",
            x = location.x,
            y = location.y,
            z = location.z,
            yaw = location.yaw,
            pitch = location.pitch,
            createdAt = createdAt,
            storedXp = storedXp,
            payload = GraveSerializer.encodeGraveToString(this)
        )
    }

    private interface GraveStore {
        fun saveAllGraves(graves: Collection<Grave>)
        fun loadAllGraves(): List<Grave>
    }

    private class GraveStoreAdapter(
        private val delegate: GraveStore
    ) : GraveStore {
        constructor(delegate: GraveDataStore) : this(object : GraveStore {
            override fun saveAllGraves(graves: Collection<Grave>) = delegate.saveAllGraves(graves)
            override fun loadAllGraves(): List<Grave> = delegate.loadAllGraves()
        })

        override fun saveAllGraves(graves: Collection<Grave>) = delegate.saveAllGraves(graves)
        override fun loadAllGraves(): List<Grave> = delegate.loadAllGraves()
    }

    private class GraveDataStoreShim(
        private val dataFolder: java.io.File,
        private val logger: LogSink,
        private val onStorageIoError: () -> Unit
    ) : GraveStore {
        private val dataFile = java.io.File(dataFolder, "data.json")

        init {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs()
            }
        }

        override fun saveAllGraves(graves: Collection<Grave>) {
            try {
                val jsonArray = GraveSerializer.encodeGraves(graves)
                val jsonText = GraveSerializer.gson.toJson(jsonArray)
                val tmpFile = java.io.File(dataFile.parentFile, dataFile.name + ".tmp")
                tmpFile.bufferedWriter().use { writer ->
                    writer.write(jsonText)
                    writer.flush()
                }
                try {
                    Files.move(
                        tmpFile.toPath(),
                        dataFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: Exception) {
                    onStorageIoError.invoke()
                    Files.move(tmpFile.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                onStorageIoError.invoke()
                logger.err("Failed to save graves to ${dataFile.absolutePath}: ${e.message}")
            }
        }

        override fun loadAllGraves(): List<Grave> {
            return try {
                if (!dataFile.exists()) return emptyList()
                val content = dataFile.readText()
            if (content.isBlank()) return emptyList()
            GraveSerializer.decodeGravesFromString(content)
        } catch (e: Exception) {
            onStorageIoError.invoke()
            logger.err("Failed to load graves from ${dataFile.absolutePath}: ${e.message}")
            emptyList()
        }
        }
    }

    private data class LogSink(
        val debug: (String) -> Unit,
        val warning: (String) -> Unit,
        val error: (String) -> Unit
    ) {
        fun debug(message: String) = debug.invoke(message)
        fun warning(message: String) = warning.invoke(message)
        fun err(message: String) = error.invoke(message)
    }
}
