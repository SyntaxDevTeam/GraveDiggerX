package pl.syntaxdevteam.gravediggerx.graves

import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.inventory.ItemStack
import java.util.UUID

data class Grave(
    val ownerId: UUID,
    val ownerName: String = "Unknown",
    val location: Location,
    val items: Map<Int, ItemStack>,
    val hologramIds: List<UUID>,
    val originalBlockData: BlockData,
    val storedXp: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val ghostEntityId: UUID? = null,
    val ghostActive: Boolean = true,
    var lastAttackerId: UUID? = null,
    var itemsStolen: Int = 0
)
