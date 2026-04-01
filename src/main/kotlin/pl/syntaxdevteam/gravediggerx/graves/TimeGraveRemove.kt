package pl.syntaxdevteam.gravediggerx.graves

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.CancellableTask
import pl.syntaxdevteam.gravediggerx.common.SchedulerProvider
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TimeGraveRemove(private val plugin: GraveDiggerX) {

    private val scheduledTasks = ConcurrentHashMap<String, CancellableTask>()

    fun scheduleRemoval(grave: Grave) {
        val totalSeconds = plugin.config.getInt("graves.grave-despawn", 60)
        val taskKey = graveTaskKey(grave)

        scheduledTasks[taskKey]?.cancel()

        val runnable = object : Runnable {
            var secondsLeft = totalSeconds

            override fun run() {
                val player: Player? = Bukkit.getPlayer(grave.ownerId)
                val world = grave.location.world

                if (world == null) {
                    cancelScheduledRemoval(taskKey)
                    return
                }

                if (plugin.graveManager.getGraveAt(grave.location) == null) {
                    cancelScheduledRemoval(taskKey)
                    return
                }

                if (grave.location.block.type != org.bukkit.Material.PLAYER_HEAD) {
                    plugin.graveManager.removeGrave(grave)
                    cancelScheduledRemoval(taskKey)
                    return
                }

                plugin.graveManager.updateHologramWithTime(grave, secondsLeft)

                if (player != null && player.isOnline) {
                    val msg = plugin.messageHandler.stringMessageToComponent(
                        "graves",
                        "removal-countdown",
                        mapOf(
                            "time" to secondsLeft.toString(),
                            "x" to grave.location.blockX.toString(),
                            "y" to grave.location.blockY.toString(),
                            "z" to grave.location.blockZ.toString()
                        )
                    )
                    SchedulerProvider.runSyncAt(plugin, player.location, Runnable {
                        player.sendActionBar(msg)
                    })
                }

                if (secondsLeft <= 0) {
                    val expirationAction = GraveExpirationAction.fromString(
                        plugin.config.getString("graves.expiration-action", "DISAPPEAR")!!
                    )
                    plugin.ghostManager.removeGhost(grave.ownerId)
                    when (expirationAction) {
                        GraveExpirationAction.DROP_ITEMS -> {
                            plugin.graveManager.dropGraveItems(grave)
                            plugin.graveManager.removeGrave(grave)
                        }
                        GraveExpirationAction.BECOME_PUBLIC -> {
                            plugin.graveManager.makeGravePublic(grave)
                        }
                        GraveExpirationAction.DISAPPEAR -> {
                            plugin.graveManager.removeGrave(grave)
                        }
                    }
                    cancelScheduledRemoval(taskKey)
                    return
                }

                secondsLeft--
            }
        }

        scheduledTasks[taskKey] = SchedulerProvider.runSyncRepeatingAt(plugin, grave.location, 0L, 20L, runnable)
    }

    fun cancelRemoval(grave: Grave) {
        cancelScheduledRemoval(graveTaskKey(grave))
    }

    fun cancelAll() {
        scheduledTasks.values.forEach { it.cancel() }
        scheduledTasks.clear()
    }

    private fun cancelScheduledRemoval(taskKey: String) {
        scheduledTasks.remove(taskKey)?.cancel()
    }

    private fun graveTaskKey(grave: Grave): String {
        return GraveIdentity.taskKey(grave)
    }
}
