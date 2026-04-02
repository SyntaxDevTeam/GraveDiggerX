package pl.syntaxdevteam.gravediggerx.database

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import pl.syntaxdevteam.core.logging.Logger
import pl.syntaxdevteam.gravediggerx.graves.CollectionState
import pl.syntaxdevteam.gravediggerx.graves.Grave
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun `sql integration covers claims and full tx lifecycle`() {
        val handler = newHandler(tempDir, dbType = "sqlite", dbName = "integration_claims_tx")
        val grave = graveStub(UUID.fromString("00000000-0000-0000-0000-000000000111"))
        val collector = UUID.fromString("00000000-0000-0000-0000-000000000222")

        handler.connect()
        handler.ensureSchema()

        assertTrue(handler.tryAcquireCollectionClaim(grave))
        assertEquals(false, handler.tryAcquireCollectionClaim(grave))
        handler.releaseCollectionClaim(grave)
        assertTrue(handler.tryAcquireCollectionClaim(grave))

        val tx = handler.beginCollectionTx(grave, collector, ttlMillis = 5_000)
        assertNotNull(tx)
        assertTrue(handler.transitionCollectionTx(grave.graveId, tx.txId, CollectionState.CLAIMED, CollectionState.COLLECTING))
        assertTrue(handler.markCollectedTx(grave.graveId, tx.txId))
        assertNull(handler.beginCollectionTx(grave, collector, ttlMillis = 5_000))

        handler.clearCollectionState(grave)
        assertNotNull(handler.beginCollectionTx(grave, collector, ttlMillis = 5_000))
        handler.close()
    }

    @Test
    fun `crash recovery integration keeps in-progress tx locked before ttl`() {
        val dbName = "integration_recovery_before_ttl"
        val grave = graveStub(UUID.fromString("00000000-0000-0000-0000-000000000333"))
        val collector = UUID.fromString("00000000-0000-0000-0000-000000000444")

        val first = newHandler(tempDir, dbType = "sqlite", dbName = dbName)
        first.connect()
        first.ensureSchema()
        val tx = first.beginCollectionTx(grave, collector, ttlMillis = 30_000)
        assertNotNull(tx)
        assertTrue(first.transitionCollectionTx(grave.graveId, tx.txId, CollectionState.CLAIMED, CollectionState.COLLECTING))
        first.close()

        val second = newHandler(tempDir, dbType = "sqlite", dbName = dbName)
        second.connect()
        second.ensureSchema()
        assertNull(second.beginCollectionTx(grave, collector, ttlMillis = 5_000))
        second.close()
    }

    @Test
    fun `crash recovery integration unlocks tx after ttl expiration`() {
        val dbName = "integration_recovery_after_ttl"
        val grave = graveStub(UUID.fromString("00000000-0000-0000-0000-000000000555"))
        val collector = UUID.fromString("00000000-0000-0000-0000-000000000666")

        val first = newHandler(tempDir, dbType = "sqlite", dbName = dbName)
        first.connect()
        first.ensureSchema()
        val tx = first.beginCollectionTx(grave, collector, ttlMillis = 50)
        assertNotNull(tx)
        assertTrue(first.transitionCollectionTx(grave.graveId, tx.txId, CollectionState.CLAIMED, CollectionState.COLLECTING))
        first.close()

        Thread.sleep(120)

        val second = newHandler(tempDir, dbType = "sqlite", dbName = dbName)
        second.connect()
        second.ensureSchema()
        assertNotNull(second.beginCollectionTx(grave, collector, ttlMillis = 5_000))
        second.close()
    }

    @Test
    fun `real sql concurrency allows only one claim winner`() {
        val handler = newHandler(tempDir, dbType = "sqlite", dbName = "integration_claim_race")
        val grave = graveStub(UUID.fromString("00000000-0000-0000-0000-000000000777"))
        handler.connect()
        handler.ensureSchema()

        val pool = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(10)
        val wins = AtomicInteger(0)

        repeat(10) {
            pool.submit {
                if (handler.tryAcquireCollectionClaim(grave)) {
                    wins.incrementAndGet()
                }
                latch.countDown()
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertEquals(1, wins.get())

        pool.shutdownNow()
        handler.releaseCollectionClaim(grave)
        handler.close()
    }

    private fun graveStub(graveId: UUID): Grave {
        val world = mock<World>()
        whenever(world.name).thenReturn("world")
        return Grave(
            graveId = graveId,
            ownerId = UUID.fromString("00000000-0000-0000-0000-000000000999"),
            ownerName = "tester",
            location = Location(world, 0.0, 64.0, 0.0),
            items = emptyMap(),
            armorContents = emptyMap(),
            hologramIds = emptyList(),
            originalBlockData = mock<BlockData>(),
            storedXp = 0,
            createdAt = 1L,
            ghostEntityId = null,
            ghostActive = false,
            isPublic = false,
            lastAttackerId = null,
            itemsStolen = 0
        )
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
