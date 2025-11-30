package pl.syntaxdevteam.gravediggerx.graves

import com.destroystokyo.paper.profile.PlayerProfile
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.block.Skull
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

    private fun notifyGraveRemoved(grave: Grave) {
        graveRemoveListeners[grave.ownerId]?.forEach { it.invoke() }
        graveRemoveListeners.remove(grave.ownerId)
    }

    fun loadGravesFromStorage() {
        // Clean up any leftover entities from previous sessions
        for (world in Bukkit.getWorlds()) {
            for (entity in world.entities) {
                if (entity is TextDisplay && entity.persistentDataContainer.has(
                        NamespacedKey(plugin, "grave_hologram"),
                        PersistentDataType.STRING
                    )
                ) {
                    entity.remove()
                }

                if (entity.persistentDataContainer.has(
                        NamespacedKey(plugin, "grave_ghost"),
                        PersistentDataType.STRING
                    )
                ) {
                    entity.remove()
                }
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val loadedGraves = plugin.databaseHandler.loadAllGraves()
            if (loadedGraves.isEmpty()) {
                return@Runnable
            }

            Bukkit.getScheduler().runTask(plugin, Runnable {
                for (grave in loadedGraves) {
                    val loc = grave.location
                    val world = loc.world ?: continue

                    world.getChunkAtAsync(loc).thenAccept {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            val block = loc.block
                            block.type = Material.PLAYER_HEAD

                            val skull = block.state as? Skull
                            skull?.apply {
                                this.persistentDataContainer.set(NamespacedKey(plugin, "grave"), PersistentDataType.STRING, grave.ownerId.toString())
                                applyPlayerProfile(this, grave.ownerId, grave.ownerName)
                                update(true, false)
                            }

                            val hologramIds = createHologram(loc, grave.ownerName)

                            var ghostId: UUID? = null
                            if (grave.ghostActive) {
                                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                                    val newGhostId = plugin.ghostManager.createGhostAndGetId(grave.ownerId, loc, grave.ownerName)
                                    if (newGhostId != null) {
                                        val active = activeGraves[getKey(loc)]
                                        if (active != null) {
                                            val updated = active.copy(
                                                ghostEntityId = newGhostId,
                                                ghostActive = true
                                            )
                                            activeGraves[getKey(loc)] = updated
                                        }
                                    }
                                }, 40L)
                            }

                            val updated = grave.copy(
                                hologramIds = hologramIds,
                                ghostEntityId = ghostId,
                                ghostActive = grave.ghostActive
                            )

                            // Remove old holograms if any
                            grave.hologramIds.forEach { oldId ->
                                Bukkit.getEntity(oldId)?.remove()
                            }

                            val blockLoc = loc.toBlockLocation()
                            activeGraves[getKey(blockLoc)] = updated
                            plugin.timeGraveRemove.scheduleRemoval(updated)
                        })
                    }
                }
            })
        })
    }

    fun saveGravesToStorage() {
        performSaveAsync()
    }

    fun saveOnDisable() {
        val snapshot = activeGraves.values.toList()
        plugin.databaseHandler.writeGravesToJsonIfConfigured(snapshot)
    }

    private fun performSaveAsync() {
        val snapshot = activeGraves.values.toList()
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            plugin.databaseHandler.writeGravesToJsonIfConfigured(snapshot)
        })
    }

    fun createGraveAndGetIt(player: org.bukkit.entity.Player, items: Map<Int, ItemStack>, xp: Int = 0): Grave? {
        val maxGraves = plugin.config.getInt("graves.max-per-player", 3)
        val playerGraves = activeGraves.values.count { it.ownerId == player.uniqueId }

        if (playerGraves >= maxGraves) {
            val message = plugin.messageHandler.stringMessageToComponent("graves", "reached-max-graves", mapOf("limit" to maxGraves.toString()))
            player.sendMessage(message)
            return null
        }

        var location = player.location.toBlockLocation()

        val safeEnabled = plugin.config.getBoolean("graves.safe-placement.enabled", true)
        if (safeEnabled) {
            val radius = plugin.config.getInt("graves.safe-placement.radius", 8)
            val maxVert = plugin.config.getInt("graves.safe-placement.max-vertical-scan", 3)
            SafeGravePlacer.findSafeLocationNear(location, radius, maxVert) { target, minDistance ->
                hasNearbyGrave(target, minDistance)
            }?.let { safeLoc ->
                if (safeLoc != location) {
                    runCatching { plugin.logger.debug("Relocating grave from ${location.blockX},${location.blockY},${location.blockZ} to ${safeLoc.blockX},${safeLoc.blockY},${safeLoc.blockZ}") }
                    location = safeLoc
                }
            }
        }

        val block = location.block
        val originalBlockData = if (block.type != Material.AIR) block.blockData.clone() else Bukkit.createBlockData(Material.AIR)
        block.type = Material.PLAYER_HEAD
        val skull = block.state as? Skull
        skull?.apply {
            this.persistentDataContainer.set(NamespacedKey(plugin, "grave"), PersistentDataType.STRING, player.uniqueId.toString())
            applyPlayerProfile(this, player.uniqueId, player.name)
            update(true, false)
        }

        val hologramIds = createHologram(location, player.name)
        val ghostEntityId = plugin.ghostManager.createGhostAndGetId(player.uniqueId, location, player.name)

        val armorContents = mapOf(
            "helmet" to ((player.inventory.helmet ?: ItemStack(Material.AIR)).clone()),
            "chestplate" to ((player.inventory.chestplate ?: ItemStack(Material.AIR)).clone()),
            "leggings" to ((player.inventory.leggings ?: ItemStack(Material.AIR)).clone()),
            "boots" to ((player.inventory.boots ?: ItemStack(Material.AIR)).clone()),
            "offhand" to ((player.inventory.itemInOffHand ?: ItemStack(Material.AIR)).clone())
        )

        val grave = Grave(
            ownerId = player.uniqueId,
            ownerName = player.name,
            location = location,
            items = items.toMutableMap(),
            armorContents = armorContents,
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

    fun removeAllGraves() {
        activeGraves.values.forEach { grave ->
            plugin.timeGraveRemove.cancelRemoval(grave)
        }
        activeGraves.values.forEach { grave ->
            val location = grave.location.toBlockLocation()
            val block = location.block
            if (block.state is Skull) {
                val state = block.state as Skull
                state.persistentDataContainer.remove(NamespacedKey(plugin, "grave"))
                state.update(true, false)
            }
            block.blockData = grave.originalBlockData
            grave.hologramIds.forEach { id ->
                Bukkit.getEntity(id)?.remove()
            }
            grave.ghostEntityId?.let { id ->
                Bukkit.getEntity(id)?.remove() }
            plugin.ghostManager.removeGhost(grave.ownerId)
            notifyGraveRemoved(grave)
        }
        activeGraves.clear()
        plugin.databaseHandler.writeGravesToJsonIfConfigured(emptyList())
    }

    fun updateHologramWithTime(grave: Grave, time: Int) {
        grave.hologramIds.forEach { id ->
            val entity = Bukkit.getEntity(id)
            if (entity is TextDisplay) {
                val text: Component = plugin.messageHandler.stringMessageToComponentNoPrefix(
                    "graveh",
                    "hologram",
                    mapOf("player" to grave.ownerName, "time" to time.toString())
                )
                entity.text(text)
            }
        }
    }

    fun getGravesFor(ownerId: UUID): List<Grave> =
        activeGraves.values.filter { it.ownerId == ownerId }

    private fun createHologram(location: Location, ownerName: String): List<UUID> {
        val text: Component = plugin.messageHandler.stringMessageToComponentNoPrefix("graveh", "hologram", mapOf("player" to ownerName))
        val hologramLocation = location.clone().add(0.5, 1.5, 0.5)
        val world = hologramLocation.world ?: return emptyList()

        val textDisplay = world.spawn(hologramLocation, TextDisplay::class.java) { display ->
            display.text(text)
            display.billboard = Display.Billboard.CENTER
            display.isShadowed = false
            display.textOpacity = 255.toByte()
            display.backgroundColor = Color.fromARGB(120, 10, 10, 10)
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
        return activeGraves[getKey(location)]
    }

    fun removeGrave(grave: Grave) {
        plugin.timeGraveRemove.cancelRemoval(grave)

        val location = grave.location.toBlockLocation()
        val block = location.block
        if (block.state is Skull) {
            val state = block.state as Skull
            state.persistentDataContainer.remove(NamespacedKey(plugin, "grave"))
            state.update(true, false)
        }
        block.blockData = grave.originalBlockData

        grave.hologramIds.forEach { Bukkit.getEntity(it)?.remove() }
        grave.ghostEntityId?.let { Bukkit.getEntity(it)?.remove() }

        plugin.ghostManager.removeGhost(grave.ownerId)
        activeGraves.remove(getKey(location))
        notifyGraveRemoved(grave)
        saveGravesToStorage()
    }

    fun removeGraveAt(location: Location): Boolean {
        val grave = getGraveAt(location)
        if (grave != null) {
            removeGrave(grave)
            return true
        }

        // If grave is not in activeGraves, try to remove it manually
        val block = location.block
        if (block.type == Material.PLAYER_HEAD) {
            // Find and remove hologram
            location.world.getNearbyEntities(location.clone().add(0.5, 1.5, 0.5), 1.0, 1.0, 1.0).forEach { entity ->
                if (entity is TextDisplay && entity.persistentDataContainer.has(NamespacedKey(plugin, "grave_hologram"), PersistentDataType.STRING)) {
                    entity.remove()
                }
            }
            block.type = Material.AIR
            // Manually trigger a save, as the grave might not be in the active list
            saveGravesToStorage()
            return true
        }
        return false
    }


    private fun getKey(location: Location): String {
        val worldName = location.world?.name ?: "unknown"
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        return "$worldName:$x:$y:$z"
    }

    private fun applyPlayerProfile(
        skull: Skull,
        ownerId: UUID,
        ownerName: String?
    ) {
        val profile = resolvePlayerProfile(ownerId, ownerName) ?: return
        if (ModernProfileSupport.trySetProfile(skull, profile)) {
            return
        }

        @Suppress("DEPRECATION")
        skull.setOwningPlayer(Bukkit.getOfflinePlayer(ownerId))
    }

    private object ModernProfileSupport {
        private val resolvableProfileClass: Class<*>? = try {
            Class.forName("io.papermc.paper.datacomponent.item.ResolvableProfile")
        } catch (_: ClassNotFoundException) {
            null
        }

        private val resolvableProfileFactory = resolvableProfileClass?.let {
            runCatching { it.getMethod("resolvableProfile", PlayerProfile::class.java) }.getOrNull()
        }

        private val skullSetProfileMethod = resolvableProfileClass?.let {
            runCatching { Skull::class.java.getMethod("setProfile", it) }.getOrNull()
        }

        fun trySetProfile(skull: Skull, profile: PlayerProfile): Boolean {
            val factory = resolvableProfileFactory ?: return false
            val setter = skullSetProfileMethod ?: return false
            return try {
                val resolvable = factory.invoke(null, profile)
                setter.invoke(skull, resolvable)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun resolvePlayerProfile(ownerId: UUID, ownerName: String?): PlayerProfile? {
        return try {
            Bukkit.createProfile(ownerId, ownerName).apply {
                complete()
            }
        } catch (ex: Exception) {
            plugin.logger.warning("Failed to resolve player profile for grave owner $ownerName: ${ex.message}")
            null
        }
    }

    private fun hasNearbyGrave(target: Location, minDistanceBlocks: Int): Boolean {
        val worldName = target.world?.name ?: return false
        val tx = target.blockX
        val ty = target.blockY
        val tz = target.blockZ
        val minDistSq = (minDistanceBlocks * minDistanceBlocks).toDouble()
        return activeGraves.values.any { g ->
            val loc = g.location
            if (loc.world?.name != worldName) return@any false
            val dx = (loc.blockX - tx).toDouble()
            val dy = (loc.blockY - ty).toDouble()
            val dz = (loc.blockZ - tz).toDouble()
            (dx * dx + dy * dy + dz * dz) < minDistSq
        }
    }
}