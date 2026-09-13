package pl.syntaxdevteam.gravediggerx.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.integrations.VaultEconomyProvider

class GraveListGUI(
    player: Player,
    private val plugin: GraveDiggerX
) : Listener {

    private val inventory: Inventory = Bukkit.createInventory(
        null,
        27,
        plugin.messageHandler.stringMessageToComponentNoPrefix("gui-list", "title", emptyMap())
    )

    init {
        setupInventory(player)
    }

    private fun setupInventory(player: Player) {
        inventory.clear()
        val userGraves = plugin.graveManager.getGravesFor(player.uniqueId)
        for ((index, grave) in userGraves.withIndex()) {
            if (index >= 27) break
            inventory.setItem(index, createGraveIcon(grave, index + 1))
        }
    }

    private fun createGraveIcon(grave: Grave, displayIndex: Int): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta

        val remainingTime = getFormattedRemainingTime(grave)
        val cost = plugin.config.getDouble("teleport.cost", 100.0)

        val placeholders = mapOf(
            "index" to displayIndex.toString(),
            "time_left" to remainingTime,
            "cost" to cost.toString(),
            "xp" to grave.storedXp.toString()
        )

        meta.displayName(plugin.messageHandler.stringMessageToComponentNoPrefix("gui-list", "item-name", placeholders))
        meta.lore(plugin.messageHandler.getSmartMessage("gui-list", "item-lore", placeholders))
        item.itemMeta = meta
        return item
    }

    private fun getFormattedRemainingTime(grave: Grave): String {
        val totalDespawnSeconds = plugin.config.getInt("graves.grave-despawn", 120)
        val elapsedSeconds = (System.currentTimeMillis() - grave.createdAt) / 1000
        val secondsLeft = (totalDespawnSeconds - elapsedSeconds).coerceAtLeast(0)

        val minutes = secondsLeft / 60
        val seconds = secondsLeft % 60

        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    fun open(target: Player) {
        val userGraves = plugin.graveManager.getGravesFor(target.uniqueId)
        if (userGraves.isEmpty()) {
            target.sendMessage(plugin.messageHandler.stringMessageToComponent("graves", "no-graves", emptyMap()))
            return
        }

        setupInventory(target)
        Bukkit.getPluginManager().registerEvents(this, plugin)
        target.openInventory(inventory)
        target.playSound(target.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val clicker = event.whoClicked as? Player ?: return
        if (event.view.topInventory != inventory) return

        event.isCancelled = true
        val slot = event.rawSlot
        val userGraves = plugin.graveManager.getGravesFor(clicker.uniqueId)
        if (slot !in userGraves.indices) return

        val selectedGrave = userGraves[slot]

        if (plugin.graveManager.getGraveAt(selectedGrave.location) == null) {
            inventory.setItem(slot, null)
            clicker.sendMessage(plugin.messageHandler.stringMessageToComponent("graves", "no-graves", emptyMap()))
            clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            return
        }

        if (!plugin.config.getBoolean("teleport.enabled", true)) {
            clicker.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "unknown-command", emptyMap())) // lub dedykowana wiadomość o wyłączonym TP
            clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            return
        }

        val cost = plugin.config.getDouble("teleport.cost", 100.0)

        if (cost > 0.0) {
            if (!VaultEconomyProvider.hasEnough(clicker, cost)) {
                clicker.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "not-enough-money", mapOf("cost" to cost.toString())))
                clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                return
            }
            if (!VaultEconomyProvider.withdraw(clicker, cost)) {
                clicker.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "transaction-failed", emptyMap()))
                return
            }
        }

        clicker.closeInventory()
        val targetLocation = selectedGrave.location.clone().add(0.5, 1.0, 0.5)
        clicker.teleport(targetLocation)
        clicker.playSound(targetLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
        clicker.sendMessage(plugin.messageHandler.stringMessageToComponent("graves", "teleported-to-grave", mapOf("cost" to cost.toString())))
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory != inventory) return
        HandlerList.unregisterAll(this)
    }
}