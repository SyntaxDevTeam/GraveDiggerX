package pl.syntaxdevteam.gravediggerx.graves

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import java.util.UUID

object GraveBackupSerializer {
    fun encodeBackups(backups: Collection<GraveBackup>): JsonArray {
        val array = JsonArray()
        backups.forEach { array.add(encodeBackup(it)) }
        return array
    }

    fun decodeBackupsFromString(content: String): List<GraveBackup> {
        if (content.isBlank()) return emptyList()
        return try {
            val json = JsonParser.parseString(content).asJsonArray
            json.mapNotNull { element ->
                runCatching { decodeBackup(element.asJsonObject) }.getOrNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeBackup(backup: GraveBackup): JsonObject {
        val backupJson = JsonObject()
        backupJson.addProperty("id", backup.id.toString())
        backupJson.addProperty("ownerId", backup.ownerId.toString())
        backupJson.addProperty("ownerName", backup.ownerName)
        backupJson.add("location", encodeLocation(backup.location))
        backupJson.add("items", encodeItems(backup.items))
        backupJson.add("armorContents", encodeArmor(backup.armorContents))
        backupJson.addProperty("storedXp", backup.storedXp)
        backupJson.addProperty("createdAt", backup.createdAt)
        backupJson.addProperty("backedUpAt", backup.backedUpAt)
        return backupJson
    }

    private fun decodeBackup(obj: JsonObject): GraveBackup {
        val location = decodeLocation(obj.getAsJsonObject("location"))
        val items = GraveSerializer.decodeItems(obj.getAsJsonArray("items"))
        val armorContents = GraveSerializer.decodeArmor(obj.getAsJsonObject("armorContents"))
        val storedXp = obj.get("storedXp")?.asInt ?: 0
        val createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis()
        val backedUpAt = obj.get("backedUpAt")?.asLong ?: createdAt

        return GraveBackup(
            id = obj.get("id")?.asString?.let(UUID::fromString) ?: UUID.randomUUID(),
            ownerId = UUID.fromString(obj.get("ownerId").asString),
            ownerName = obj.get("ownerName")?.asString ?: "Unknown",
            location = location,
            items = items,
            armorContents = armorContents,
            storedXp = storedXp,
            createdAt = createdAt,
            backedUpAt = backedUpAt
        )
    }

    private fun encodeLocation(location: Location): JsonObject {
        val locationJson = JsonObject()
        locationJson.addProperty("world", location.world?.name ?: "world")
        locationJson.addProperty("x", location.x)
        locationJson.addProperty("y", location.y)
        locationJson.addProperty("z", location.z)
        locationJson.addProperty("yaw", location.yaw)
        locationJson.addProperty("pitch", location.pitch)
        return locationJson
    }

    private fun encodeItems(items: Map<Int, ItemStack>): JsonArray {
        val itemsArray = JsonArray()
        items.forEach { (slot, itemStack) ->
            val itemJson = JsonObject()
            itemJson.addProperty("slot", slot)
            itemJson.add("item", GraveSerializer.serializeItemStack(itemStack))
            itemsArray.add(itemJson)
        }
        return itemsArray
    }

    private fun encodeArmor(armorContents: Map<String, ItemStack>): JsonObject {
        val armor = JsonObject()
        armorContents.forEach { (slot, itemStack) ->
            armor.add(slot, GraveSerializer.serializeItemStack(itemStack))
        }
        return armor
    }

    private fun decodeLocation(obj: JsonObject?): Location {
        val world = obj?.get("world")?.asString
            ?.let { Bukkit.getWorld(it) }
            ?: Bukkit.getWorlds().firstOrNull()
            ?: Bukkit.getWorld("world")
        val x = obj?.get("x")?.asDouble ?: 0.0
        val y = obj?.get("y")?.asDouble ?: 0.0
        val z = obj?.get("z")?.asDouble ?: 0.0
        val yaw = obj?.get("yaw")?.asFloat ?: 0f
        val pitch = obj?.get("pitch")?.asFloat ?: 0f
        return Location(world, x, y, z, yaw, pitch)
    }
}
