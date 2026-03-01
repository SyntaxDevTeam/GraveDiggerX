package pl.syntaxdevteam.gravediggerx.integrations

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

fun interface RegionOwnershipChecker {
    fun canPlaceGrave(player: Player, location: Location): Boolean

    companion object {
        val ALLOW_ALL = RegionOwnershipChecker { _, _ -> true }

        fun create(plugin: Plugin): RegionOwnershipChecker {
            if (plugin.server.pluginManager.getPlugin("WorldGuard")?.isEnabled != true) {
                return ALLOW_ALL
            }

            return runCatching {
                val clazz = Class.forName("pl.syntaxdevteam.gravediggerx.integrations.worldguard.WorldGuardRegionOwnershipChecker")
                val constructor = clazz.getDeclaredConstructor()
                constructor.isAccessible = true
                constructor.newInstance() as RegionOwnershipChecker
            }.getOrElse {
                plugin.logger.warning("WorldGuard was detected but region ownership checks could not be enabled: ${it.message}")
                ALLOW_ALL
            }
        }
    }
}
