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
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.graves.GraveDataStore
import pl.syntaxdevteam.gravediggerx.graves.GraveIdentity
import pl.syntaxdevteam.gravediggerx.graves.GraveSerializer
import pl.syntaxdevteam.gravediggerx.graves.CollectionState
import pl.syntaxdevteam.gravediggerx.graves.CollectionTx
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class DatabaseHandler(private val plugin: GraveDiggerX) {
    private enum class StorageBackend { FILE, SQL }

    private val logger = plugin.logger

    private val fileStore = GraveDataStore(plugin)
    private val configuredType =
        plugin.config.getString("database.type")?.trim()?.lowercase(Locale.ROOT) ?: "yaml"

    private val storageBackend = resolveBackend(configuredType)
    private val dbType: DatabaseType? = parseDatabaseType(configuredType)
    private val dbConfig = if (storageBackend == StorageBackend.SQL) {
        dbType?.let {
            val databaseName = resolveDatabaseName(
                plugin.config.getString("database.sql.dbname")
            )
            DatabaseConfig(
                type = it,
                host = plugin.config.getString("database.sql.host") ?: "localhost",
                port = resolvePort(it, plugin.config.getInt("database.sql.port")),
                database = databaseName,
                username = plugin.config.getString("database.sql.username") ?: "root",
                password = plugin.config.getString("database.sql.password") ?: "",
                filePath = resolveDatabaseFilePath(it, databaseName)
            )
        }
    } else {
        null
    }
    private val dbManager = dbConfig?.let { DatabaseManager(it, logger) }
    private val sqlOperational = AtomicBoolean(false)
    private val claimsFilePath = plugin.dataFolder.toPath().resolve("collection_claims.json")
    private val collectionTxFilePath = plugin.dataFolder.toPath().resolve("collection_tx.json")
    private val fileClaimsLock = Any()
    private val fileClaims = ConcurrentHashMap.newKeySet<String>()
    private val fileTxLock = Any()
    private val fileCollectionTx = ConcurrentHashMap<UUID, CollectionTx>()

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
        val claimKey = collectionClaimKey(grave)
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
            loadTxFromFileIfNeeded()
            fileCollectionTx.entries.removeIf { it.value.graveId == grave.graveId }
            saveTxToFile()
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
        configuredName?.takeUnless { it.isBlank() } ?: plugin.name

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

        val dataFolderPath = plugin.dataFolder.toPath()
        createDirectoryIfMissing(dataFolderPath)
        val databaseRoot = dataFolderPath.resolve("database")
        createDirectoryIfMissing(databaseRoot)

        val resolvedPath = databaseRoot.resolve(trimmed)
        resolvedPath.parent?.let { createDirectoryIfMissing(it) }
        return resolvedPath.toAbsolutePath().toString()
    }

    private fun resolveSqliteDatabasePath(name: String): String {
        val trimmed = name.trim().removeSuffix(".db")
        val dataFolderPath = plugin.dataFolder.toPath()
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
            loadTxFromFileIfNeeded()
            val now = System.currentTimeMillis()
            fileCollectionTx.entries.removeIf {
                it.value.graveId == tx.graveId && it.value.state != CollectionState.COLLECTED && it.value.expiresAt < now
            }
            val locked = fileCollectionTx.values.any {
                it.graveId == tx.graveId && (it.state == CollectionState.CLAIMED || it.state == CollectionState.COLLECTING || it.state == CollectionState.COLLECTED)
            }
            if (locked) return null
            fileCollectionTx[tx.txId] = tx
            if (!saveTxToFile()) {
                fileCollectionTx.remove(tx.txId)
                return null
            }
            return tx
        }
    }

    private fun transitionCollectionTxFile(graveId: UUID, txId: UUID, from: CollectionState, to: CollectionState, error: String?): Boolean {
        synchronized(fileTxLock) {
            loadTxFromFileIfNeeded()
            val current = fileCollectionTx[txId] ?: return false
            if (current.graveId != graveId || current.state != from) return false
            val updated = current.copy(
                state = to,
                version = current.version + 1,
                updatedAt = System.currentTimeMillis(),
                lastError = error
            )
            fileCollectionTx[txId] = updated
            if (!saveTxToFile()) {
                fileCollectionTx[txId] = current
                return false
            }
            return true
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
            logger.warning("Failed to load collection tx from ${collectionTxFilePath.toAbsolutePath()}: ${it.message}")
        }
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
}
