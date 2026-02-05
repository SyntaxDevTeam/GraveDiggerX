package pl.syntaxdevteam.gravediggerx.listeners

import org.bukkit.event.Event.Result
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.addItemOrDrop
import pl.syntaxdevteam.gravediggerx.common.equipSafely
import pl.syntaxdevteam.gravediggerx.gui.GraveGUI
import pl.syntaxdevteam.gravediggerx.graves.Grave
import java.util.UUID

@Suppress("NestedLambdaShadowedImplicitParameter")
class GraveClickListener(private val plugin: GraveDiggerX) : Listener {

    private val effectCooldowns = mutableMapOf<UUID, Long>()

    private data class GraveGuiEntry(val ownerId: UUID, val createdAt: Long, val gui: GraveGUI)

    private val graveGuiCache = mutableMapOf<String, GraveGuiEntry>()

    @EventHandler
    fun onGraveInteract(e: PlayerInteractEvent) {
        if (e.useInteractedBlock() == Result.DENY || e.useItemInHand() == Result.DENY) return

        val player = e.player

        if (e.action != Action.RIGHT_CLICK_BLOCK) return
        val block = e.clickedBlock ?: return

        val grave = plugin.graveManager.getGraveAt(block.location) ?: return
        e.setUseInteractedBlock(Result.DENY)
        e.setUseItemInHand(Result.DENY)

        if (grave.ownerId == player.uniqueId) {
            if (player.isSneaking) {
                collectGraveInstantly(player, grave)
                return
            }
            val graveId = grave.location.toBlockLocation().toString()
            val gui = getOrCreateGui(graveId, grave)
            gui.open(player)

            if (plugin.graveManager.getGraveAt(grave.location) == null) {
                graveGuiCache.remove(graveId)?.gui?.destroy()
            }
            return
        }

        if (grave.isPublic) {
            val graveId = grave.location.toBlockLocation().toString()
            val gui = getOrCreateGui(graveId, grave)
            gui.open(player)

            if (plugin.graveManager.getGraveAt(grave.location) == null) {
                graveGuiCache.remove(graveId)?.gui?.destroy()
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

            val notYourGraveMsg = plugin.messageHandler.stringMessageToComponent("graves", "not-your-grave", emptyMap())
            player.sendMessage(notYourGraveMsg)

            val direction = player.location.subtract(grave.location).toVector().normalize()
            player.velocity = direction.multiply(1.5)

            effectCooldowns[player.uniqueId] = now
            return
        }

        val graveExpiredMsg = plugin.messageHandler.stringMessageToComponent("graves", "not-your-grave", emptyMap())
        player.sendMessage(graveExpiredMsg)
    }


    private fun getOrCreateGui(graveId: String, grave: Grave): GraveGUI {
        val cached = graveGuiCache[graveId]
        if (cached != null && cached.ownerId == grave.ownerId && cached.createdAt == grave.createdAt) {
            return cached.gui
        }

        cached?.gui?.destroy()

        val gui = GraveGUI(grave, plugin)
        graveGuiCache[graveId] = GraveGuiEntry(grave.ownerId, grave.createdAt, gui)
        return gui
    }

    private fun collectGraveInstantly(player: org.bukkit.entity.Player, grave: Grave) {
        if (player.uniqueId != grave.ownerId && !grave.isPublic) return

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

        if (grave.storedXp > 0) player.giveExp(grave.storedXp)

        plugin.graveManager.removeGrave(grave)
        val world = player.world
        val loc = grave.location.clone().add(0.5, 0.5, 0.5)
        world.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f)
        world.spawnParticle(org.bukkit.Particle.SOUL, loc, 30, 0.3, 0.3, 0.3, 0.02)
        val msg = plugin.messageHandler.stringMessageToComponent("graves", "collected", mapOf("player" to player.name))
        player.sendMessage(msg)
    }
}
