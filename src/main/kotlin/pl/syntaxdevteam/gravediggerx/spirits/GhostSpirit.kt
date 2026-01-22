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

        task = SchedulerProvider.runSyncRepeatingAt(plugin, graveLocation, 1L, 1L, Runnable {
            if (!isAlive || entity == null || entity!!.isDead) {
                task?.cancel()
                task = null
                return@Runnable
            }

            val w = entity!!.world

            val strictLoc = Location(
                w,
                graveLocation.blockX + 0.5,
                graveLocation.blockY + 2.7,
                graveLocation.blockZ + 0.5
            )

            entity!!.velocity = org.bukkit.util.Vector(0, 0, 0)
            teleportEntity(entity!!, strictLoc)

            w.spawnParticle(org.bukkit.Particle.SOUL, strictLoc, 2, 0.2, 0.2, 0.2, 0.05)

            val allay = entity as? Allay ?: return@Runnable

            val closestPlayer = w.players
                .filter { it.world == w }
                .minByOrNull { it.location.distance(strictLoc) }

            if (closestPlayer != null && strictLoc.distance(closestPlayer.location) < 50.0) {
                val direction = closestPlayer.eyeLocation.clone()
                    .subtract(strictLoc).toVector().normalize()
                val lookLoc = strictLoc.clone().apply { setDirection(direction) }
                teleportEntity(allay, lookLoc)
            }

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
