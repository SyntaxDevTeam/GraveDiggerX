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

class GraveConfirmGUI(
    private val player: Player,
    private val grave: Grave,
    private val plugin: GraveDiggerX
) : Listener {

    private val inventory: Inventory = Bukkit.createInventory(
        null,
        9,
        plugin.messageHandler.stringMessageToComponentNoPrefix("gui-confirm", "title", emptyMap())
    )

    init {
        setupInventory()
    }

    private fun setupInventory() {
        inventory.clear()

        val cost = plugin.config.getDouble("teleport.cost", 100.0)
        val loc = grave.location
        val placeholders = mapOf(
            "cost" to cost.toString(),
            "x" to loc.blockX.toString(),
            "y" to loc.blockY.toString(),
            "z" to loc.blockZ.toString()
        )

        // Zielony przycisk - Potwierdź
        val confirmItem = ItemStack(Material.LIME_CONCRETE)
        val confirmMeta = confirmItem.itemMeta
        confirmMeta.displayName(plugin.messageHandler.stringMessageToComponentNoPrefix("gui-confirm", "confirm-name", placeholders))
        confirmMeta.lore(plugin.messageHandler.getSmartMessage("gui-confirm", "confirm-lore", placeholders))
        confirmItem.itemMeta = confirmMeta
        inventory.setItem(2, confirmItem)

        // Czerwony przycisk - Anuluj
        val cancelItem = ItemStack(Material.RED_CONCRETE)
        val cancelMeta = cancelItem.itemMeta
        cancelMeta.displayName(plugin.messageHandler.stringMessageToComponentNoPrefix("gui-confirm", "cancel-name", placeholders))
        cancelMeta.lore(plugin.messageHandler.getSmartMessage("gui-confirm", "cancel-lore", placeholders))
        cancelItem.itemMeta = cancelMeta
        inventory.setItem(6, cancelItem)
    }

    fun open() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        player.openInventory(inventory)
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val clicker = event.whoClicked as? Player ?: return
        if (event.view.topInventory != inventory) return

        event.isCancelled = true

        // Sprawdzenie czy grób nadal istnieje przed podjęciem akcji
        if (plugin.graveManager.getGraveAt(grave.location) == null) {
            clicker.closeInventory()
            clicker.sendMessage(plugin.messageHandler.stringMessageToComponent("graves", "no-graves", emptyMap()))
            clicker.playSound(clicker.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            return
        }

        when (event.rawSlot) {
            2 -> { // Potwierdzenie teleportu
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
                val targetLocation = grave.location.clone().add(0.5, 1.0, 0.5)
                clicker.teleport(targetLocation)
                clicker.playSound(targetLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
                clicker.sendMessage(plugin.messageHandler.stringMessageToComponent("graves", "teleported-to-grave", mapOf("cost" to cost.toString())))
            }
            6 -> { // Anulowanie - powrót do głównej listy grobów
                clicker.closeInventory()
                GraveListGUI(clicker, plugin).open(clicker)
                clicker.playSound(clicker.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory != inventory) return
        HandlerList.unregisterAll(this)
    }
}