package pl.syntaxdevteam.gravediggerx.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.gravediggerx.GraveDiggerX

class GraveProtectionListener(
    private val plugin: GraveDiggerX
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (plugin.graveManager.getGraveAt(event.block.location) != null) {
            event.isCancelled = true
        }
    }



    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!plugin.config.getBoolean("graves.protection.explosions", true)) return
        event.blockList().removeIf { plugin.graveManager.getGraveAt(it.location) != null }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (!plugin.config.getBoolean("graves.protection.explosions", true)) return
        event.blockList().removeIf { plugin.graveManager.getGraveAt(it.location) != null }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockFromTo(event: BlockFromToEvent) {
        if (!plugin.config.getBoolean("graves.protection.fluids", true)) return
        if (plugin.graveManager.getGraveAt(event.toBlock.location) != null) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (!plugin.config.getBoolean("graves.protection.mobs", true)) return
        if (plugin.graveManager.getGraveAt(event.block.location) != null) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (!plugin.config.getBoolean("graves.protection.pistons", true)) return
        if (event.blocks.any { plugin.graveManager.getGraveAt(it.location) != null }) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (!plugin.config.getBoolean("graves.protection.pistons", true)) return
        if (event.blocks.any { plugin.graveManager.getGraveAt(it.location) != null }) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockMove(event: InventoryMoveItemEvent) {
        if (!plugin.config.getBoolean("graves.protection.hoppers", true)) return
        val destination = event.destination.location ?: return
        if (plugin.graveManager.getGraveAt(destination) != null) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (isGhostSpirit(event.entity)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (isGhostSpirit(event.entity)) {
            event.isCancelled = true
        }
    }

    private fun isGhostSpirit(entity: org.bukkit.entity.Entity): Boolean {
        return entity.persistentDataContainer.has(
            NamespacedKey(plugin, "ghost_spirit"),
            PersistentDataType.STRING
        )
    }
}
