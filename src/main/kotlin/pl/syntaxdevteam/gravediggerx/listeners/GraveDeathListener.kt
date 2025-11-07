package pl.syntaxdevteam.gravediggerx.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.gravediggerx.GraveDiggerX

class GraveDeathListener(private val plugin: GraveDiggerX) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity

        val playerItems = mutableMapOf<Int, ItemStack>()

        for (i in 0..35) {
            player.inventory.getItem(i)?.let { playerItems[i] = it }
        }

        player.inventory.helmet?.let { playerItems[36] = it }
        player.inventory.chestplate?.let { playerItems[37] = it }
        player.inventory.leggings?.let { playerItems[38] = it }
        player.inventory.boots?.let { playerItems[39] = it }
        player.inventory.itemInOffHand?.let { playerItems[40] = it }

        val totalXP = player.totalExperience

        player.totalExperience = 0
        player.exp = 0f
        player.level = 0

        event.drops.clear()
        event.keepInventory = false

        val grave = plugin.graveManager.createGraveAndGetIt(player, playerItems, totalXP)

        event.droppedExp = 0

        val message = plugin.messageHandler.getMessage(
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
