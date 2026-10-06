package pl.syntaxdevteam.gravediggerx.graves.registry

import org.bukkit.Location
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.graves.GraveIdentity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GraveRegistry {
    private val graves = ConcurrentHashMap<String, Grave>()

    fun add(grave: Grave) {
        graves[GraveIdentity.locationKey(grave.location.toBlockLocation())] = grave
    }

    fun remove(location: Location): Grave? = graves.remove(GraveIdentity.locationKey(location.toBlockLocation()))

    fun findAt(location: Location): Grave? = graves[GraveIdentity.locationKey(location.toBlockLocation())]

    fun findByOwner(ownerId: UUID): List<Grave> = graves.values.filter { it.ownerId == ownerId }

    fun findById(graveId: UUID): Grave? = graves.values.firstOrNull { it.graveId == graveId }

    fun snapshot(): List<Grave> = graves.values.toList()

    fun clear() = graves.clear()

    val size: Int get() = graves.size

    fun hasGraveNear(target: Location, minDistanceBlocks: Int): Boolean {
        val worldName = target.world?.name ?: return false
        val minDistSq = (minDistanceBlocks * minDistanceBlocks).toDouble()
        return graves.values.any { grave ->
            val location = grave.location
            if (location.world?.name != worldName) return@any false
            val dx = (location.blockX - target.blockX).toDouble()
            val dy = (location.blockY - target.blockY).toDouble()
            val dz = (location.blockZ - target.blockZ).toDouble()
            dx * dx + dy * dy + dz * dz < minDistSq
        }
    }
}
