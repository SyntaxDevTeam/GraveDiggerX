package pl.syntaxdevteam.gravediggerx.spirits

import org.bukkit.Location
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GhostManager(private val plugin: GraveDiggerX) {

    private val activeGhosts = ConcurrentHashMap<UUID, GhostSpirit>()

    fun createGhost(graveOwnerId: UUID, graveLocation: Location, ownerName: String): GhostSpirit? {
        val enabled = plugin.config.getBoolean("spirits.enabled", true)
        if (!enabled) return null

        val ghost = GhostSpirit(plugin, graveOwnerId, graveLocation)
        ghost.spawn()
        activeGhosts[graveOwnerId] = ghost

        return ghost
    }

    fun createGhostAndGetId(graveOwnerId: UUID, graveLocation: Location, ownerName: String): UUID? {
        val ghost = createGhost(graveOwnerId, graveLocation, ownerName)
        return ghost?.entity?.uniqueId
    }

    fun removeGhost(graveOwnerId: UUID) {
        activeGhosts[graveOwnerId]?.despawn()
        activeGhosts.remove(graveOwnerId)
    }

    fun removeAllGhosts() {
        activeGhosts.values.forEach { it.despawn() }
        activeGhosts.clear()
    }
}