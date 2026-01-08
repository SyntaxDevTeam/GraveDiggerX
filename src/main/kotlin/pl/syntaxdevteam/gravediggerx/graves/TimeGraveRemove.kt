package pl.syntaxdevteam.gravediggerx.graves

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.CancellableTask
import pl.syntaxdevteam.gravediggerx.common.SchedulerProvider
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TimeGraveRemove(private val plugin: GraveDiggerX) {

    private val scheduledTasks = ConcurrentHashMap<UUID, CancellableTask>()

    fun scheduleRemoval(grave: Grave) {
        val totalSeconds = plugin.config.getInt("graves.grave-despawn", 60)

        scheduledTasks[grave.ownerId]?.cancel()

        val runnable = object : BukkitRunnable() {
            var secondsLeft = totalSeconds

            override fun run() {
                val player: Player? = Bukkit.getPlayer(grave.ownerId)

                if (plugin.graveManager.getGraveAt(grave.location) == null) {
                    scheduledTasks.remove(grave.ownerId)
                    cancel()
                    return
                }

                SchedulerProvider.runSync(plugin, Runnable {
                    if (grave.location.block.type != org.bukkit.Material.PLAYER_HEAD) {
                        plugin.graveManager.removeGrave(grave)
                        scheduledTasks.remove(grave.ownerId)
                        cancel()
                        return@Runnable
                    }

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
                        player.sendActionBar(msg)
                    }
                })

                if (secondsLeft <= 0) {
                    SchedulerProvider.runSync(plugin, Runnable {
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
                    })
                    scheduledTasks.remove(grave.ownerId)
                    cancel()
                    return
                }

                secondsLeft--
            }
        }

        scheduledTasks[grave.ownerId] = SchedulerProvider.runAsyncRepeating(plugin, 0L, 20L, runnable)
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
