package pl.syntaxdevteam.gravediggerx.common

import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.graves.GraveManager
import pl.syntaxdevteam.gravediggerx.graves.TimeGraveRemove
import pl.syntaxdevteam.gravediggerx.spirits.GhostManager

/**
 * Handles reloading runtime-managed components when the plugin configuration changes.
 */
class ReloadPlugin(private val plugin: GraveDiggerX) {

    fun reloadAll() {
        plugin.timeGraveRemove.cancelAll()
        plugin.graveManager.saveGravesToStorage()
        plugin.ghostManager.removeAllGhosts()

        plugin.graveManager = GraveManager(plugin)
        plugin.ghostManager = GhostManager(plugin)
        plugin.timeGraveRemove = TimeGraveRemove(plugin)

        plugin.graveManager.loadGravesFromStorage()
    }
}