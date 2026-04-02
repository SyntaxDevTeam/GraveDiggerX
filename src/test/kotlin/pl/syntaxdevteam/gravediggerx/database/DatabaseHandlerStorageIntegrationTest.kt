package pl.syntaxdevteam.gravediggerx.database

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.nio.file.Files
import java.nio.file.Path
import pl.syntaxdevteam.core.logging.Logger

class DatabaseHandlerStorageIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `sqlite backend works on temporary filesystem with real DatabaseManager`() {
        val handler = newHandler(tempDir, dbType = "sqlite", dbName = "integration_ok")

        handler.connect()
        handler.ensureSchema()
        handler.replaceAllGraves(emptyList())
        val loaded = handler.loadAllGraves()
        handler.close()

        assertTrue(Files.exists(tempDir.resolve("database/integration_ok.db")))
        assertEquals(emptyList(), loaded)
    }

    @Test
    fun `sql to file fallback triggers when sql query fails before schema`() {
        val handler = newHandler(tempDir, dbType = "sqlite", dbName = "integration_fallback")

        handler.connect()
        // celowo bez ensureSchema(): DELETE FROM graves rzuci wyjątek i powinien włączyć fallback file.
        handler.replaceAllGraves(emptyList())
        handler.close()

        val jsonFile = tempDir.resolve("data.json")
        assertTrue(Files.exists(jsonFile))
        assertEquals("[]", Files.readString(jsonFile).trim())
    }

    private fun newHandler(dataFolder: Path, dbType: String, dbName: String): DatabaseHandler {
        val values = mapOf(
            "database.type" to dbType,
            "database.sql.dbname" to dbName,
            "database.sql.host" to "localhost",
            "database.sql.username" to "root",
            "database.sql.password" to ""
        )

        return DatabaseHandler(
            logger = mock<Logger>(),
            pluginName = "GraveDiggerX-Test",
            dataFolder = dataFolder.toFile(),
            configString = { key -> values[key] },
            configInt = { _ -> 0 }
        )
    }
}
