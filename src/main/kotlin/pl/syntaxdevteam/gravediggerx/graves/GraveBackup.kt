package pl.syntaxdevteam.gravediggerx.graves

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
    val backedUpAt: Long = System.currentTimeMillis()
)
