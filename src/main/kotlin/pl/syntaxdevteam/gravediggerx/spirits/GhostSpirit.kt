package pl.syntaxdevteam.gravediggerx.spirits

import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Allay
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.core.platform.ServerEnvironment
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.CancellableTask
import pl.syntaxdevteam.gravediggerx.common.SchedulerProvider
import java.util.UUID

class GhostSpirit(
    private val plugin: GraveDiggerX,
    val graveOwnerId: UUID,
    val graveLocation: Location
) {

    var entity: Entity? = null
    var isAlive: Boolean = true
    private var task: CancellableTask? = null

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
            allay.removeWhenFarAway = false
            allay.persistentDataContainer.set(
                key(plugin),
                PersistentDataType.STRING,
                graveOwnerId.toString()
            )

            allay.velocity = org.bukkit.util.Vector(0, 0, 0)
        }

        this.entity = ghost

        task = SchedulerProvider.runSyncRepeatingAt(plugin, graveLocation, 5L, 5L, Runnable {
            val activeEntity = entity
            if (!isAlive || activeEntity == null || activeEntity.isDead) {
                task?.cancel()
                task = null
                return@Runnable
            }

            val w = activeEntity.world

            val strictLoc = Location(
                w,
                graveLocation.blockX + 0.5,
                graveLocation.blockY + 2.7,
                graveLocation.blockZ + 0.5
            )

            activeEntity.velocity = org.bukkit.util.Vector(0, 0, 0)
            w.spawnParticle(org.bukkit.Particle.SOUL, strictLoc, 3, 0.2, 0.2, 0.2, 0.05)

            val maxDistSq = 25.0 * 25.0
            val nearbyPlayers = runCatching {
                w.getNearbyPlayers(strictLoc, 25.0)
            }.getOrElse {
                w.players.filter { it.world == w && it.location.distanceSquared(strictLoc) <= maxDistSq }
            }

            val closestPlayer = nearbyPlayers.minByOrNull { it.location.distanceSquared(strictLoc) }

            val targetLoc = strictLoc.clone()
            if (closestPlayer != null) {
                val direction = closestPlayer.eyeLocation.clone()
                    .subtract(strictLoc).toVector().normalize()
                targetLoc.setDirection(direction)
            }

            teleportEntity(activeEntity, targetLoc)
        })
    }

    private fun teleportEntity(entity: Entity, location: Location) {
        if (ServerEnvironment.isFoliaBased()) {
            entity.teleportAsync(location)
            return
        }
        val teleportAsync = entity.javaClass.methods.firstOrNull {
            it.name == "teleportAsync" && it.parameterCount == 1 && it.parameterTypes[0] == Location::class.java
        }
        if (teleportAsync != null) {
            teleportAsync.invoke(entity, location)
            return
        }
        entity.teleport(location)
    }

    fun despawn() {
        task?.cancel()
        task = null
        entity?.remove()
    }

    companion object {
        const val KEY_NAME = "grave_ghost"

        fun key(plugin: GraveDiggerX): NamespacedKey = NamespacedKey(plugin, KEY_NAME)

        fun legacyKey(): NamespacedKey = NamespacedKey.fromString(KEY_NAME)!!
    }
}
