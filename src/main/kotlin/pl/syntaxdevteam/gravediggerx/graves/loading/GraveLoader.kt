package pl.syntaxdevteam.gravediggerx.graves.loading

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.SchedulerProvider
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.graves.registry.GraveRegistry
import pl.syntaxdevteam.gravediggerx.graves.rendering.GraveWorldRenderer
import pl.syntaxdevteam.gravediggerx.spirits.GhostSpirit

class GraveLoader(
    private val plugin: GraveDiggerX,
    private val registry: GraveRegistry,
    private val renderer: GraveWorldRenderer
) {
    fun load() {
        removeStaleVisualEntities()
        SchedulerProvider.runAsync(plugin, Runnable {
            plugin.databaseHandler.loadAllGraves().forEach(::loadIntoWorld)
        })
    }

    private fun loadIntoWorld(grave: Grave) {
        val location = grave.location
        location.world?.getChunkAtAsync(location)?.thenAccept {
            SchedulerProvider.runSyncAt(plugin, location) {
                val rendered = renderer.place(location, grave.ownerId, grave.ownerName, grave.isPublic, spawnGhost = false)
                val loaded = grave.copy(
                    hologramIds = rendered.hologramIds,
                    ghostEntityId = null,
                    ghostActive = grave.ghostActive
                )
                grave.hologramIds.forEach { Bukkit.getEntity(it)?.remove() }
                registry.add(loaded)
                restoreGhostLater(loaded)
                if (!loaded.isPublic) plugin.timeGraveRemove.scheduleRemoval(loaded)
            }
        }
    }

    private fun restoreGhostLater(grave: Grave) {
        if (!grave.ghostActive) return
        SchedulerProvider.runSyncLaterAt(plugin, grave.location, 40L) {
            val ghostId = plugin.ghostManager.createGhostAndGetId(grave.ownerId, grave.location, grave.ownerName)
            if (ghostId != null) {
                registry.findAt(grave.location)?.let {
                    registry.add(it.copy(ghostEntityId = ghostId, ghostActive = true))
                }
            }
        }
    }

    private fun removeStaleVisualEntities() {
        val hologramKey = NamespacedKey(plugin, "grave_hologram")
        val ghostKey = GhostSpirit.key(plugin)
        val legacyGhostKey = GhostSpirit.legacyKey()
        Bukkit.getWorlds().flatMap { it.entities }.forEach { entity ->
            val staleHologram = entity is TextDisplay &&
                entity.persistentDataContainer.has(hologramKey, PersistentDataType.STRING)
            val staleGhost = entity.persistentDataContainer.has(ghostKey, PersistentDataType.STRING) ||
                entity.persistentDataContainer.has(legacyGhostKey, PersistentDataType.STRING)
            if (staleHologram || staleGhost) entity.remove()
        }
    }
}
