package pl.syntaxdevteam.gravediggerx.graves.lifecycle

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.graves.GraveManager
import pl.syntaxdevteam.gravediggerx.graves.SafeGravePlacer
import pl.syntaxdevteam.gravediggerx.graves.backup.GraveBackup
import pl.syntaxdevteam.gravediggerx.graves.backup.GraveBackupService
import pl.syntaxdevteam.gravediggerx.graves.collection.GraveCollectionService
import pl.syntaxdevteam.gravediggerx.graves.registry.GraveRegistry
import pl.syntaxdevteam.gravediggerx.graves.rendering.GraveWorldRenderer
import pl.syntaxdevteam.gravediggerx.integrations.RegionOwnershipChecker

class GraveLifecycleService(
    private val plugin: GraveDiggerX,
    private val registry: GraveRegistry,
    private val renderer: GraveWorldRenderer,
    private val collections: GraveCollectionService,
    private val backups: GraveBackupService,
    private val save: () -> Unit
) {
    private val regionOwnership = RegionOwnershipChecker.create(plugin)

    fun create(player: Player, items: Map<Int, ItemStack>, xp: Int): Grave? {
        val maxGraves = plugin.config.getInt("graves.max-per-player", 3)
        if (registry.findByOwner(player.uniqueId).size >= maxGraves) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("graves", "reached-max-graves", mapOf("limit" to maxGraves.toString())))
            return null
        }

        var location = player.location.toBlockLocation()
        if (plugin.config.getBoolean("graves.safe-placement.enabled", true)) {
            SafeGravePlacer.findSafeLocationNear(
                location,
                plugin.config.getInt("graves.safe-placement.radius", 8),
                plugin.config.getInt("graves.safe-placement.max-vertical-scan", 3),
                hasNearbyGrave = registry::hasGraveNear,
                isAllowedLocation = { regionOwnership.canPlaceGrave(player, it) }
            )?.let { location = it }
        }
        if (!regionOwnership.canPlaceGrave(player, location)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("graves", "blocked-by-region"))
            return null
        }

        val block = location.block
        val original = if (block.type == Material.AIR) Bukkit.createBlockData(Material.AIR) else block.blockData.clone()
        val rendered = renderer.place(location, player.uniqueId, player.name, isPublic = false, spawnGhost = true)
        val grave = Grave(
            ownerId = player.uniqueId,
            ownerName = player.name,
            location = location,
            items = items.filterKeys { it in 0..35 }.mapValues { it.value.clone() },
            armorContents = armor(items),
            hologramIds = rendered.hologramIds,
            originalBlockData = original,
            storedXp = xp,
            ghostActive = rendered.ghostEntityId != null,
            ghostEntityId = rendered.ghostEntityId,
            isPublic = false
        )
        registry.add(grave)
        plugin.timeGraveRemove.scheduleRemoval(grave)
        save()
        backups.add(grave)
        return grave
    }

    fun remove(grave: Grave, persist: Boolean = true) {
        plugin.timeGraveRemove.cancelRemoval(grave)
        renderer.remove(grave)
        collections.release(grave)
        plugin.databaseHandler.clearCollectionState(grave)
        registry.remove(grave.location)
        if (persist) save()
    }

    fun removeAt(location: org.bukkit.Location): Boolean {
        registry.findAt(location)?.let { remove(it); return true }
        if (!renderer.removeUntrackedAt(location)) return false
        collections.forget(location)
        save()
        return true
    }

    fun removeAll() {
        registry.snapshot().forEach { remove(it, persist = false) }
        registry.clear()
        plugin.databaseHandler.writeGravesToJsonIfConfigured(emptyList())
    }

    fun makePublic(grave: Grave): Grave {
        plugin.timeGraveRemove.cancelRemoval(grave)
        val updated = grave.copy(isPublic = true)
        registry.add(updated)
        updated.hologramIds.forEach { id ->
            (Bukkit.getEntity(id) as? org.bukkit.entity.TextDisplay)?.text(
                plugin.messageHandler.stringMessageToComponentNoPrefix("graveh", "hologram-public", mapOf("player" to updated.ownerName))
            )
        }
        save()
        return updated
    }

    fun dropItems(grave: Grave) {
        val world = grave.location.world ?: return
        (grave.items.values + grave.armorContents.values).filter { it.type != Material.AIR }
            .forEach { world.dropItemNaturally(grave.location, it) }
    }

    fun restore(backup: GraveBackup): GraveManager.BackupRestoreResult {
        val location = backup.location.toBlockLocation()
        if (registry.findAt(location) != null) return GraveManager.BackupRestoreResult.LOCATION_OCCUPIED
        if (location.world == null) return GraveManager.BackupRestoreResult.WORLD_MISSING
        return runCatching {
            val block = location.block
            val original = if (block.type == Material.AIR) Bukkit.createBlockData(Material.AIR) else block.blockData.clone()
            val rendered = renderer.place(location, backup.ownerId, backup.ownerName, false, true)
            val grave = Grave(
                ownerId = backup.ownerId, ownerName = backup.ownerName, location = location,
                items = backup.items.mapValues { it.value.clone() },
                armorContents = backup.armorContents.mapValues { it.value.clone() },
                hologramIds = rendered.hologramIds, originalBlockData = original,
                storedXp = backup.storedXp, createdAt = backup.createdAt,
                ghostActive = rendered.ghostEntityId != null, ghostEntityId = rendered.ghostEntityId,
                isPublic = false
            )
            registry.add(grave)
            plugin.timeGraveRemove.scheduleRemoval(grave)
            save()
            GraveManager.BackupRestoreResult.SUCCESS
        }.getOrElse {
            plugin.logger.warning("Failed to restore grave backup for ${backup.ownerName}: ${it.message}")
            GraveManager.BackupRestoreResult.FAILED
        }
    }

    private fun armor(items: Map<Int, ItemStack>) = mapOf(
        "helmet" to (items[36] ?: ItemStack(Material.AIR)).clone(),
        "chestplate" to (items[37] ?: ItemStack(Material.AIR)).clone(),
        "leggings" to (items[38] ?: ItemStack(Material.AIR)).clone(),
        "boots" to (items[39] ?: ItemStack(Material.AIR)).clone(),
        "offhand" to (items[40] ?: ItemStack(Material.AIR)).clone()
    )
}
