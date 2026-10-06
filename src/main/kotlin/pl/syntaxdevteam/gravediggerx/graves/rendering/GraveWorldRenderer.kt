package pl.syntaxdevteam.gravediggerx.graves.rendering

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Skull
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.graves.GraveHologramManager
import pl.syntaxdevteam.gravediggerx.spirits.GhostSpirit
import java.util.UUID

data class RenderedGrave(val hologramIds: List<UUID>, val ghostEntityId: UUID?)

class GraveWorldRenderer(
    private val plugin: GraveDiggerX,
    private val holograms: GraveHologramManager,
    private val profiles: SkullProfileResolver
) {
    private val graveKey get() = NamespacedKey(plugin, "grave")
    private val hologramKey get() = NamespacedKey(plugin, "grave_hologram")

    fun place(location: Location, ownerId: UUID, ownerName: String, isPublic: Boolean, spawnGhost: Boolean): RenderedGrave {
        val block = location.block
        block.type = Material.PLAYER_HEAD
        (block.state as? Skull)?.apply {
            persistentDataContainer.set(graveKey, PersistentDataType.STRING, ownerId.toString())
            profiles.apply(this, ownerId, ownerName)
            update(true, false)
        }
        val seconds = plugin.config.getInt("graves.grave-despawn", 60)
        val hologramIds = holograms.createHologram(location, ownerName, seconds, isPublic)
        val ghostId = if (spawnGhost) plugin.ghostManager.createGhostAndGetId(ownerId, location, ownerName) else null
        return RenderedGrave(hologramIds, ghostId)
    }

    fun remove(grave: Grave) {
        val block = grave.location.toBlockLocation().block
        (block.state as? Skull)?.apply {
            persistentDataContainer.remove(graveKey)
            update(true, false)
        }
        block.blockData = grave.originalBlockData
        grave.hologramIds.forEach { Bukkit.getEntity(it)?.remove() }
        grave.ghostEntityId?.let { Bukkit.getEntity(it)?.remove() }
        plugin.ghostManager.removeGhost(grave.location)
    }

    fun removeUntrackedAt(location: Location): Boolean {
        val block = location.block
        if (block.type != Material.PLAYER_HEAD) return false
        location.world?.getNearbyEntities(location.clone().add(0.5, 1.5, 0.5), 1.0, 1.0, 1.0)
            ?.filterIsInstance<TextDisplay>()
            ?.filter { it.persistentDataContainer.has(hologramKey, PersistentDataType.STRING) }
            ?.forEach { it.remove() }
        removeGhostsNear(location)
        block.type = Material.AIR
        return true
    }

    private fun removeGhostsNear(location: Location) {
        val world = location.world ?: return
        val ghostKey = GhostSpirit.key(plugin)
        val legacyKey = GhostSpirit.legacyKey()
        world.getNearbyEntities(location.clone().add(0.5, 2.7, 0.5), 2.0, 2.0, 2.0).forEach { entity ->
            if (entity.persistentDataContainer.has(ghostKey, PersistentDataType.STRING) ||
                entity.persistentDataContainer.has(legacyKey, PersistentDataType.STRING)
            ) entity.remove()
        }
    }
}
