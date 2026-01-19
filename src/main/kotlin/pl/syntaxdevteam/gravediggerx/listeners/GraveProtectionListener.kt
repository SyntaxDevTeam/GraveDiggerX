package pl.syntaxdevteam.gravediggerx.listeners

import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.Skull
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.spirits.GhostSpirit

class GraveProtectionListener(
    private val plugin: GraveDiggerX
) : Listener {

    private fun isGrave(block: Block): Boolean {
        if (plugin.graveManager.getGraveAt(block.location) != null) {
            return true
        }
        val state = block.state
        if (state is Skull) {
            return state.persistentDataContainer.has(NamespacedKey(plugin, "grave"), PersistentDataType.STRING)
        }
        return false
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (isGrave(event.block)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!plugin.config.getBoolean("graves.protection.explosions", true)) return
        event.blockList().removeIf { isGrave(it) }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (!plugin.config.getBoolean("graves.protection.explosions", true)) return
        event.blockList().removeIf { isGrave(it) }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockFromTo(event: BlockFromToEvent) {
        if (!plugin.config.getBoolean("graves.protection.fluids", true)) return
        if (isGrave(event.toBlock)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (!plugin.config.getBoolean("graves.protection.fluids", true)) return
        if (isGrave(event.block)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (!plugin.config.getBoolean("graves.protection.mobs", true)) return
        if (isGrave(event.block)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (!plugin.config.getBoolean("graves.protection.pistons", true)) return
        if (event.blocks.any { isGrave(it) }) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (!plugin.config.getBoolean("graves.protection.pistons", true)) return
        if (event.blocks.any { isGrave(it) }) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockMove(event: InventoryMoveItemEvent) {
        if (!plugin.config.getBoolean("graves.protection.hoppers", true)) return
        val destination = event.destination.location ?: return
        if (isGrave(destination.block)) {
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
        val ghostKey = GhostSpirit.key(plugin)
        val legacyGhostKey = GhostSpirit.legacyKey()
        return entity.persistentDataContainer.has(ghostKey, PersistentDataType.STRING) ||
            entity.persistentDataContainer.has(legacyGhostKey, PersistentDataType.STRING)
    }
}
