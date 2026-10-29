package pl.syntaxdevteam.gravediggerx.graves.backup

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import java.util.UUID

data class GraveBackup(
    val id: UUID = UUID.randomUUID(),
    val ownerId: UUID,
    val ownerName: String,
    val location: Location,
    val items: Map<Int, ItemStack>,
    val armorContents: Map<String, ItemStack> = emptyMap(),
    val storedXp: Int = 0,
    val createdAt: Long,
    val backedUpAt: Long = System.currentTimeMillis(),
    // Keep the name even when the world is not loaded at plugin startup.
    val worldName: String? = location.world?.name
) {
    /** Resolve at restore time, since multi-world plugins may load worlds after us. */
    fun resolveLocation(): Location? {
        val world = worldName?.let { Bukkit.getWorld(it) } ?: return null
        return Location(world, location.x, location.y, location.z, location.yaw, location.pitch)
    }
}
