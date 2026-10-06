package pl.syntaxdevteam.gravediggerx.graves

import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.SchedulerProvider
import pl.syntaxdevteam.gravediggerx.graves.backup.GraveBackup
import pl.syntaxdevteam.gravediggerx.graves.backup.GraveBackupService
import pl.syntaxdevteam.gravediggerx.graves.cleanup.GraveCleanupService
import pl.syntaxdevteam.gravediggerx.graves.collection.CollectionTicket
import pl.syntaxdevteam.gravediggerx.graves.collection.GraveCollectionService
import pl.syntaxdevteam.gravediggerx.graves.lifecycle.GraveLifecycleService
import pl.syntaxdevteam.gravediggerx.graves.loading.GraveLoader
import pl.syntaxdevteam.gravediggerx.graves.registry.GraveRegistry
import pl.syntaxdevteam.gravediggerx.graves.rendering.GraveWorldRenderer
import pl.syntaxdevteam.gravediggerx.graves.rendering.SkullProfileResolver
import java.util.UUID

/** Public facade for grave operations. State and implementation details live in focused services. */
class GraveManager(private val plugin: GraveDiggerX) {
    private val registry = GraveRegistry()
    val hologramManager = GraveHologramManager(plugin)
    private val renderer = GraveWorldRenderer(plugin, hologramManager, SkullProfileResolver(plugin))
    private val collections = GraveCollectionService(plugin, registry)
    private val backups = GraveBackupService(plugin)
    private val lifecycle = GraveLifecycleService(plugin, registry, renderer, collections, backups, ::saveGravesToStorage)
    private val cleanup = GraveCleanupService(plugin, registry, lifecycle::removeAt)
    private val loader = GraveLoader(plugin, registry, renderer)

    enum class BackupRestoreResult { SUCCESS, LOCATION_OCCUPIED, WORLD_MISSING, FAILED }

    fun loadGravesFromStorage() = loader.load()

    fun saveGravesToStorage() {
        val snapshot = registry.snapshot()
        SchedulerProvider.runAsync(plugin) { plugin.databaseHandler.writeGravesToJsonIfConfigured(snapshot) }
    }

    fun createGraveAndGetIt(player: org.bukkit.entity.Player, items: Map<Int, ItemStack>, xp: Int = 0): Grave? =
        lifecycle.create(player, items, xp)

    fun removeAllGraves() = lifecycle.removeAll()
    fun updateHologramWithTime(grave: Grave, time: Int) = hologramManager.updateHologramWithTime(grave, time)
    fun getGravesFor(ownerId: UUID): List<Grave> = registry.findByOwner(ownerId)
    fun getBackupsFor(ownerId: UUID): List<GraveBackup> = backups.findByOwner(ownerId)
    fun getGraveAt(location: Location): Grave? = registry.findAt(location)
    fun activeGravesCount(): Int = registry.size
    fun cleanupOrphanedHolograms(): Int = cleanup.cleanupHologramsNow()
    fun cleanupOrphanedHologramsBatched(limitPerTick: Int, onComplete: (Int) -> Unit): Boolean =
        cleanup.cleanupHolograms(limitPerTick, onComplete)
    fun cleanupOrphanedGhosts(): Int = cleanup.cleanupGhostsNow()
    fun cleanupOrphanedGhostsBatched(limitPerTick: Int, onComplete: (Int) -> Unit): Boolean =
        cleanup.cleanupGhosts(limitPerTick, onComplete)
    fun cleanupOrphanedGraves(): Int = cleanup.cleanupGravesNow()
    fun cleanupOrphanedGravesBatched(limitPerTick: Int, onComplete: (Int) -> Unit): Boolean =
        cleanup.cleanupGraves(limitPerTick, onComplete)
    fun dropGraveItems(grave: Grave) = lifecycle.dropItems(grave)
    fun makeGravePublic(grave: Grave) { lifecycle.makePublic(grave) }
    fun removeGrave(grave: Grave) = lifecycle.remove(grave)
    fun removeGraveAt(location: Location): Boolean = lifecycle.removeAt(location)
    fun beginCollection(grave: Grave, collectorId: UUID): CollectionTicket? = collections.begin(grave, collectorId)
    fun markCollecting(grave: Grave, ticket: CollectionTicket): Boolean = collections.markCollecting(grave, ticket)
    fun markCollected(grave: Grave, ticket: CollectionTicket): Boolean = collections.markCollected(grave, ticket)
    fun markCollectionFailed(grave: Grave, ticket: CollectionTicket, error: String?) = collections.markFailed(grave, ticket, error)
    fun releaseCollectionLock(grave: Grave) = collections.release(grave)
    fun releaseCollectionLockById(identifier: UUID): Boolean = collections.releaseById(identifier)
    fun restoreBackup(backup: GraveBackup): BackupRestoreResult = lifecycle.restore(backup)
}
