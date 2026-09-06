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
            return
        }

        val playerItems = mutableMapOf<Int, ItemStack>()

        for (i in 0..35) {
            player.inventory.getItem(i)?.let { playerItems[i] = it.clone() }
        }

        playerItems[36] = player.inventory.helmet.clone()
        playerItems[37] = player.inventory.chestplate.clone()
        playerItems[38] = player.inventory.leggings.clone()
        playerItems[39] = player.inventory.boots.clone()
        player.inventory.itemInOffHand.let { playerItems[40] = it.clone() }
        val totalXP = player.totalExperience
        val hasAnyRealItem = playerItems.values.any { it.type != Material.AIR && it.amount > 0 }
        if (!hasAnyRealItem && totalXP <= 0) {
            return
        }

        val grave = plugin.graveManager.createGraveAndGetIt(player, playerItems, totalXP)
        if (grave == null) {
            return
        }

        player.totalExperience = 0
        player.exp = 0f
        player.level = 0

        event.drops.clear()
        event.keepInventory = false
        event.droppedExp = 0
        event.newExp = 0
        event.newLevel = 0
        event.newTotalExp = 0

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
