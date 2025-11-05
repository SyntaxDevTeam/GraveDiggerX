package pl.syntaxdevteam.gravediggerx.graves

import com.google.gson.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.io.*
import java.util.*

class GraveDataStore(private val plugin: GraveDiggerX) {

    private val dataFile = File(plugin.dataFolder, "data.json")
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    init {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
    }

    fun saveAllGraves(graves: Collection<Grave>) {
        try {
            val jsonArray = JsonArray()
            
            for (grave in graves) {
                val graveJson = JsonObject()
                graveJson.addProperty("ownerId", grave.ownerId.toString())
                graveJson.addProperty("ownerName", grave.ownerName)
                val locationJson = JsonObject()
                locationJson.addProperty("world", grave.location.world?.name ?: "world")
                locationJson.addProperty("x", grave.location.x)
                locationJson.addProperty("y", grave.location.y)
                locationJson.addProperty("z", grave.location.z)
                locationJson.addProperty("yaw", grave.location.yaw)
                locationJson.addProperty("pitch", grave.location.pitch)
                graveJson.add("location", locationJson)

                val itemsArray = JsonArray()
                for ((slot, itemStack) in grave.items) {
                    val itemJson = JsonObject()
                    itemJson.addProperty("slot", slot)
                    itemJson.addProperty("data", serializeItemStack(itemStack))
                    itemsArray.add(itemJson)
                }
                graveJson.add("items", itemsArray)
                graveJson.addProperty("originalBlockData", grave.originalBlockData.asString)
                graveJson.addProperty("storedXp", grave.storedXp)
                graveJson.addProperty("createdAt", grave.createdAt)
                graveJson.addProperty("ghostEntityId", grave.ghostEntityId?.toString())
                graveJson.addProperty("ghostActive", grave.ghostActive)
                graveJson.addProperty("lastAttackerId", grave.lastAttackerId?.toString())
                graveJson.addProperty("itemsStolen", grave.itemsStolen)
                
                jsonArray.add(graveJson)
            }

            val jsonText = gson.toJson(jsonArray)
            dataFile.bufferedWriter().use { writer ->
                writer.write(jsonText)
                writer.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadAllGraves(): List<Grave> {
        return try {
            if (!dataFile.exists()) {
                return emptyList()
            }
            val content = dataFile.readText()
            if (content.isEmpty()) {
                return emptyList()
            }

            val jsonArray = JsonParser.parseString(content).asJsonArray
            val graves = mutableListOf<Grave>()

            for (element in jsonArray) {
                try {
                    val obj = element.asJsonObject
                    val locObj = obj.getAsJsonObject("location")
                    val world = Bukkit.getWorld(locObj.get("world").asString) ?: Bukkit.getWorld("world")
                    val location = Location(
                        world,
                        locObj.get("x").asDouble,
                        locObj.get("y").asDouble,
                        locObj.get("z").asDouble,
                        locObj.get("yaw").asFloat,
                        locObj.get("pitch").asFloat
                    )

                    val items = mutableMapOf<Int, ItemStack>()
                    val itemsArray = obj.getAsJsonArray("items")
                    for (itemElement in itemsArray) {
                        val itemObj = itemElement.asJsonObject
                        val slot = itemObj.get("slot").asInt
                        val itemStack = deserializeItemStack(itemObj.get("data").asString)
                        items[slot] = itemStack
                    }

                    val blockData = try {
                        Bukkit.createBlockData(obj.get("originalBlockData").asString)
                    } catch (e: Exception) {
                        Bukkit.createBlockData(Material.STONE)
                    }
                    
                    val grave = Grave(
                        ownerId = UUID.fromString(obj.get("ownerId").asString),
                        ownerName = obj.get("ownerName").asString,
                        location = location,
                        items = items,
                        hologramIds = emptyList(),
                        originalBlockData = blockData,
                        storedXp = obj.get("storedXp")?.asInt ?: 0,
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                        ghostEntityId = obj.get("ghostEntityId")?.takeIf { !it.isJsonNull }?.asString?.let { UUID.fromString(it) },
                        ghostActive = obj.get("ghostActive")?.asBoolean ?: false,
                        lastAttackerId = obj.get("lastAttackerId")?.takeIf { !it.isJsonNull }?.asString?.let { UUID.fromString(it) },
                        itemsStolen = obj.get("itemsStolen")?.asInt ?: 0
                    )
                    graves.add(grave)
                } catch (e: Exception) {
                }
            }
            graves
        } catch (e: Exception) {
            emptyList()
        }
    }
    private fun serializeItemStack(itemStack: ItemStack): String {
        return try {
            val outputStream = ByteArrayOutputStream()
            val dataOutput = BukkitObjectOutputStream(outputStream)
            dataOutput.writeObject(itemStack)
            dataOutput.close()
            Base64.getEncoder().encodeToString(outputStream.toByteArray())
        } catch (e: Exception) {
            Base64.getEncoder().encodeToString(ByteArray(0))
        }
    }

    private fun deserializeItemStack(data: String): ItemStack {
        return try {
            if (data.isEmpty()) return ItemStack(Material.AIR)
            val decoded = Base64.getDecoder().decode(data)
            val inputStream = ByteArrayInputStream(decoded)
            val dataInput = BukkitObjectInputStream(inputStream)
            val itemStack = dataInput.readObject() as ItemStack
            dataInput.close()
            itemStack
        } catch (e: Exception) {
            ItemStack(Material.AIR)
        }
    }
}