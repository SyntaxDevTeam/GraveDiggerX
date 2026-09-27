package pl.syntaxdevteam.gravediggerx.integrations

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

fun interface RegionOwnershipChecker {
    fun canPlaceGrave(player: Player, location: Location): Boolean

    companion object {
        val ALLOW_ALL = RegionOwnershipChecker { _, _ -> true }

        fun create(plugin: Plugin): RegionOwnershipChecker {
            val wgPlugin = plugin.server.pluginManager.getPlugin("WorldGuard")
            if (wgPlugin == null || !wgPlugin.isEnabled) {
                plugin.logger.info("[WG Integration] WorldGuard not found or disabled. Skipping region checks.")
                return ALLOW_ALL
            }

            return runCatching {
                val clazz = Class.forName("pl.syntaxdevteam.gravediggerx.integrations.worldguard.WorldGuardRegionOwnershipChecker")
                val constructor = clazz.getDeclaredConstructor()
                constructor.isAccessible = true
                val instance = constructor.newInstance() as RegionOwnershipChecker
                plugin.logger.info("[WG Integration] WorldGuardRegionOwnershipChecker successfully loaded!")
                instance
            }.getOrElse {
                plugin.logger.warning("[WG Integration] Failed to load WorldGuardRegionOwnershipChecker: ${it.message}")
                it.printStackTrace()
                ALLOW_ALL
            }
        }
    }
}
