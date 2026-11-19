package pl.syntaxdevteam.gravediggerx.database

// Local compatibility layer adapted from SyntaxCore's former JDBC API (MIT).
// Keep existing SQL schemas and supported engines; no dependence on removed core.database classes.

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import pl.syntaxdevteam.core.logging.Logger
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Configuration details required to establish a database connection.
 *
 * @property type target [DatabaseType] to connect to.
 * @property host hostname or IP address of the database server. For file-based
 * databases (SQLite/H2) this value is ignored.
 * @property port network port on which the server listens. Defaults to the
 * standard port for the selected [type].
 * @property database database or schema name. For SQLite/H2 this represents the
 * file name when [filePath] is not provided.
 * @property username username used to authenticate with the database server.
 * @property password password used during authentication.
 * @property filePath optional absolute or relative path for file-based engines
 * (SQLite/H2). When set, [database] is treated as a logical name only.
 */
internal data class DatabaseConfig(
    val type: DatabaseType,
    val host: String = "localhost",
    val port: Int = when (type) {
        DatabaseType.MYSQL, DatabaseType.MARIADB -> 3306
        DatabaseType.POSTGRESQL -> 5432
        DatabaseType.MSSQL -> 1433
        else -> 0
    },
    val database: String,
    val username: String = "",
    val password: String = "",
    val filePath: String? = null
)


/**
 * Definition of a single table column.
 *
 * @property name name of the column.
 * @property definition full SQL definition of the column, e.g. `"INT PRIMARY KEY"`.
 */
internal data class Column(val name: String, val definition: String)

/**
 * Represents a table schema consisting of multiple [columns].
 *
 * @property name name of the table.
 * @property columns ordered list of column definitions that make up the table.
 */
internal data class TableSchema(val name: String, val columns: List<Column>)

/**
 * Simple and flexible database manager using HikariCP.
 *
 * The manager lazily creates a connection pool on [connect] and can be reused across
 * multiple plugins. Connections are retrieved on demand via [getConnection] and the
 * entire pool can be shut down using [close].
 *
 * Example usage:
 * ```
 * val manager = DatabaseManager(config, logger)
 * manager.connect()
 * val rows = manager.query("SELECT * FROM users") { it.getString("name") }
 * manager.close()
 * ```
 *
 * @param config database configuration.
 * @param logger logger used for debug output.
 */
internal class DatabaseManager(
    private val config: DatabaseConfig,
    private val logger: Logger
) : AutoCloseable {

    @Volatile
    private var dataSource: HikariDataSource? = null
    private val lock = Any()

    /**
     * Opens the connection pool using the information provided in [config].
     *
     * This method is idempotent: if the pool is already initialized, it returns immediately.
     * The configuration is validated before creating the pool.
     *
     * @throws SQLException if a connection pool cannot be created.
     */
    @Throws(SQLException::class)
    fun connect() {
        if (dataSource != null) return

        synchronized(lock) {
            if (dataSource != null) return

            val hikari = HikariConfig().apply {
                when (config.type) {
                    DatabaseType.MYSQL, DatabaseType.MARIADB -> {
                        jdbcUrl = "jdbc:${config.type.name.lowercase()}://${config.host}:${config.port}/${config.database}"
                        username = config.username
                        password = config.password
                        driverClassName = if (config.type == DatabaseType.MYSQL) {
                            "com.mysql.cj.jdbc.Driver"
                        } else {
                            "org.mariadb.jdbc.Driver"
                        }
                    }
                    DatabaseType.POSTGRESQL -> {
                        jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.database}"
                        username = config.username
                        password = config.password
                        driverClassName = "org.postgresql.Driver"
                    }
                    DatabaseType.MSSQL -> {
                        jdbcUrl = "jdbc:sqlserver://${config.host}:${config.port};databaseName=${config.database}"
                        username = config.username
                        password = config.password
                        driverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
                    }
                    DatabaseType.SQLITE -> {
                        val path = config.filePath ?: config.database
                        jdbcUrl = "jdbc:sqlite:$path"
                        driverClassName = "org.sqlite.JDBC"
                        maximumPoolSize = 1
                        minimumIdle = 1
                    }
                    DatabaseType.H2 -> {
                        val path = config.filePath ?: config.database
                        jdbcUrl = "jdbc:h2:$path"
                        username = config.username
                        password = config.password
                        driverClassName = "org.h2.Driver"
                    }
                }
                connectionTimeout = 30_000
                validationTimeout = 5_000
            }

            try {
                hikari.validate()
                dataSource = HikariDataSource(hikari)
                logger.debug("Connected to ${config.type} database")
            } catch (e: SQLException) {
                logger.severe("Failed to connect to ${config.type} database: ${e.message}")
                throw e
            }
        }
    }

    /**
     * Closes the connection pool and frees resources.
     * It is safe to call this method multiple times.
     */
    override fun close() {
        synchronized(lock) {
            dataSource?.close()
            dataSource = null
        }
    }

    /**
     * Retrieves a connection from the pool.
     *
     * @return active JDBC [Connection].
     * @throws SQLException if the pool has not been initialised or cannot provide a connection.
     */
    @Throws(SQLException::class)
    fun getConnection(): Connection =
        dataSource?.connection ?: throw SQLException("DataSource not initialized")

    /**
     * Executes an update, insert or delete statement.
     *
     * @param sql SQL statement with `?` placeholders.
     * @param params parameters bound to the placeholders in [sql].
     * @return number of rows affected by the statement.
     * @throws SQLException if an error occurs during execution.
     */
    @Throws(SQLException::class)
    fun execute(sql: String, vararg params: Any?): Int = getConnection().use { conn ->
        conn.prepareStatement(sql).use { ps ->
            params.forEachIndexed { index, param -> ps.setObject(index + 1, param) }
            ps.executeUpdate()
        }
    }

    /**
     * Executes a select query and maps each row using [mapper].
     *
     * @param sql SQL select query with `?` placeholders.
     * @param params parameters bound to the placeholders in [sql].
     * @param mapper function converting each [ResultSet] row into a value of type [T].
     * @return list of mapped results, possibly empty.
     * @throws SQLException if an error occurs during execution.
     */
    @Throws(SQLException::class)
    fun <T> query(
        sql: String,
        vararg params: Any?,
        mapper: (ResultSet) -> T
    ): List<T> = getConnection().use { conn ->
        conn.prepareStatement(sql).use { ps ->
            params.forEachIndexed { index, param -> ps.setObject(index + 1, param) }
            ps.executeQuery().use { rs ->
                val results = mutableListOf<T>()
                while (rs.next()) {
                    results += mapper(rs)
                }
                results
            }
        }
    }

    /**
     * Creates a table if it does not already exist using the provided [schema].
     *
     * @param schema description of the table to create.
     * @throws SQLException if the statement fails.
     */
    @Throws(SQLException::class)
    fun createTable(schema: TableSchema) {
        val columnsSql = schema.columns.joinToString(", ") { "${it.name} ${it.definition}" }
        val sql = "CREATE TABLE IF NOT EXISTS ${schema.name} ($columnsSql)"
        execute(sql)
    }

    /**
     * Convenience wrapper that invokes [createTable] for each provided schema.
     *
     * @param schemas collection of table schemas to create.
     * @throws SQLException if any statement fails.
     */
    @Throws(SQLException::class)
    fun createTables(vararg schemas: TableSchema) = schemas.forEach { createTable(it) }
}


internal enum class DatabaseType { MYSQL, MARIADB, POSTGRESQL, MSSQL, SQLITE, H2 }
