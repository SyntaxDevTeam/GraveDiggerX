package pl.syntaxdevteam.gravediggerx.graves.cleanup

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.block.Skull
import org.bukkit.entity.Entity
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.CancellableTask
import pl.syntaxdevteam.gravediggerx.common.SchedulerProvider
import pl.syntaxdevteam.gravediggerx.graves.registry.GraveRegistry
import pl.syntaxdevteam.gravediggerx.spirits.GhostSpirit

class GraveCleanupService(
    private val plugin: GraveDiggerX,
    private val registry: GraveRegistry,
    private val removeUntrackedGrave: (org.bukkit.Location) -> Boolean
) {
    @Volatile private var task: CancellableTask? = null

    fun cleanupHolograms(limit: Int, onComplete: (Int) -> Unit): Boolean {
        val snapshot = Bukkit.getWorlds().flatMap { it.entities.filterIsInstance<TextDisplay>() }.toMutableList()
        val hologramKey = NamespacedKey(plugin, "grave_hologram")
        val graveKey = NamespacedKey(plugin, "grave")
        return runBatch(snapshot, limit, onComplete) { display ->
            if (!display.persistentDataContainer.has(hologramKey, PersistentDataType.STRING)) return@runBatch false
            val location = display.location.clone().subtract(0.5, 1.5, 0.5).toBlockLocation()
            if (registry.findAt(location) != null || hasGraveMarker(location.block.state as? Skull, graveKey)) return@runBatch false
            display.remove()
            true
        }
    }

    fun cleanupGhosts(limit: Int, onComplete: (Int) -> Unit): Boolean {
        val snapshot = Bukkit.getWorlds().flatMap { it.entities }.toMutableList()
        val graveKey = NamespacedKey(plugin, "grave")
        val ghostKey = GhostSpirit.key(plugin)
        val legacyKey = GhostSpirit.legacyKey()
        return runBatch(snapshot, limit, onComplete) { entity ->
            if (!isGhost(entity, ghostKey, legacyKey)) return@runBatch false
            val location = entity.location.clone().subtract(0.5, 2.7, 0.5).toBlockLocation()
            if (registry.findAt(location) != null || hasGraveMarker(location.block.state as? Skull, graveKey)) return@runBatch false
            entity.remove()
            true
        }
    }

    fun cleanupGraves(limit: Int, onComplete: (Int) -> Unit): Boolean {
        val skulls = Bukkit.getWorlds().flatMap { it.loadedChunks.toList() }
            .flatMap { it.tileEntities.filterIsInstance<Skull>() }.toMutableList()
        val graveKey = NamespacedKey(plugin, "grave")
        return runBatch(skulls, limit, onComplete) { skull ->
            if (!hasGraveMarker(skull, graveKey)) return@runBatch false
            val location = skull.location.toBlockLocation()
            registry.findAt(location) == null && removeUntrackedGrave(location)
        }
    }

    fun cleanupHologramsNow(): Int = cleanupNow(
        Bukkit.getWorlds().flatMap { it.entities.filterIsInstance<TextDisplay>() }
    ) { display ->
        val hologramKey = NamespacedKey(plugin, "grave_hologram")
        val graveKey = NamespacedKey(plugin, "grave")
        if (!display.persistentDataContainer.has(hologramKey, PersistentDataType.STRING)) return@cleanupNow false
        val location = display.location.clone().subtract(0.5, 1.5, 0.5).toBlockLocation()
        if (registry.findAt(location) != null || hasGraveMarker(location.block.state as? Skull, graveKey)) return@cleanupNow false
        display.remove(); true
    }

    fun cleanupGhostsNow(): Int = cleanupNow(Bukkit.getWorlds().flatMap { it.entities }) { entity ->
        val graveKey = NamespacedKey(plugin, "grave")
        val ghostKey = GhostSpirit.key(plugin)
        val legacyKey = GhostSpirit.legacyKey()
        if (!isGhost(entity, ghostKey, legacyKey)) return@cleanupNow false
        val location = entity.location.clone().subtract(0.5, 2.7, 0.5).toBlockLocation()
        if (registry.findAt(location) != null || hasGraveMarker(location.block.state as? Skull, graveKey)) return@cleanupNow false
        entity.remove(); true
    }

    fun cleanupGravesNow(): Int {
        val graveKey = NamespacedKey(plugin, "grave")
        val skulls = Bukkit.getWorlds().flatMap { it.loadedChunks.toList() }.flatMap { it.tileEntities.filterIsInstance<Skull>() }
        return cleanupNow(skulls) { skull ->
            val location = skull.location.toBlockLocation()
            hasGraveMarker(skull, graveKey) && registry.findAt(location) == null && removeUntrackedGrave(location)
        }
    }

    private fun <T> cleanupNow(items: List<T>, remover: (T) -> Boolean): Int = items.count(remover)

    private fun hasGraveMarker(skull: Skull?, key: NamespacedKey): Boolean =
        skull?.persistentDataContainer?.has(key, PersistentDataType.STRING) == true

    private fun isGhost(entity: Entity, key: NamespacedKey, legacyKey: NamespacedKey): Boolean =
        entity.persistentDataContainer.has(key, PersistentDataType.STRING) ||
            entity.persistentDataContainer.has(legacyKey, PersistentDataType.STRING)

    private fun <T> runBatch(snapshot: MutableList<T>, limit: Int, onComplete: (Int) -> Unit, remover: (T) -> Boolean): Boolean {
        if (task != null) return false
        val startedAt = System.currentTimeMillis()
        var removed = 0
        val iterator = snapshot.iterator()
        task = SchedulerProvider.runGlobalRepeating(plugin, 1L, 1L, Runnable {
            var processed = 0
            while (iterator.hasNext() && processed < limit) {
                if (remover(iterator.next())) removed++
                processed++
            }
            if (!iterator.hasNext()) {
                task?.cancel()
                task = null
                plugin.runtimeMetrics.recordCleanup(System.currentTimeMillis() - startedAt, removed.toLong())
                onComplete(removed)
            }
        })
        return true
    }
}
