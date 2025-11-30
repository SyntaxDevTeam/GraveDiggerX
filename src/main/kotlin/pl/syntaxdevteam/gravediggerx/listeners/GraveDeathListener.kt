package pl.syntaxdevteam.gravediggerx.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import pl.syntaxdevteam.gravediggerx.GraveDiggerX

class GraveDeathListener(private val plugin: GraveDiggerX) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity

        val env = player.world.environment
        val enabledInWorld = when (env) {
            org.bukkit.World.Environment.NORMAL -> plugin.config.getBoolean("graves.worlds.overworld", true)
            org.bukkit.World.Environment.NETHER -> plugin.config.getBoolean("graves.worlds.nether", true)
            org.bukkit.World.Environment.THE_END -> plugin.config.getBoolean("graves.worlds.end", true)
            else -> true
        }

        if (!enabledInWorld) {
            event.keepInventory = false
            return
        }

        if (event.keepInventory) {
            return
        }

        val playerItems: Map<Int, ItemStack> = event.drops
            .withIndex()
            .associate { it.index to it.value }

        val totalXP = event.droppedExp
        val grave = plugin.graveManager.createGraveAndGetIt(player, playerItems, totalXP)

        if (grave != null) {
            event.drops.clear()
            event.droppedExp = 0

            val message = plugin.messageHandler.stringMessageToComponent(
                "graves",
                "created-grave",
                mapOf(
                    "x" to player.location.blockX.toString(),
                    "y" to player.location.blockY.toString(),
                    "z" to player.location.blockZ.toString()
                )
            )
            player.sendMessage(message)
        }
    }
}