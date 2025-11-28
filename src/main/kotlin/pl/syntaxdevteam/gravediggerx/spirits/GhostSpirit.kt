package pl.syntaxdevteam.gravediggerx.spirits

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Allay
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.util.UUID

class GhostSpirit(
    private val plugin: GraveDiggerX,
    val graveOwnerId: UUID,
    val graveLocation: Location,
    val ownerName: String
) {

    var entity: Entity? = null
    var isAlive: Boolean = true
    private var taskId: Int = -1

    fun spawn() {
        val world = graveLocation.world ?: return

        val exactLoc = Location(
            world,
            graveLocation.blockX + 0.5,
            graveLocation.blockY + 2.7,
            graveLocation.blockZ + 0.5
        )

        val ghost = world.spawn(exactLoc, Allay::class.java) { allay ->
            allay.isInvulnerable = true
            allay.isCollidable = false
            allay.setGravity(false)
            allay.setAI(false)
            allay.canPickupItems = false
            allay.setRemoveWhenFarAway(false)
            allay.persistentDataContainer.set(
                GHOST_KEY,
                PersistentDataType.STRING,
                graveOwnerId.toString()
            )

            allay.velocity = org.bukkit.util.Vector(0, 0, 0)
        }

        this.entity = ghost

        taskId = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, {
            if (!isAlive || entity == null || entity!!.isDead) {
                if (taskId >= 0) {
                    plugin.server.scheduler.cancelTask(taskId)
                    taskId = -1
                }
                return@scheduleSyncRepeatingTask
            }

            val w = entity!!.world

            val strictLoc = Location(
                w,
                graveLocation.blockX + 0.5,
                graveLocation.blockY + 2.7,
                graveLocation.blockZ + 0.5
            )

            entity!!.velocity = org.bukkit.util.Vector(0, 0, 0)
            entity!!.teleport(strictLoc)

            w.spawnParticle(org.bukkit.Particle.SOUL, strictLoc, 2, 0.2, 0.2, 0.2, 0.05)

            val allay = entity as? Allay ?: return@scheduleSyncRepeatingTask

            val closestPlayer = w.players
                .filter { it.world == w }
                .minByOrNull { it.location.distance(strictLoc) }

            if (closestPlayer != null && strictLoc.distance(closestPlayer.location) < 50.0) {
                val direction = closestPlayer.eyeLocation.clone()
                    .subtract(strictLoc).toVector().normalize()
                val lookLoc = strictLoc.clone().apply { setDirection(direction) }
                allay.teleport(lookLoc)
            }

        }, 1L, 1L)
    }

    fun despawn() {
        entity?.remove()
    }

    companion object {
        val GHOST_KEY: NamespacedKey = NamespacedKey.fromString("gravediggerx:ghost")!!
    }
}
