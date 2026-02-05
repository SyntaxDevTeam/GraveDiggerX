package pl.syntaxdevteam.gravediggerx.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
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
import pl.syntaxdevteam.gravediggerx.common.addItemOrDrop
import pl.syntaxdevteam.gravediggerx.common.equipSafely
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker

@Suppress("NestedLambdaShadowedImplicitParameter")
class GraveGUI(
    private val grave: Grave,
    private val plugin: GraveDiggerX
) : Listener {

    private val inventory: Inventory = Bukkit.createInventory(
        null,
        54,
        plugin.messageHandler.stringMessageToComponentNoPrefix("gui-grave", "title", emptyMap())
    )

    private var openedBy: Player? = null

    init {
        setupInventory()
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    private fun setupInventory() {
        for ((slot, item) in grave.items) {
            if (slot in 0 until 36 && slot < inventory.size - 9) {
                inventory.setItem(slot, item)
            }
        }

        val armorSlots = listOf(45, 46, 47, 48, 49)
        val armorItems = listOf(
            grave.armorContents["helmet"],
            grave.armorContents["chestplate"],
            grave.armorContents["leggings"],
            grave.armorContents["boots"],
            grave.armorContents["offhand"]
        )

        armorSlots.zip(armorItems).forEach { (slot, item) ->
            if (item != null && item.type != Material.AIR) {
                inventory.setItem(slot, item)
            }
        }

        inventory.setItem(51, createOwnerBanner())
        inventory.setItem(52, createXpBanner())
        inventory.setItem(53, createCollectButton())
    }

    private fun createOwnerBanner(): ItemStack {
        val banner = ItemStack(Material.WHITE_BANNER)
        val meta = banner.itemMeta
        val ownerName = grave.ownerName.ifBlank { plugin.messageHandler.stringMessageToStringNoPrefix("error", "unknown-player", emptyMap()) }
        val message = plugin.messageHandler.stringMessageToStringNoPrefix("gui-grave", "stats-owner", mapOf("player" to ownerName))
        meta.displayName(plugin.messageHandler.formatMixedTextToMiniMessage(message, null))
        banner.itemMeta = meta
        return banner
    }

    private fun createXpBanner(): ItemStack {
        val banner = ItemStack(Material.CYAN_BANNER)
        val meta = banner.itemMeta
        val message = plugin.messageHandler.stringMessageToStringNoPrefix("gui-grave", "stats-xp", mapOf("xp" to grave.storedXp.toString()))
        meta.displayName(plugin.messageHandler.formatMixedTextToMiniMessage(message, null))
        banner.itemMeta = meta
        return banner
    }

    private fun createCollectButton(): ItemStack {
        val item = ItemStack(Material.LIME_CANDLE)
        val meta = item.itemMeta

        val displayName = plugin.messageHandler.stringMessageToStringNoPrefix("gui-grave", "collect-item-name", emptyMap())
        val loreList = plugin.messageHandler.getSmartMessage("gui-grave", "collect-item-lore", emptyMap())

        meta.displayName(plugin.messageHandler.formatMixedTextToMiniMessage(displayName, null))
        meta.lore(loreList)
        item.itemMeta = meta

        return item
    }

    fun open(player: Player) {
        if (!PermissionChecker.has(player, PermissionChecker.PermissionKey.OPEN_GRAVE)) {
            val msg = plugin.messageHandler.stringMessageToComponent("error", "no-permission", emptyMap())
            player.sendMessage(msg)
            return
        }

        openedBy = player
        player.openInventory(inventory)
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
    }


    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.view.topInventory != inventory) return

        event.isCancelled = true

        if (event.slot == 53) {
            collectAll(player)
            player.closeInventory()
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory != inventory) return
        openedBy = null
        HandlerList.unregisterAll(this)
    }

    private fun collectAll(player: Player) {
        if (player.uniqueId != grave.ownerId && !grave.isPublic) {
            val loc = grave.location.clone().add(0.5, 0.5, 0.5)
            val world = loc.world ?: return

            val notYourGraveMsg = plugin.messageHandler.stringMessageToComponent("graves", "not-your-grave", emptyMap())
            player.sendMessage(notYourGraveMsg)

            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 20, 0.2, 0.2, 0.2, 0.05)
            world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f)
            player.playSound(player.location, Sound.ENTITY_GENERIC_HURT, 1f, 0.5f)

            player.closeInventory()
            return
        }
        for ((slot, item) in grave.items) {
            if (slot in 0..35) {
                player.addItemOrDrop(item)
            }
        }
        grave.armorContents["helmet"]?.let { player.equipSafely(player.inventory.helmet, it) { player.inventory.helmet = it } }
        grave.armorContents["chestplate"]?.let { player.equipSafely(player.inventory.chestplate, it) { player.inventory.chestplate = it } }
        grave.armorContents["leggings"]?.let { player.equipSafely(player.inventory.leggings, it) { player.inventory.leggings = it } }
        grave.armorContents["boots"]?.let { player.equipSafely(player.inventory.boots, it) { player.inventory.boots = it } }
        grave.armorContents["offhand"]?.let { player.equipSafely(player.inventory.itemInOffHand, it) { player.inventory.setItemInOffHand(it) } }

        if (grave.storedXp > 0) {
            player.giveExp(grave.storedXp)
        }

        val loc = grave.location.clone().add(0.5, 0.5, 0.5)
        val world = loc.world ?: return
        world.spawnParticle(Particle.SOUL, loc, 30, 0.3, 0.3, 0.3, 0.02)
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f)
        world.playSound(loc, Sound.BLOCK_SOUL_SAND_BREAK, 0.7f, 0.9f)
        player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)

        val successMsg = plugin.messageHandler.stringMessageToComponent(
            "graves",
            "collected",
            mapOf("player" to player.name)
        )
        player.sendMessage(successMsg)

        plugin.ghostManager.removeGhost(grave.ownerId)
        plugin.graveManager.removeGrave(grave)
    }
}
