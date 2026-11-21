package pl.syntaxdevteam.gravediggerx.graves.backup

import org.bukkit.Bukkit
import org.bukkit.World
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class GraveBackupWorldTest {
    private fun json(worldName: String) = """
        [{"ownerId":"00000000-0000-0000-0000-000000000001","ownerName":"Gabriela_Elza",
          "location":{"world":"$worldName","x":100,"y":64,"z":-30},"items":[]}]
    """.trimIndent()

    @Test
    fun `missing world is not replaced by the default world and survives saving`() {
        val defaultWorld = mock<World>()
        mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<List<World>> { Bukkit.getWorlds() }.thenReturn(listOf(defaultWorld))
            val backup = GraveBackupSerializer.decodeBackupsFromString(json("survival_nether")).single()
            assertNull(backup.location.world)
            assertNull(backup.resolveLocation())
            val saved = GraveBackupSerializer.encodeBackups(listOf(backup)).toString()
            val reloaded = GraveBackupSerializer.decodeBackupsFromString(saved).single()
            assertEquals("survival_nether", reloaded.worldName)
            assertEquals(100, reloaded.location.blockX)
            assertNull(reloaded.resolveLocation())
        }
    }

    @Test
    fun `world loaded after backup decoding is resolved when restoring`() {
        mockStatic(Bukkit::class.java).use { bukkit ->
            val backup = GraveBackupSerializer.decodeBackupsFromString(json("adventure")).single()
            assertNull(backup.resolveLocation())
            val world = mock<World>()
            bukkit.`when`<World?> { Bukkit.getWorld("adventure") }.thenReturn(world)
            val restored = backup.resolveLocation()!!
            assertSame(world, restored.world)
            assertEquals(100, restored.blockX)
            assertEquals(64, restored.blockY)
            assertEquals(-30, restored.blockZ)
            assertNull(backup.location.world) // Snapshot remains independent of resolution.
        }
    }

    @Test
    fun `same coordinates in different worlds remain distinct`() {
        mockStatic(Bukkit::class.java).use { bukkit ->
            val overworld = mock<World>()
            val end = mock<World>()
            whenever(overworld.name).thenReturn("survival")
            whenever(end.name).thenReturn("survival_the_end")
            bukkit.`when`<World?> { Bukkit.getWorld("survival") }.thenReturn(overworld)
            bukkit.`when`<World?> { Bukkit.getWorld("survival_the_end") }.thenReturn(end)
            val backups = listOf("survival", "survival_the_end").map {
                GraveBackupSerializer.decodeBackupsFromString(json(it)).single()
            }
            val reloaded = GraveBackupSerializer.decodeBackupsFromString(
                GraveBackupSerializer.encodeBackups(backups).toString()
            )
            assertSame(overworld, reloaded[0].resolveLocation()!!.world)
            assertSame(end, reloaded[1].resolveLocation()!!.world)
        }
    }
}
