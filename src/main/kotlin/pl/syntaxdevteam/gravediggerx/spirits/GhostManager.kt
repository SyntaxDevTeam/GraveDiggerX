package pl.syntaxdevteam.gravediggerx.spirits

import org.bukkit.Location
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GhostManager(private val plugin: GraveDiggerX) {

    // A player may have several graves, so a ghost must be tracked by its grave,
    // not by the grave owner's UUID.
    private val activeGhosts = ConcurrentHashMap<String, GhostSpirit>()

    fun createGhost(graveOwnerId: UUID, graveLocation: Location, ownerName: String): GhostSpirit? {
        val enabled = plugin.config.getBoolean("spirits.enabled", true)
        if (!enabled) return null

        val ghost = GhostSpirit(plugin, graveOwnerId, graveLocation)
        ghost.spawn()
        activeGhosts[graveKey(graveLocation)] = ghost

        return ghost
    }

    fun createGhostAndGetId(graveOwnerId: UUID, graveLocation: Location, ownerName: String): UUID? {
        val ghost = createGhost(graveOwnerId, graveLocation, ownerName)
        return ghost?.entity?.uniqueId
    }

    fun removeGhost(graveLocation: Location) {
        activeGhosts.remove(graveKey(graveLocation))?.despawn()
    }

    fun removeAllGhosts() {
        activeGhosts.values.forEach { it.despawn() }
        activeGhosts.clear()
    }

    private fun graveKey(location: Location): String {
        val worldId = location.world?.uid ?: return "unloaded:${location.blockX}:${location.blockY}:${location.blockZ}"
        return "$worldId:${location.blockX}:${location.blockY}:${location.blockZ}"
    }
}
