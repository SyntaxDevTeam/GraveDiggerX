package pl.syntaxdevteam.gravediggerx.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import net.kyori.adventure.text.Component
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
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker

class GraveGUI(
    private val grave: Grave,
    private val plugin: GraveDiggerX
) : Listener {

    private val guiPlaceholders: Map<String, String> = mapOf(
        "player" to grave.ownerName.ifBlank {
            plugin.messageHandler.stringMessageToStringNoPrefix("error", "unknown-player", emptyMap())
        },
        "xp" to grave.storedXp.toString()
    )

    private val inventory: Inventory = Bukkit.createInventory(
        null,
        54,
        plugin.messageHandler.stringMessageToComponentNoPrefix("gui-grave", "title", guiPlaceholders)
    )

    init {
        setupInventory()
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
        meta.displayName(plugin.messageHandler.stringMessageToComponentNoPrefix("gui-grave", "stats-owner", guiPlaceholders))
        banner.itemMeta = meta
        return banner
    }

    private fun createXpBanner(): ItemStack {
        val banner = ItemStack(Material.CYAN_BANNER)
        val meta = banner.itemMeta
        meta.displayName(plugin.messageHandler.stringMessageToComponentNoPrefix("gui-grave", "stats-xp", guiPlaceholders))
        banner.itemMeta = meta
        return banner
    }

    private fun createCollectButton(): ItemStack {
        val item = ItemStack(Material.LIME_CANDLE)
        val meta = item.itemMeta
        meta.displayName(plugin.messageHandler.stringMessageToComponentNoPrefix("gui-grave", "collect-item-name", guiPlaceholders))
        meta.lore(plugin.messageHandler.getSmartMessage("gui-grave", "collect-item-lore", guiPlaceholders))
        item.itemMeta = meta
        return item
    }

    fun open(player: Player) {
        if (!PermissionChecker.has(player, PermissionChecker.PermissionKey.OPEN_GRAVE)) {
            val msg = plugin.messageHandler.stringMessageToComponent("error", "no-permission", emptyMap())
            player.sendMessage(msg)
            return
        }

        Bukkit.getPluginManager().registerEvents(this, plugin)
        player.openInventory(inventory)
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
    }


    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.view.topInventory != inventory) return

        event.isCancelled = true

        if (event.rawSlot == 53) {
            collectAll(player)
            player.closeInventory()
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory != inventory) return
        HandlerList.unregisterAll(this)
    }

    private fun collectAll(player: Player) {
        val liveGrave = plugin.graveManager.getGraveAt(grave.location) ?: grave
        if (player.uniqueId != liveGrave.ownerId && !liveGrave.isPublic) {
            val loc = liveGrave.location.clone().add(0.5, 0.5, 0.5)
            val world = loc.world ?: return

            val notYourGraveMsg = plugin.messageHandler.stringMessageToComponent("graves", "not-your-grave", emptyMap())
            player.sendMessage(notYourGraveMsg)

            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 20, 0.2, 0.2, 0.2, 0.05)
            world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f)
            player.playSound(player.location, Sound.ENTITY_GENERIC_HURT, 1f, 0.5f)

            player.closeInventory()
            return
        }
        val ticket = plugin.graveManager.beginCollection(liveGrave, player.uniqueId)
        if (ticket == null) {
            val alreadyCollectedMsg = plugin.messageHandler.stringMessageToComponent("graves", "already-collected", emptyMap())
            player.sendMessage(alreadyCollectedMsg)
            return
        }
        if (!plugin.graveManager.markCollecting(liveGrave, ticket)) {
            plugin.graveManager.releaseCollectionLock(liveGrave)
            val alreadyCollectedMsg = plugin.messageHandler.stringMessageToComponent("graves", "already-collected", emptyMap())
            player.sendMessage(alreadyCollectedMsg)
            return
        }

        try {
            for ((slot, item) in liveGrave.items) {
                if (slot in 0..35) {
                    player.addItemOrDrop(item)
                }
            }

            liveGrave.armorContents["helmet"]?.let {
                if (it.type != Material.AIR) {
                    val current = player.inventory.helmet
                    if (current.type == Material.AIR) {
                        player.inventory.setHelmet(it)
                    } else {
                        player.addItemOrDrop(it)
                    }
                }
            }
            liveGrave.armorContents["chestplate"]?.let {
                if (it.type != Material.AIR) {
                    val current = player.inventory.chestplate
                    if (current.type == Material.AIR) {
                        player.inventory.setChestplate(it)
                    } else {
                        player.addItemOrDrop(it)
                    }
                }
            }
            liveGrave.armorContents["leggings"]?.let {
                if (it.type != Material.AIR) {
                    val current = player.inventory.leggings
                    if (current.type == Material.AIR) {
                        player.inventory.setLeggings(it)
                    } else {
                        player.addItemOrDrop(it)
                    }
                }
            }
            liveGrave.armorContents["boots"]?.let {
                if (it.type != Material.AIR) {
                    val current = player.inventory.boots
                    if (current.type == Material.AIR) {
                        player.inventory.setBoots(it)
                    } else {
                        player.addItemOrDrop(it)
                    }
                }
            }
            liveGrave.armorContents["offhand"]?.let {
                if (it.type != Material.AIR) {
                    val current = player.inventory.itemInOffHand
                    if (current.type == Material.AIR) player.inventory.setItemInOffHand(it) else player.addItemOrDrop(it)
                }
            }

            if (liveGrave.storedXp > 0) {
                player.giveExp(liveGrave.storedXp)
            }

            val loc = liveGrave.location.clone().add(0.5, 0.5, 0.5)
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

            plugin.ghostManager.removeGhost(liveGrave.location)
            val markedCollected = plugin.graveManager.markCollected(liveGrave, ticket)
            if (!markedCollected) {
                player.sendMessage(plugin.messageHandler.stringMessageToComponent("graves", "collection-tx-save-failed"))
                return
            }
            plugin.graveManager.removeGrave(liveGrave)
        } catch (ex: Exception) {
            plugin.graveManager.markCollectionFailed(liveGrave, ticket, ex.message)
            throw ex
        } finally {
            plugin.graveManager.releaseCollectionLock(liveGrave)
        }
    }
}
