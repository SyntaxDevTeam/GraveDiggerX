package pl.syntaxdevteam.gravediggerx.graves

import com.destroystokyo.paper.profile.PlayerProfile
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.block.Skull
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import org.joml.Vector3f
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.SchedulerProvider
import pl.syntaxdevteam.gravediggerx.integrations.RegionOwnershipChecker
import pl.syntaxdevteam.gravediggerx.spirits.GhostSpirit
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class GraveManager(private val plugin: GraveDiggerX) {
    private val activeGraves = ConcurrentHashMap<String, Grave>()
    private val collectionLocks = ConcurrentHashMap.newKeySet<String>()
    private val graveRemoveListeners = ConcurrentHashMap<UUID, MutableList<() -> Unit>>()
    private val backupStore = GraveBackupStore(plugin)
    private val regionOwnershipChecker = RegionOwnershipChecker.create(plugin)
    private val graveBackups = Collections.synchronizedList(backupStore.loadAllBackups().toMutableList())
    @Volatile
    private var cleanupTask: BukkitTask? = null

    enum class BackupRestoreResult {
        SUCCESS,
        LOCATION_OCCUPIED,
        WORLD_MISSING,
        FAILED
    }

    private fun notifyGraveRemoved(grave: Grave) {
        graveRemoveListeners[grave.ownerId]?.forEach { it.invoke() }
        graveRemoveListeners.remove(grave.ownerId)
    }

    fun loadGravesFromStorage() {
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
                        GhostSpirit.key(plugin),
                        PersistentDataType.STRING
                    )
                ) {
                    entity.remove()
                }

                if (entity.persistentDataContainer.has(
                        GhostSpirit.legacyKey(),
                        PersistentDataType.STRING
                    )
                ) {
                    entity.remove()
                }
            }
        }

        SchedulerProvider.runAsync(plugin, Runnable {
            val loadedGraves = plugin.databaseHandler.loadAllGraves()
            if (loadedGraves.isEmpty()) {
                return@Runnable
            }

            for (grave in loadedGraves) {
                val loc = grave.location
                val world = loc.world ?: continue

                world.getChunkAtAsync(loc).thenAccept {
                    SchedulerProvider.runSyncAt(plugin, loc) {
                        val block = loc.block
                        block.type = Material.PLAYER_HEAD

                        val skull = block.state as? Skull
                        skull?.apply {
                            this.persistentDataContainer.set(
                                NamespacedKey(plugin, "grave"),
                                PersistentDataType.STRING,
                                grave.ownerId.toString()
                            )
                            applyPlayerProfile(this, grave.ownerId, grave.ownerName)
                            update(true, false)
                        }
                        val totalSeconds = plugin.config.getInt("graves.grave-despawn", 60)
                        val hologramIds = createHologram(loc, grave.ownerName, totalSeconds, grave.isPublic)

                        val ghostId: UUID? = null
                        if (grave.ghostActive) {
                            SchedulerProvider.runSyncLaterAt(plugin, loc, 40L) {
                                val newGhostId =
                                    plugin.ghostManager.createGhostAndGetId(grave.ownerId, loc, grave.ownerName)
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
                            }
                        }

                        val updated = grave.copy(
                            hologramIds = hologramIds,
                            ghostEntityId = ghostId,
                            ghostActive = grave.ghostActive
                        )

                        grave.hologramIds.forEach { oldId ->
                            Bukkit.getEntity(oldId)?.remove()
                        }

                        val blockLoc = loc.toBlockLocation()
                        activeGraves[getKey(blockLoc)] = updated
                        if (!updated.isPublic) {
                            plugin.timeGraveRemove.scheduleRemoval(updated)
                        }
                    }
                }
            }
        })
    }

    fun saveGravesToStorage() {
        performSaveAsync()
    }

    private fun performSaveAsync() {
        val snapshot = activeGraves.values.toList()
        SchedulerProvider.runAsync(plugin) {
            plugin.databaseHandler.writeGravesToJsonIfConfigured(snapshot)
        }
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
            SafeGravePlacer.findSafeLocationNear(
                location,
                radius,
                maxVert,
                hasNearbyGrave = { target, minDistance -> hasNearbyGrave(target, minDistance) },
                isAllowedLocation = { target -> regionOwnershipChecker.canPlaceGrave(player, target) }
            )?.let { safeLoc ->
                if (safeLoc != location) {
                    runCatching { plugin.logger.debug("Relocating grave from ${location.blockX},${location.blockY},${location.blockZ} to ${safeLoc.blockX},${safeLoc.blockY},${safeLoc.blockZ}") }
                    location = safeLoc
                }
            }
        }

        if (!regionOwnershipChecker.canPlaceGrave(player, location)) {
            val message = plugin.messageHandler.stringMessageToComponent("graves", "blocked-by-region")
            player.sendMessage(message)
            return null
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

        val totalSeconds = plugin.config.getInt("graves.grave-despawn", 60)
        val hologramIds = createHologram(location, player.name, totalSeconds, false)
        val ghostEntityId = plugin.ghostManager.createGhostAndGetId(player.uniqueId, location, player.name)

        val armorContents = mapOf(
            "helmet" to ((items[36] ?: ItemStack(Material.AIR)).clone()),
            "chestplate" to ((items[37] ?: ItemStack(Material.AIR)).clone()),
            "leggings" to ((items[38] ?: ItemStack(Material.AIR)).clone()),
            "boots" to ((items[39] ?: ItemStack(Material.AIR)).clone()),
            "offhand" to ((items[40] ?: ItemStack(Material.AIR)).clone())
        )
        val filteredItems = items.filterKeys { it in 0..35 }

        val grave = Grave(
            ownerId = player.uniqueId,
            ownerName = player.name,
            location = location,
            items = filteredItems.toMutableMap(),
            armorContents = armorContents,
            hologramIds = hologramIds,
            originalBlockData = originalBlockData,
            storedXp = xp,
            createdAt = System.currentTimeMillis(),
            ghostActive = ghostEntityId != null,
            ghostEntityId = ghostEntityId,
            isPublic = false,
            itemsStolen = 0,
            lastAttackerId = null
        )

        activeGraves[getKey(location)] = grave
        plugin.timeGraveRemove.scheduleRemoval(grave)
        saveGravesToStorage()
        addBackup(grave)
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
            releaseCollectionLock(grave)
            plugin.databaseHandler.clearCollectionState(grave)
            notifyGraveRemoved(grave)
        }
        activeGraves.clear()
        plugin.databaseHandler.writeGravesToJsonIfConfigured(emptyList())
    }

    fun updateHologramWithTime(grave: Grave, time: Int) {
        grave.hologramIds.forEach { id ->
            val entity = Bukkit.getEntity(id)
            if (entity is TextDisplay) {
                val text: Component = buildHologramText(grave.ownerName, time, grave.isPublic)
                entity.text(text)
            }
        }
    }

    fun getGravesFor(ownerId: UUID): List<Grave> =
        activeGraves.values.filter { it.ownerId == ownerId }

    fun getBackupsFor(ownerId: UUID): List<GraveBackup> =
        graveBackups.toList().filter { it.ownerId == ownerId }.sortedByDescending { it.createdAt }

    private fun createHologram(location: Location, ownerName: String, time: Int, isPublic: Boolean): List<UUID> {
        val text: Component = buildHologramText(ownerName, time, isPublic)
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

    private fun buildHologramText(ownerName: String, time: Int, isPublic: Boolean): Component {
        val key = if (isPublic) "hologram-public" else "hologram"
        return plugin.messageHandler.stringMessageToComponentNoPrefix(
            "graveh",
            key,
            mapOf("player" to ownerName, "time" to time.toString())
        )
    }

    fun getGraveAt(location: Location): Grave? {
        return activeGraves[getKey(location)]
    }

    fun activeGravesCount(): Int = activeGraves.size

    fun cleanupOrphanedHolograms(): Int {
        var removed = 0
        val hologramKey = NamespacedKey(plugin, "grave_hologram")
        val graveKey = NamespacedKey(plugin, "grave")
        for (world in Bukkit.getWorlds()) {
            for (entity in world.entities) {
                val display = entity as? TextDisplay ?: continue
                if (!display.persistentDataContainer.has(hologramKey, PersistentDataType.STRING)) continue

                val blockLocation = display.location.clone().subtract(0.5, 1.5, 0.5).toBlockLocation()
                if (getGraveAt(blockLocation) != null) continue

                val block = blockLocation.block
                val state = block.state
                if (state is Skull && state.persistentDataContainer.has(graveKey, PersistentDataType.STRING)) continue

                display.remove()
                removed++
            }
        }
        return removed
    }

    fun cleanupOrphanedHologramsBatched(limitPerTick: Int, onComplete: (Int) -> Unit): Boolean {
        val snapshot = Bukkit.getWorlds().flatMap { world -> world.entities.filterIsInstance<TextDisplay>() }.toMutableList()
        return runEntityCleanupBatch(snapshot, limitPerTick, onComplete) { display ->
            val hologramKey = NamespacedKey(plugin, "grave_hologram")
            val graveKey = NamespacedKey(plugin, "grave")
            if (!display.persistentDataContainer.has(hologramKey, PersistentDataType.STRING)) return@runEntityCleanupBatch false
            val blockLocation = display.location.clone().subtract(0.5, 1.5, 0.5).toBlockLocation()
            if (getGraveAt(blockLocation) != null) return@runEntityCleanupBatch false
            val block = blockLocation.block
            val state = block.state
            if (state is Skull && state.persistentDataContainer.has(graveKey, PersistentDataType.STRING)) return@runEntityCleanupBatch false
            display.remove()
            true
        }
    }

    fun cleanupOrphanedGhosts(): Int {
        var removed = 0
        val graveKey = NamespacedKey(plugin, "grave")
        val ghostKey = GhostSpirit.key(plugin)
        val legacyGhostKey = GhostSpirit.legacyKey()

        for (world in Bukkit.getWorlds()) {
            for (entity in world.entities) {
                if (!entity.persistentDataContainer.has(ghostKey, PersistentDataType.STRING) &&
                    !entity.persistentDataContainer.has(legacyGhostKey, PersistentDataType.STRING)
                ) {
                    continue
                }

                val baseLocation = entity.location.clone().subtract(0.5, 2.7, 0.5).toBlockLocation()
                if (getGraveAt(baseLocation) != null) continue

                val block = baseLocation.block
                val state = block.state
                if (state is Skull && state.persistentDataContainer.has(graveKey, PersistentDataType.STRING)) continue

                entity.remove()
                removed++
            }
        }
        return removed
    }

    fun cleanupOrphanedGhostsBatched(limitPerTick: Int, onComplete: (Int) -> Unit): Boolean {
        val snapshot = Bukkit.getWorlds().flatMap { it.entities }.toMutableList()
        return runEntityCleanupBatch(snapshot, limitPerTick, onComplete) { entity ->
            val graveKey = NamespacedKey(plugin, "grave")
            val ghostKey = GhostSpirit.key(plugin)
            val legacyGhostKey = GhostSpirit.legacyKey()
            if (!entity.persistentDataContainer.has(ghostKey, PersistentDataType.STRING) &&
                !entity.persistentDataContainer.has(legacyGhostKey, PersistentDataType.STRING)
            ) return@runEntityCleanupBatch false
            val baseLocation = entity.location.clone().subtract(0.5, 2.7, 0.5).toBlockLocation()
            if (getGraveAt(baseLocation) != null) return@runEntityCleanupBatch false
            val block = baseLocation.block
            val state = block.state
            if (state is Skull && state.persistentDataContainer.has(graveKey, PersistentDataType.STRING)) return@runEntityCleanupBatch false
            entity.remove()
            true
        }
    }

    fun cleanupOrphanedGraves(): Int {
        var removed = 0
        val graveKey = NamespacedKey(plugin, "grave")

        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) {
                for (state in chunk.tileEntities) {
                    val skull = state as? Skull ?: continue
                    if (!skull.persistentDataContainer.has(graveKey, PersistentDataType.STRING)) continue

                    val location = skull.location.toBlockLocation()
                    if (getGraveAt(location) != null) continue

                    if (removeGraveAt(location)) {
                        removed++
                    }
                }
            }
        }
        return removed
    }

    fun cleanupOrphanedGravesBatched(limitPerTick: Int, onComplete: (Int) -> Unit): Boolean {
        val skulls = Bukkit.getWorlds()
            .flatMap { it.loadedChunks.toList() }
            .flatMap { chunk -> chunk.tileEntities.filterIsInstance<Skull>() }
            .toMutableList()
        if (cleanupTask != null) return false
        val startedAt = System.currentTimeMillis()
        var removed = 0
        val iterator = skulls.iterator()
        val graveKey = NamespacedKey(plugin, "grave")
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            var processed = 0
            while (iterator.hasNext() && processed < limitPerTick) {
                val skull = iterator.next()
                processed++
                if (!skull.persistentDataContainer.has(graveKey, PersistentDataType.STRING)) continue
                val location = skull.location.toBlockLocation()
                if (getGraveAt(location) != null) continue
                if (removeGraveAt(location)) removed++
            }
            if (!iterator.hasNext()) {
                cleanupTask?.cancel()
                cleanupTask = null
                plugin.runtimeMetrics.recordCleanup(System.currentTimeMillis() - startedAt, removed.toLong())
                onComplete(removed)
            }
        }, 1L, 1L)
        return true
    }

    private fun <T> runEntityCleanupBatch(
        snapshot: MutableList<T>,
        limitPerTick: Int,
        onComplete: (Int) -> Unit,
        remover: (T) -> Boolean
    ): Boolean {
        if (cleanupTask != null) return false
        val startedAt = System.currentTimeMillis()
        var removed = 0
        val iterator = snapshot.iterator()
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            var processed = 0
            while (iterator.hasNext() && processed < limitPerTick) {
                val entity = iterator.next()
                processed++
                if (remover(entity)) {
                    removed++
                }
            }
            if (!iterator.hasNext()) {
                cleanupTask?.cancel()
                cleanupTask = null
                plugin.runtimeMetrics.recordCleanup(System.currentTimeMillis() - startedAt, removed.toLong())
                onComplete(removed)
            }
        }, 1L, 1L)
        return true
    }

    fun dropGraveItems(grave: Grave) {
        val location = grave.location
        val world = location.world ?: return
        (grave.items.values + grave.armorContents.values).forEach { item ->
            if (item.type != Material.AIR) {
                world.dropItemNaturally(location, item)
            }
        }
    }

    fun makeGravePublic(grave: Grave) {
        plugin.timeGraveRemove.cancelRemoval(grave)
        val updated = grave.copy(isPublic = true)
        activeGraves[getKey(grave.location)] = updated
        updateHologramWithTime(updated, 0)
        saveGravesToStorage()
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
        releaseCollectionLock(grave)
        plugin.databaseHandler.clearCollectionState(grave)
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
        val block = location.block
        if (block.type == Material.PLAYER_HEAD) {
            location.world.getNearbyEntities(location.clone().add(0.5, 1.5, 0.5), 1.0, 1.0, 1.0).forEach { entity ->
                if (entity is TextDisplay && entity.persistentDataContainer.has(NamespacedKey(plugin, "grave_hologram"), PersistentDataType.STRING)) {
                    entity.remove()
                }
            }
            removeGhostsNear(location)
            block.type = Material.AIR
            collectionLocks.remove(getKey(location))
            saveGravesToStorage()
            return true
        }
        return false
    }

    fun beginCollection(grave: Grave, collectorId: UUID): CollectionTicket? {
        val lockKey = getKey(grave.location.toBlockLocation())
        if (!collectionLocks.add(lockKey)) {
            plugin.runtimeMetrics.incrementCollectionClaimConflict()
            return null
        }
        if (!plugin.databaseHandler.tryAcquireCollectionClaim(grave)) {
            collectionLocks.remove(lockKey)
            plugin.runtimeMetrics.incrementCollectionClaimConflict()
            return null
        }
        val ttlMillis = plugin.config.getLong("graves.collection.claim-ttl-ms", 15000L).coerceAtLeast(3000L)
        val tx = plugin.databaseHandler.beginCollectionTx(grave, collectorId, ttlMillis)
        if (tx == null) {
            collectionLocks.remove(lockKey)
            plugin.databaseHandler.releaseCollectionClaim(grave)
            plugin.runtimeMetrics.incrementCollectionClaimConflict()
            return null
        }
        return CollectionTicket(tx.txId, tx.graveId, collectorId)
    }

    fun markCollecting(grave: Grave, ticket: CollectionTicket): Boolean {
        return plugin.databaseHandler.transitionCollectionTx(
            graveId = grave.graveId,
            txId = ticket.txId,
            from = CollectionState.CLAIMED,
            to = CollectionState.COLLECTING
        )
    }

    fun markCollected(grave: Grave, ticket: CollectionTicket): Boolean {
        val marked = plugin.databaseHandler.markCollectedTx(grave.graveId, ticket.txId)
        if (marked) return true
        val rolledBack = plugin.databaseHandler.transitionCollectionTx(
            graveId = grave.graveId,
            txId = ticket.txId,
            from = CollectionState.COLLECTING,
            to = CollectionState.FAILED_RECOVERABLE,
            error = "safe rollback: COLLECTED persist failed"
        )
        if (!rolledBack) {
            plugin.runtimeMetrics.incrementCollectionTxTransitionFail()
        }
        return false
    }

    fun markCollectionFailed(grave: Grave, ticket: CollectionTicket, error: String?) {
        val transitioned = plugin.databaseHandler.transitionCollectionTx(
            graveId = grave.graveId,
            txId = ticket.txId,
            from = CollectionState.COLLECTING,
            to = CollectionState.FAILED,
            error = error
        )
        if (!transitioned) {
            plugin.runtimeMetrics.incrementCollectionTxTransitionFail()
        }
    }

    fun releaseCollectionLock(grave: Grave) {
        collectionLocks.remove(getKey(grave.location.toBlockLocation()))
        plugin.databaseHandler.releaseCollectionClaim(grave)
    }

    fun releaseCollectionLockById(identifier: UUID): Boolean {
        val byGraveId = activeGraves.values.firstOrNull { it.graveId == identifier }
        if (byGraveId != null) {
            releaseCollectionLock(byGraveId)
            return true
        }
        return false
    }


    private fun getKey(location: Location): String {
        return GraveIdentity.locationKey(location)
    }

    fun restoreBackup(backup: GraveBackup): BackupRestoreResult {
        val location = backup.location.toBlockLocation()
        if (getGraveAt(location) != null) {
            return BackupRestoreResult.LOCATION_OCCUPIED
        }

        if (location.world == null) {
            return BackupRestoreResult.WORLD_MISSING
        }
        return runCatching {
            val block = location.block
            val originalBlockData = if (block.type != Material.AIR) block.blockData.clone() else Bukkit.createBlockData(Material.AIR)
            block.type = Material.PLAYER_HEAD

            val skull = block.state as? Skull
            skull?.apply {
                this.persistentDataContainer.set(
                    NamespacedKey(plugin, "grave"),
                    PersistentDataType.STRING,
                    backup.ownerId.toString()
                )
                applyPlayerProfile(this, backup.ownerId, backup.ownerName)
                update(true, false)
            }

            val totalSeconds = plugin.config.getInt("graves.grave-despawn", 60)
            val hologramIds = createHologram(location, backup.ownerName, totalSeconds, false)
            val ghostEntityId = plugin.ghostManager.createGhostAndGetId(backup.ownerId, location, backup.ownerName)

            val grave = Grave(
                ownerId = backup.ownerId,
                ownerName = backup.ownerName,
                location = location,
                items = backup.items.mapValues { it.value.clone() },
                armorContents = backup.armorContents.mapValues { it.value.clone() },
                hologramIds = hologramIds,
                originalBlockData = originalBlockData,
                storedXp = backup.storedXp,
                createdAt = backup.createdAt,
                ghostActive = ghostEntityId != null,
                ghostEntityId = ghostEntityId,
                isPublic = false,
                itemsStolen = 0,
                lastAttackerId = null
            )

            activeGraves[getKey(location)] = grave
            plugin.timeGraveRemove.scheduleRemoval(grave)
            saveGravesToStorage()
            BackupRestoreResult.SUCCESS
        }.getOrElse { ex ->
            plugin.logger.warning("Failed to restore grave backup for ${backup.ownerName}: ${ex.message}")
            BackupRestoreResult.FAILED
        }
    }

    private fun addBackup(grave: Grave) {
        if (!plugin.config.getBoolean("graves.backups.enabled", true)) {
            return
        }

        val alreadyStored = graveBackups.any {
            it.ownerId == grave.ownerId && it.createdAt == grave.createdAt &&
                it.location.blockX == grave.location.blockX &&
                it.location.blockY == grave.location.blockY &&
                it.location.blockZ == grave.location.blockZ &&
                it.location.world?.name == grave.location.world?.name
        }
        if (alreadyStored) {
            return
        }

        val backup = GraveBackup(
            ownerId = grave.ownerId,
            ownerName = grave.ownerName,
            location = grave.location.clone(),
            items = grave.items.mapValues { it.value.clone() },
            armorContents = grave.armorContents.mapValues { it.value.clone() },
            storedXp = grave.storedXp,
            createdAt = grave.createdAt
        )

        graveBackups.add(backup)
        trimBackupsIfNeeded(grave.ownerId)
        saveBackupsAsync()
    }

    private fun trimBackupsIfNeeded(ownerId: UUID) {
        val maxPerPlayer = plugin.config.getInt("graves.backups.max-per-player", 50)
        if (maxPerPlayer > 0) {
            val playerBackups = graveBackups.filter { it.ownerId == ownerId }
                .sortedBy { it.createdAt }
            val toRemove = (playerBackups.size - maxPerPlayer).coerceAtLeast(0)
            if (toRemove > 0) {
                val removeSet = playerBackups.take(toRemove).map { it.id }.toSet()
                graveBackups.removeIf { it.id in removeSet }
            }
        }

        val maxTotal = plugin.config.getInt("graves.backups.max-total", 5000)
        if (maxTotal > 0 && graveBackups.size > maxTotal) {
            val sorted = graveBackups.sortedBy { it.backedUpAt }
            val toRemove = (graveBackups.size - maxTotal).coerceAtLeast(0)
            if (toRemove > 0) {
                val removeSet = sorted.take(toRemove).map { it.id }.toSet()
                graveBackups.removeIf { it.id in removeSet }
            }
        }
    }

    private fun saveBackupsAsync() {
        val snapshot = graveBackups.toList()
        SchedulerProvider.runAsync(plugin) {
            backupStore.saveAllBackups(snapshot)
        }
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

    private fun removeGhostsNear(location: Location) {
        val world = location.world ?: return
        val ghostKey = GhostSpirit.key(plugin)
        val legacyGhostKey = GhostSpirit.legacyKey()
        val ghostLocation = location.clone().add(0.5, 2.7, 0.5)
        world.getNearbyEntities(ghostLocation, 2.0, 2.0, 2.0).forEach { entity ->
            if (entity.persistentDataContainer.has(ghostKey, PersistentDataType.STRING) ||
                entity.persistentDataContainer.has(legacyGhostKey, PersistentDataType.STRING)
            ) {
                entity.remove()
            }
        }
    }
}
