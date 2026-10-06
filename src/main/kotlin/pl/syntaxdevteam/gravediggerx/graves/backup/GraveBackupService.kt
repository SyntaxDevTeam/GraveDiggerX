package pl.syntaxdevteam.gravediggerx.graves.backup

import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.SchedulerProvider
import pl.syntaxdevteam.gravediggerx.graves.Grave
import java.util.Collections
import java.util.UUID

class GraveBackupService(private val plugin: GraveDiggerX) {
    private val store = GraveBackupStore(plugin)
    private val backups = Collections.synchronizedList(store.loadAllBackups().toMutableList())

    fun findByOwner(ownerId: UUID): List<GraveBackup> =
        backups.toList().filter { it.ownerId == ownerId }.sortedByDescending { it.createdAt }

    fun add(grave: Grave) {
        if (!plugin.config.getBoolean("graves.backups.enabled", true)) return
        val exists = backups.any {
            it.ownerId == grave.ownerId && it.createdAt == grave.createdAt &&
                it.location.blockX == grave.location.blockX && it.location.blockY == grave.location.blockY &&
                it.location.blockZ == grave.location.blockZ && it.location.world?.name == grave.location.world?.name
        }
        if (exists) return

        backups.add(
            GraveBackup(
                ownerId = grave.ownerId,
                ownerName = grave.ownerName,
                location = grave.location.clone(),
                items = grave.items.mapValues { it.value.clone() },
                armorContents = grave.armorContents.mapValues { it.value.clone() },
                storedXp = grave.storedXp,
                createdAt = grave.createdAt
            )
        )
        trim(grave.ownerId)
        saveAsync()
    }

    private fun trim(ownerId: UUID) {
        val maxPerPlayer = plugin.config.getInt("graves.backups.max-per-player", 50)
        if (maxPerPlayer > 0) {
            val excess = (backups.count { it.ownerId == ownerId } - maxPerPlayer).coerceAtLeast(0)
            val ids = backups.filter { it.ownerId == ownerId }.sortedBy { it.createdAt }.take(excess).map { it.id }.toSet()
            if (ids.isNotEmpty()) backups.removeIf { it.id in ids }
        }
        val maxTotal = plugin.config.getInt("graves.backups.max-total", 5000)
        if (maxTotal > 0) {
            val excess = (backups.size - maxTotal).coerceAtLeast(0)
            val ids = backups.sortedBy { it.backedUpAt }.take(excess).map { it.id }.toSet()
            if (ids.isNotEmpty()) backups.removeIf { it.id in ids }
        }
    }

    private fun saveAsync() {
        val snapshot = backups.toList()
        SchedulerProvider.runAsync(plugin) { store.saveAllBackups(snapshot) }
    }
}
