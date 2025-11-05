package pl.syntaxdevteam.gravediggerx.graves

import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.joml.Vector3f
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class GraveManager(private val plugin: GraveDiggerX) {
    private val activeGraves = ConcurrentHashMap<String, Grave>()
    private val graveRemoveListeners = ConcurrentHashMap<UUID, MutableList<() -> Unit>>()
    private val dataStore = GraveDataStore(plugin)

    private fun notifyGraveRemoved(grave: Grave) {
        graveRemoveListeners[grave.ownerId]?.forEach { it.invoke() }
        graveRemoveListeners.remove(grave.ownerId)
    }

    fun loadGravesFromStorage() {
        val loadedGraves = dataStore.loadAllGraves()
        if (loadedGraves.isEmpty()) return

        var restored = 0
        var skipped = 0

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            for (grave in loadedGraves) {
                val loc = grave.location
                val world = loc.world ?: run {
                    skipped++
                    return@Runnable
                }

                world.getChunkAtAsync(loc).thenAccept {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        val block = loc.block
                        block.type = Material.PLAYER_HEAD
                        val skull = block.state as? org.bukkit.block.Skull
                        if (skull != null) {
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                val profile = Bukkit.createProfile(grave.ownerId, grave.ownerName)
                                profile.complete(true)
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    skull.setPlayerProfile(profile)
                                    skull.update(true, false)
                                })
                            })
                        }
                        val hologramIds = createHologram(loc, grave.ownerName)
                        val ghostId = if (grave.ghostActive)
                            plugin.ghostManager.createGhostAndGetId(grave.ownerId, loc, grave.ownerName)
                        else null

                        val updated = grave.copy(
                            hologramIds = hologramIds,
                            ghostEntityId = ghostId,
                            ghostActive = grave.ghostActive
                        )

                        activeGraves[getKey(loc)] = updated
                        plugin.timeGraveRemove.scheduleRemoval(updated)
                        restored++
                    })
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            }, 100L)
        }, 40L)
    }

    fun saveGravesToStorage() {
        val validGraves = activeGraves.values.toList()
        dataStore.saveAllGraves(validGraves)
    }

    fun createGraveAndGetIt(player: org.bukkit.entity.Player, items: Map<Int, ItemStack>, xp: Int = 0): Grave? {
        val maxGraves = plugin.config.getInt("graves.max-per-player", 3)
        val playerGraves = activeGraves.values.count { it.ownerId == player.uniqueId }

        if (playerGraves >= maxGraves) {
            val message = plugin.messageHandler.getMessage("graves", "reached-max-graves", mapOf("limit" to maxGraves.toString()))
            player.sendMessage(message)
            return null
        }

        val location = player.location.toBlockLocation()
        val block = location.block
        val originalBlockData = if (block.type != Material.AIR) block.blockData.clone() else Bukkit.createBlockData(Material.AIR)
        block.type = Material.PLAYER_HEAD
        val skull = block.state as? org.bukkit.block.Skull
        skull?.apply {
            val offline = Bukkit.getOfflinePlayer(player.uniqueId)
            setOwningPlayer(offline)
            update()
        }

        val hologramIds = createHologram(location, player.name)
        val ghostEntityId = plugin.ghostManager.createGhostAndGetId(player.uniqueId, location, player.name)

        val grave = Grave(
            ownerId = player.uniqueId,
            ownerName = player.name,
            location = location,
            items = items.toMutableMap(),
            hologramIds = hologramIds,
            originalBlockData = originalBlockData,
            storedXp = xp,
            createdAt = System.currentTimeMillis(),
            ghostActive = ghostEntityId != null,
            ghostEntityId = ghostEntityId,
            itemsStolen = 0,
            lastAttackerId = null
        )

        activeGraves[getKey(location)] = grave
        plugin.timeGraveRemove.scheduleRemoval(grave)
        saveGravesToStorage()
        return grave
    }

    fun getGravesFor(ownerId: UUID): List<Grave> =
        activeGraves.values.filter { it.ownerId == ownerId }

    private fun createHologram(location: Location, ownerName: String): List<UUID> {
        val text: Component = plugin.messageHandler.getLogMessage("graveh", "hologram", mapOf("player" to ownerName))
        val hologramLocation = location.clone().add(0.5, 1.5, 0.5)
        val world = hologramLocation.world ?: return emptyList()

        val textDisplay = world.spawn(hologramLocation, TextDisplay::class.java) { display ->
            display.text(text)
            display.billboard = Display.Billboard.CENTER
            display.isShadowed = false
            display.textOpacity = 255.toByte()
            display.backgroundColor = Color.fromARGB(180, 10, 10, 10)
            display.brightness = Display.Brightness(15, 15)
            display.isSeeThrough = true

            val transform = display.transformation
            transform.scale.set(Vector3f(1.25f, 1.25f, 1.25f))
            display.transformation = transform

            display.persistentDataContainer.set(
                NamespacedKey(plugin, "grave_hologram"),
                PersistentDataType.STRING,
                ownerName
            )
        }

        return listOf(textDisplay.uniqueId)
    }

    fun getGraveAt(location: Location): Grave? {
        val worldName = location.world?.name ?: return null
        val (x, y, z) = listOf(location.blockX, location.blockY, location.blockZ)
        return activeGraves.values.firstOrNull {
            val loc = it.location
            loc.world?.name == worldName && loc.blockX == x && loc.blockY == y && loc.blockZ == z
        }
    }


    fun removeGrave(grave: Grave) {
        plugin.timeGraveRemove.cancelRemoval(grave)

        val location = grave.location.toBlockLocation()
        val block = location.block
        block.blockData = grave.originalBlockData

        grave.hologramIds.forEach { Bukkit.getEntity(it)?.remove() }
        grave.ghostEntityId?.let { Bukkit.getEntity(it)?.remove() }

        plugin.ghostManager.removeGhost(grave.ownerId)
        activeGraves.remove(getKey(location))
        notifyGraveRemoved(grave)
        saveGravesToStorage()
    }

    private fun getKey(location: Location): String {
        val worldName = location.world?.name ?: "unknown"
        return "$worldName:${location.blockX}:${location.blockY}:${location.blockZ}"
    }

    fun removeAllGraves() {
        val keys = activeGraves.keys.toList()
        for (key in keys) {
            activeGraves[key]?.let { removeGrave(it) }
        }
    }
}
