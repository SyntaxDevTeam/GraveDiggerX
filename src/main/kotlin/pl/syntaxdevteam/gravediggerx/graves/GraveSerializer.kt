package pl.syntaxdevteam.gravediggerx.graves

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.util.Base64
import java.util.UUID

/**
 * Shared JSON serializer/deserializer for [Grave] that can be used by
 * both file-based and SQL-backed storage implementations.
 */
object GraveSerializer {
    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    fun encodeGraves(graves: Collection<Grave>): JsonArray {
        val array = JsonArray()
        graves.forEach { array.add(encodeGrave(it)) }
        return array
    }

    fun encodeGraveToString(grave: Grave): String = gson.toJson(encodeGrave(grave))

    fun decodeGravesFromString(content: String): List<Grave> {
        if (content.isBlank()) return emptyList()
        return try {
            val json = JsonParser.parseString(content).asJsonArray
            json.mapNotNull { element ->
                runCatching { decodeGrave(element.asJsonObject) }.getOrNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun decodeGraveFromString(payload: String): Grave? {
        if (payload.isBlank()) return null
        return try {
            val obj = JsonParser.parseString(payload).asJsonObject
            decodeGrave(obj)
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeGrave(grave: Grave): JsonObject {
        val graveJson = JsonObject()
        graveJson.addProperty("ownerId", grave.ownerId.toString())
        graveJson.addProperty("ownerName", grave.ownerName)
        graveJson.add("location", encodeLocation(grave.location))
        graveJson.add("items", encodeItems(grave.items))
        graveJson.add("armorContents", encodeArmor(grave.armorContents))
        graveJson.addProperty("originalBlockData", grave.originalBlockData.asString)
        graveJson.addProperty("storedXp", grave.storedXp)
        graveJson.addProperty("createdAt", grave.createdAt)
        graveJson.addProperty("ghostEntityId", grave.ghostEntityId?.toString())
        graveJson.addProperty("ghostActive", grave.ghostActive)
        graveJson.addProperty("lastAttackerId", grave.lastAttackerId?.toString())
        graveJson.addProperty("itemsStolen", grave.itemsStolen)
        return graveJson
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
            itemJson.add("item", serializeItemStack(itemStack))
            itemsArray.add(itemJson)
        }
        return itemsArray
    }

    private fun encodeArmor(armorContents: Map<String, ItemStack>): JsonObject {
        val armor = JsonObject()
        armorContents.forEach { (slot, itemStack) ->
            armor.add(slot, serializeItemStack(itemStack))
        }
        return armor
    }

    private fun decodeGrave(obj: JsonObject): Grave {
        val location = decodeLocation(obj.getAsJsonObject("location"))
        val items = decodeItems(obj.getAsJsonArray("items"))
        val armorContents = decodeArmor(obj.getAsJsonObject("armorContents"))
        val blockData = try {
            Bukkit.createBlockData(obj.get("originalBlockData").asString)
        } catch (_: Exception) {
            Bukkit.createBlockData(Material.STONE)
        }

        return Grave(
            ownerId = UUID.fromString(obj.get("ownerId").asString),
            ownerName = obj.get("ownerName")?.asString ?: "Unknown",
            location = location,
            items = items,
            armorContents = armorContents,
            hologramIds = emptyList(),
            originalBlockData = blockData,
            storedXp = obj.get("storedXp")?.asInt ?: 0,
            createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis(),
            ghostEntityId = obj.get("ghostEntityId")?.takeIf { !it.isJsonNull }?.asString?.let(UUID::fromString),
            ghostActive = obj.get("ghostActive")?.asBoolean ?: false,
            lastAttackerId = obj.get("lastAttackerId")?.takeIf { !it.isJsonNull }?.asString?.let(UUID::fromString),
            itemsStolen = obj.get("itemsStolen")?.asInt ?: 0
        )
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

    private fun decodeItems(array: JsonArray?): MutableMap<Int, ItemStack> {
        if (array == null) return mutableMapOf()
        val items = mutableMapOf<Int, ItemStack>()
        array.forEach { element ->
            val itemObj = element.asJsonObject
            val slot = itemObj.get("slot").asInt
            val itemStack = when {
                itemObj.has("item") && itemObj.get("item").isJsonObject ->
                    deserializeItemFromJson(itemObj.getAsJsonObject("item"))
                itemObj.has("data") && itemObj.get("data").isJsonPrimitive ->
                    deserializeItemLegacyBase64(itemObj.get("data").asString)
                else -> ItemStack(Material.AIR)
            }
            items[slot] = itemStack
        }
        return items
    }

    private fun decodeArmor(obj: JsonObject?): Map<String, ItemStack> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<String, ItemStack>()
        obj.entrySet().forEach { (slot, element) ->
            val stack = runCatching { deserializeItemFromJson(element.asJsonObject) }
                .getOrDefault(ItemStack(Material.AIR))
            map[slot] = stack
        }
        return map
    }

    private fun serializeItemStack(itemStack: ItemStack): JsonObject {
        return try {
            val map: Map<String, Any> = itemStack.serialize()
            gson.toJsonTree(map).asJsonObject
        } catch (_: Exception) {
            JsonObject()
        }
    }

    private fun deserializeItemFromJson(json: JsonObject): ItemStack {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(json, type)
            ItemStack.deserialize(map)
        } catch (_: Exception) {
            ItemStack(Material.AIR)
        }
    }

    @Suppress("DEPRECATION")
    private fun deserializeItemLegacyBase64(data: String): ItemStack {
        return try {
            if (data.isEmpty()) return ItemStack(Material.AIR)
            val decoded = Base64.getDecoder().decode(data)
            val inputStream = ByteArrayInputStream(decoded)
            val cls = Class.forName("org.bukkit.util.io.BukkitObjectInputStream")
            val ctor = cls.getConstructor(InputStream::class.java)
            val dataInput = ctor.newInstance(inputStream) as ObjectInputStream
            val itemStack = dataInput.readObject() as? ItemStack
            dataInput.close()
            itemStack ?: ItemStack(Material.AIR)
        } catch (_: Exception) {
            ItemStack(Material.AIR)
        }
    }
}
