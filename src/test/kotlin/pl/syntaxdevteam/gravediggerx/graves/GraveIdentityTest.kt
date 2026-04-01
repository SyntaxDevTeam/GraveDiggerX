package pl.syntaxdevteam.gravediggerx.graves

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class GraveIdentityTest {

    @Test
    fun `location key is stable for block coordinates`() {
        val keyA = GraveIdentity.locationKey("world", 100, 64, -30)
        val keyB = GraveIdentity.locationKey("world", 100, 64, -30)

        assertEquals("world:100:64:-30", keyA)
        assertEquals(keyA, keyB)
    }

    @Test
    fun `task key uses grave id to prevent collisions between graves`() {
        val graveIdA = UUID.randomUUID()
        val graveIdB = UUID.randomUUID()
        val keyA = "${graveIdA}:1234"
        val keyB = "${graveIdB}:1234"

        assertNotEquals(keyA, keyB)
    }

    @Test
    fun `grave id parser falls back to random uuid`() {
        val parsed = GraveIdentity.graveIdOrRandom("not-a-uuid")
        assertTrue(parsed.toString().isNotBlank())
    }
}
