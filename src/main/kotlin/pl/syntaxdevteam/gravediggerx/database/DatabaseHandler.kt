package pl.syntaxdevteam.gravediggerx.database

import org.bukkit.Location
import pl.syntaxdevteam.core.database.Column
import pl.syntaxdevteam.core.database.DatabaseConfig
import pl.syntaxdevteam.core.database.DatabaseManager
import pl.syntaxdevteam.core.database.DatabaseType
import pl.syntaxdevteam.core.database.TableSchema
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.graves.GraveDataStore
import pl.syntaxdevteam.gravediggerx.graves.GraveSerializer
import java.util.Locale
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
            DatabaseConfig(
                type = it,
                host = plugin.config.getString("database.sql.host") ?: "localhost",
                port = plugin.config.getInt("database.sql.port").takeIf { port -> port != 0 }
                    ?: defaultPort(it),
                database = plugin.config.getString("database.sql.dbname") ?: plugin.name,
                username = plugin.config.getString("database.sql.username") ?: "root",
                password = plugin.config.getString("database.sql.password") ?: "",
            )
        }
    } else {
        null
    }
    private val dbManager = dbConfig?.let { DatabaseManager(it, logger) }
    private val sqlOperational = AtomicBoolean(false)

    fun connect() {
        if (storageBackend != StorageBackend.SQL) {
            logger.debug("Using file-based storage backend ($configuredType), SQL connection skipped.")
            return
        }

        if (ensureSqlReady()) {
            logger.debug("Connected to ${dbType?.name?.lowercase(Locale.ROOT)} database.")
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
            sqlOperational.set(false)
            logger.err("Failed to load graves from database, switching to file storage. ${e.message}")
            fileStore.loadAllGraves()
        }
    }

    fun writeGravesToJsonIfConfigured(graves: Collection<Grave>) {
        if (isJsonBackend()) {
            fileStore.saveAllGraves(graves)
        } else {
            replaceAllGraves(graves)
        }
    }

    fun isJsonBackend(): Boolean = configuredType == "json"

    private fun ensureSqlReady(): Boolean {
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
            logger.err("Failed to (re)connect to database: ${e.message}")
            false
        }
    }

    private fun resolveBackend(type: String): StorageBackend = when (type.lowercase(Locale.ROOT)) {
        "mysql", "mariadb", "postgresql", "postgres", "sqlite", "h2" -> StorageBackend.SQL
        else -> StorageBackend.FILE
    }

    private fun idDefinition(): String = when (dbType) {
        DatabaseType.SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT"
        DatabaseType.POSTGRESQL -> "SERIAL PRIMARY KEY"
        else -> "INT AUTO_INCREMENT PRIMARY KEY"
    }

    private fun payloadColumnType(): String = when (dbType) {
        DatabaseType.POSTGRESQL -> "TEXT"
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
        else -> 0
    }

    private fun locationKey(location: Location): String {
        val worldName = location.world?.name ?: "unknown"
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        return "$worldName:$x:$y:$z"
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
