package pl.syntaxdevteam.gravediggerx.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.gui.GraveGUI
import pl.syntaxdevteam.gravediggerx.graves.Grave
import java.util.UUID

class GraveClickListener(private val plugin: GraveDiggerX) : Listener {

    private val effectCooldowns = mutableMapOf<UUID, Long>()

    private val graveGuiCache = mutableMapOf<String, GraveGUI>()

    @EventHandler
    fun onGraveInteract(e: PlayerInteractEvent) {
        if (e.isCancelled) return

        val player = e.player

        if (e.action != Action.RIGHT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return

        val grave = plugin.graveManager.getGraveAt(block.location) ?: return
        e.isCancelled = true

        if (grave.ownerId == player.uniqueId) {
            if (player.isSneaking) {
                collectGraveInstantly(player, grave)
                return
            }
            val graveId = grave.location.toString()
            val gui = graveGuiCache.getOrPut(graveId) {
                GraveGUI(grave, plugin)
            }
            gui.open(player)

            if (plugin.graveManager.getGraveAt(grave.location) == null) {
                graveGuiCache.remove(graveId)
            }
            return
        }

        val graveAgeSeconds = (System.currentTimeMillis() - grave.createdAt) / 1000
        val graveExpireSeconds = plugin.config.getInt("graves.grave-despawn", 120)

        if (graveAgeSeconds < graveExpireSeconds) {
            val now = System.currentTimeMillis()
            val lastEffectTime = effectCooldowns[player.uniqueId] ?: 0
            val effectCooldownMs = plugin.config.getLong("graves.protected-effect-cooldown", 5000L)

            if (now - lastEffectTime < effectCooldownMs) {
                return
            }

            val loc = grave.location.clone().add(0.5, 0.5, 0.5)
            val world = loc.world ?: return

            world.playSound(loc, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f)

            val notYourGraveMsg = plugin.messageHandler.getMessage("graves", "not-your-grave", emptyMap())
            player.sendMessage(notYourGraveMsg)

            val direction = player.location.subtract(grave.location).toVector().normalize()
            player.velocity = direction.multiply(1.5)

            effectCooldowns[player.uniqueId] = now
            return
        }

        val graveExpiredMsg = plugin.messageHandler.getMessage("graves", "not-your-grave", emptyMap())
        player.sendMessage(graveExpiredMsg)
    }

    private fun collectGraveInstantly(player: org.bukkit.entity.Player, grave: Grave) {
        if (player.uniqueId != grave.ownerId) return

        for ((slot, item) in grave.items) {
            when (slot) {
                36 -> player.inventory.helmet = item
                37 -> player.inventory.chestplate = item
                38 -> player.inventory.leggings = item
                39 -> player.inventory.boots = item
                40 -> player.inventory.setItemInOffHand(item)
                else -> if (slot in 0..35) player.inventory.addItem(item)
            }
        }

        if (grave.storedXp > 0) player.giveExp(grave.storedXp)

        plugin.graveManager.removeGrave(grave)
        val world = player.world
        val loc = grave.location.clone().add(0.5, 0.5, 0.5)
        world.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f)
        world.spawnParticle(org.bukkit.Particle.SOUL, loc, 30, 0.3, 0.3, 0.3, 0.02)
        val msg = plugin.messageHandler.getMessage("graves", "collected", mapOf("player" to player.name))
        player.sendMessage(msg)
    }
}