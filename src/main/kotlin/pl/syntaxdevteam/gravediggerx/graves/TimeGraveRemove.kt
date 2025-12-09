package pl.syntaxdevteam.gravediggerx.graves

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TimeGraveRemove(private val plugin: GraveDiggerX) {

    private val scheduledTasks = ConcurrentHashMap<UUID, BukkitTask>()

    fun scheduleRemoval(grave: Grave) {
        val totalSeconds = plugin.config.getInt("graves.grave-despawn", 60)

        scheduledTasks[grave.ownerId]?.cancel()

        val task = object : BukkitRunnable() {
            var secondsLeft = totalSeconds

            override fun run() {
                val player: Player? = Bukkit.getPlayer(grave.ownerId)

                if (plugin.graveManager.getGraveAt(grave.location) == null) {
                    scheduledTasks.remove(grave.ownerId)
                    cancel()
                    return
                }

                Bukkit.getScheduler().runTask(plugin, Runnable {
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
                    Bukkit.getScheduler().runTask(plugin, Runnable {
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
        }.runTaskTimerAsynchronously(plugin, 0L, 20L)

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
