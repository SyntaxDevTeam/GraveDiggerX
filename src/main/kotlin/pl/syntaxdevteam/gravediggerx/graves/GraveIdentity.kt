package pl.syntaxdevteam.gravediggerx.graves

import org.bukkit.Location
import java.util.UUID

object GraveIdentity {
    fun locationKey(location: Location): String {
        val blockLocation = location.toBlockLocation()
        val worldName = blockLocation.world?.name ?: "unknown"
        return locationKey(worldName, blockLocation.blockX, blockLocation.blockY, blockLocation.blockZ)
    }

    fun locationKey(worldName: String, x: Int, y: Int, z: Int): String = "$worldName:$x:$y:$z"

    fun taskKey(grave: Grave): String = "${grave.graveId}:${grave.createdAt}"

    fun collectionClaimKey(grave: Grave): String = grave.graveId.toString()

    fun graveIdOrRandom(id: String?): UUID = runCatching { UUID.fromString(id) }.getOrElse { UUID.randomUUID() }
}
