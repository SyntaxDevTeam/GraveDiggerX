package pl.syntaxdevteam.gravediggerx.graves

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TimeGraveRemove(private val plugin: GraveDiggerX) {

    private val scheduledTasks = ConcurrentHashMap<UUID, BukkitTask>()

    fun scheduleRemoval(grave: Grave) {
        val totalSeconds = plugin.config.getInt("graves.grave-despawn", 60)
        var secondsLeft = totalSeconds

        scheduledTasks[grave.ownerId]?.cancel()

        lateinit var task: BukkitTask
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            val player: Player? = Bukkit.getPlayer(grave.ownerId)

            if (plugin.graveManager.getGraveAt(grave.location) == null) {
                scheduledTasks.remove(grave.ownerId)
                task.cancel()
                return@Runnable
            }

            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (player != null && player.isOnline) {
                    val msg = plugin.messageHandler.getMessage(
                        "graves",
                        "removal-countdown",
                        mapOf(
                            "time" to secondsLeft.toString(),
                            "x" to grave.location.blockX.toString(),
                            "y" to grave.location.blockY.toString(),
                            "z" to grave.location.blockZ.toString()
                        )
                    )
                    player.sendActionBar(msg)
                }
            })

            if (secondsLeft <= 0) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    plugin.ghostManager.removeGhost(grave.ownerId)
                    plugin.graveManager.removeGrave(grave)
                })
                scheduledTasks.remove(grave.ownerId)
                task.cancel()
                return@Runnable
            }

            secondsLeft--
        }, 0L, 20L)

        scheduledTasks[grave.ownerId] = task
    }

    fun cancelRemoval(grave: Grave) {
        scheduledTasks[grave.ownerId]?.cancel()
        scheduledTasks.remove(grave.ownerId)
    }

    fun cancelAll() {
        scheduledTasks.values.forEach { it.cancel() }
        scheduledTasks.clear()
    }
}
