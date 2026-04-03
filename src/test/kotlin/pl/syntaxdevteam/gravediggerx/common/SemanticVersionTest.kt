package pl.syntaxdevteam.gravediggerx.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemanticVersionTest {

    @Test
    fun `parse should support major minor patch`() {
        val version = SemanticVersion.parse("1.21.11")
        assertEquals(1, version.major)
        assertEquals(21, version.minor)
        assertEquals(11, version.patch)
    }

    @Test
    fun `parse should default patch to zero when missing`() {
        val version = SemanticVersion.parse("26.1")
        assertEquals(SemanticVersion(26, 1, 0), version)
    }

    @Test
    fun `parse should extract semantic version from raw server strings`() {
        val version = SemanticVersion.parse("1.21.11-R0.1-SNAPSHOT")
        assertEquals(SemanticVersion(1, 21, 11), version)
    }

    @Test
    fun `compare should follow semantic ordering`() {
        assertTrue(SemanticVersion(1, 21, 11) >= SemanticVersion(1, 21, 10))
        assertFalse(SemanticVersion(1, 20, 6) >= SemanticVersion(1, 21, 0))
    }

    @Test
    fun `isBetween should respect both bounded and open ranges`() {
        val value = SemanticVersion(1, 21, 11)
        assertTrue(value.isBetween(SemanticVersion(1, 21, 0), SemanticVersion(1, 21, 11)))
        assertTrue(value.isBetween(SemanticVersion(1, 21, 0), null))
        assertFalse(value.isBetween(SemanticVersion(1, 22, 0), null))
    }
}
