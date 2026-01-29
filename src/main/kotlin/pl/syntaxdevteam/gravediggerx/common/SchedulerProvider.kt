package pl.syntaxdevteam.gravediggerx.common

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.Plugin
import pl.syntaxdevteam.core.platform.ServerEnvironment
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

interface CancellableTask {
    fun cancel()
}

object SchedulerProvider {
    fun runAsync(plugin: Plugin, task: Runnable) {
        if (ServerEnvironment.isFoliaBased()) {
            if (runFoliaAsyncNow(plugin, task)) {
                return
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
            return
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
    }

    fun runSync(plugin: Plugin, task: Runnable) {
        if (ServerEnvironment.isFoliaBased()) {
            if (runFoliaGlobal(plugin, task)) {
                return
            }
            Bukkit.getScheduler().runTask(plugin, task)
            return
        }
        Bukkit.getScheduler().runTask(plugin, task)
    }

    fun runSyncAt(plugin: Plugin, location: Location, task: Runnable) {
        val world = location.world
        if (ServerEnvironment.isFoliaBased()) {
            val executed = if (world != null) {
                runFoliaRegion(world, plugin, location, task, null)
            } else {
                false
            }
            if (!executed) {
                runFoliaGlobal(plugin, task)
            }
            return
        }
        Bukkit.getScheduler().runTask(plugin, task)
    }

    fun runSyncLaterAt(plugin: Plugin, location: Location, delayTicks: Long, task: Runnable) {
        val world = location.world
        if (ServerEnvironment.isFoliaBased()) {
            val executed = if (world != null) {
                runFoliaRegion(world, plugin, location, task, delayTicks)
            } else {
                false
            }
            if (!executed) {
                runFoliaGlobalDelayed(plugin, delayTicks, task)
            }
            return
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
    }

    fun runAsyncRepeating(
        plugin: Plugin,
        initialDelayTicks: Long,
        periodTicks: Long,
        task: Runnable
    ): CancellableTask {
        if (ServerEnvironment.isFoliaBased()) {
            val foliaInitialDelayTicks = initialDelayTicks.coerceAtLeast(1L)
            val scheduled = runFoliaAsyncRepeating(plugin, foliaInitialDelayTicks, periodTicks, task)
            if (scheduled != null) {
                return ReflectiveTask(scheduled)
            }
            val bukkitTask = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks)
            return BukkitTaskWrapper(bukkitTask)
        }
        val bukkitTask = Bukkit.getScheduler()
            .runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks)
        return BukkitTaskWrapper(bukkitTask)
    }

    fun runSyncRepeatingAt(
        plugin: Plugin,
        location: Location,
        initialDelayTicks: Long,
        periodTicks: Long,
        task: Runnable
    ): CancellableTask {
        val world = location.world
        if (ServerEnvironment.isFoliaBased()) {
            val foliaInitialDelayTicks = initialDelayTicks.coerceAtLeast(1L)
            val scheduled = if (world != null) {
                runFoliaRegionRepeating(world, plugin, location, foliaInitialDelayTicks, periodTicks, task)
            } else {
                null
            }
            if (scheduled != null) {
                return ReflectiveTask(scheduled)
            }
            if (world != null) {
                val fallbackTask = scheduleFoliaRegionRepeatingFallback(
                    world,
                    plugin,
                    location,
                    foliaInitialDelayTicks,
                    periodTicks,
                    task
                )
                if (fallbackTask != null) {
                    return fallbackTask
                }
                return NoopTask
            }
            val globalScheduled = runFoliaGlobalRepeating(plugin, foliaInitialDelayTicks, periodTicks, task)
            if (globalScheduled != null) {
                return ReflectiveTask(globalScheduled)
            }
            return NoopTask
        }
        val bukkitTask = Bukkit.getScheduler()
            .runTaskTimer(plugin, task, initialDelayTicks, periodTicks)
        return BukkitTaskWrapper(bukkitTask)
    }

    private fun ticksToMillis(ticks: Long): Long = ticks * 50L

    private data class BukkitTaskWrapper(private val task: org.bukkit.scheduler.BukkitTask) : CancellableTask {
        override fun cancel() = task.cancel()
    }

    private data class ReflectiveTask(private val task: Any?) : CancellableTask {
        override fun cancel() {
            val cancelMethod = task?.javaClass?.methods?.firstOrNull { it.name == "cancel" && it.parameterCount == 0 }
                ?: return
            try {
                if (!cancelMethod.canAccess(task)) {
                    cancelMethod.isAccessible = true
                }
                cancelMethod.invoke(task)
            } catch (_: Exception) {
                // Ignore reflective cancel failures to avoid breaking grave cleanup.
            }
        }
    }

    private object NoopTask : CancellableTask {
        override fun cancel() = Unit
    }

    private class FoliaRepeatingFallbackTask(
        private val schedule: (Runnable, Long) -> Boolean
    ) : CancellableTask {
        @Volatile
        private var cancelled: Boolean = false

        fun start(initialDelayTicks: Long, periodTicks: Long, task: Runnable): Boolean {
            val runner = object : Runnable {
                override fun run() {
                    if (cancelled) {
                        return
                    }
                    task.run()
                    if (!cancelled) {
                        schedule(this, periodTicks)
                    }
                }
            }
            return schedule(runner, initialDelayTicks)
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private fun runFoliaAsyncNow(plugin: Plugin, task: Runnable): Boolean {
        val scheduler = getServerScheduler("getAsyncScheduler") ?: return false
        val method = scheduler.javaClass.methods.firstOrNull {
            it.name == "runNow" && it.parameterCount == 2
        } ?: return false
        method.invoke(scheduler, plugin, Consumer<Any> { task.run() })
        return true
    }

    private fun runFoliaAsyncRepeating(
        plugin: Plugin,
        initialDelayTicks: Long,
        periodTicks: Long,
        task: Runnable
    ): Any? {
        val scheduler = getServerScheduler("getAsyncScheduler") ?: return null
        val method = scheduler.javaClass.methods.firstOrNull {
            it.name == "runAtFixedRate" && it.parameterCount == 5
        } ?: return null
        return method.invoke(
            scheduler,
            plugin,
            Consumer<Any> { task.run() },
            ticksToMillis(initialDelayTicks),
            ticksToMillis(periodTicks),
            TimeUnit.MILLISECONDS
        )
    }

    private fun runFoliaGlobal(plugin: Plugin, task: Runnable): Boolean {
        val scheduler = getServerScheduler("getGlobalRegionScheduler") ?: return false
        val method = scheduler.javaClass.methods.firstOrNull {
            it.name == "run" && it.parameterCount == 2
        } ?: return false
        method.invoke(scheduler, plugin, Consumer<Any> { task.run() })
        return true
    }

    private fun runFoliaGlobalDelayed(plugin: Plugin, delayTicks: Long, task: Runnable): Boolean {
        val scheduler = getServerScheduler("getGlobalRegionScheduler") ?: return false
        val method = scheduler.javaClass.methods.firstOrNull {
            it.name == "runDelayed" && it.parameterCount == 3
        } ?: return false
        method.invoke(scheduler, plugin, Consumer<Any> { task.run() }, delayTicks)
        return true
    }

    private fun runFoliaGlobalRepeating(
        plugin: Plugin,
        initialDelayTicks: Long,
        periodTicks: Long,
        task: Runnable
    ): Any? {
        val scheduler = getServerScheduler("getGlobalRegionScheduler") ?: return null
        val ticksMethod = scheduler.javaClass.methods.firstOrNull {
            it.name == "runAtFixedRate" && it.parameterCount == 4
        }
        if (ticksMethod != null) {
            return try {
                ticksMethod.invoke(scheduler, plugin, Consumer<Any> { task.run() }, initialDelayTicks, periodTicks)
            } catch (_: IllegalArgumentException) {
                ticksMethod.invoke(scheduler, plugin, Runnable { task.run() }, initialDelayTicks, periodTicks)
            }
        }
        val millisMethod = scheduler.javaClass.methods.firstOrNull {
            it.name == "runAtFixedRate" && it.parameterCount == 5
        } ?: return null
        return try {
            millisMethod.invoke(
                scheduler,
                plugin,
                Consumer<Any> { task.run() },
                ticksToMillis(initialDelayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
            )
        } catch (_: IllegalArgumentException) {
            millisMethod.invoke(
                scheduler,
                plugin,
                Runnable { task.run() },
                ticksToMillis(initialDelayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun runFoliaRegion(
        world: org.bukkit.World,
        plugin: Plugin,
        location: Location,
        task: Runnable,
        delayTicks: Long?
    ): Boolean {
        val regionScheduler = world.javaClass.methods.firstOrNull { it.name == "getRegionScheduler" }?.invoke(world)
            ?: return false
        val regionX = location.blockX shr 4
        val regionZ = location.blockZ shr 4
        if (delayTicks == null) {
            val executeMethod = regionScheduler.javaClass.methods.firstOrNull {
                it.name == "execute" && it.parameterCount == 3
            } ?: regionScheduler.javaClass.methods.firstOrNull {
                it.name == "execute" && it.parameterCount == 5
            }
            if (executeMethod != null) {
                return try {
                    if (executeMethod.parameterCount == 5) {
                        executeMethod.invoke(regionScheduler, plugin, world, regionX, regionZ, task)
                    } else {
                        executeMethod.invoke(regionScheduler, plugin, location, task)
                    }
                    true
                } catch (_: IllegalArgumentException) {
                    false
                }
            }
        }
        val methodName = if (delayTicks == null) "run" else "runDelayed"
        val delayMethod = if (delayTicks == null) {
            regionScheduler.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 3
            } ?: regionScheduler.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 5
            }
        } else {
            regionScheduler.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 4
            } ?: regionScheduler.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 5
            } ?: regionScheduler.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 6
            }
        } ?: return false
        val useWorldParams = delayMethod.parameterTypes.getOrNull(1)?.name == "org.bukkit.World"
        if (delayTicks == null) {
            try {
                if (useWorldParams) {
                    delayMethod.invoke(
                        regionScheduler,
                        plugin,
                        world,
                        regionX,
                        regionZ,
                        Consumer<Any> { task.run() }
                    )
                } else {
                    delayMethod.invoke(regionScheduler, plugin, location, Consumer<Any> { task.run() })
                }
            } catch (_: IllegalArgumentException) {
                if (useWorldParams) {
                    delayMethod.invoke(
                        regionScheduler,
                        plugin,
                        world,
                        regionX,
                        regionZ,
                        Runnable { task.run() }
                    )
                } else {
                    delayMethod.invoke(regionScheduler, plugin, location, Runnable { task.run() })
                }
            }
        } else {
            try {
                if (useWorldParams) {
                    delayMethod.invoke(
                        regionScheduler,
                        plugin,
                        world,
                        regionX,
                        regionZ,
                        Consumer<Any> { task.run() },
                        delayTicks
                    )
                } else if (delayMethod.parameterCount == 4) {
                    delayMethod.invoke(regionScheduler, plugin, location, Consumer<Any> { task.run() }, delayTicks)
                } else {
                    delayMethod.invoke(
                        regionScheduler,
                        plugin,
                        location,
                        Consumer<Any> { task.run() },
                        ticksToMillis(delayTicks),
                        TimeUnit.MILLISECONDS
                    )
                }
            } catch (_: IllegalArgumentException) {
                if (useWorldParams) {
                    delayMethod.invoke(
                        regionScheduler,
                        plugin,
                        world,
                        regionX,
                        regionZ,
                        Runnable { task.run() },
                        delayTicks
                    )
                } else if (delayMethod.parameterCount == 4) {
                    delayMethod.invoke(regionScheduler, plugin, location, Runnable { task.run() }, delayTicks)
                } else {
                    delayMethod.invoke(
                        regionScheduler,
                        plugin,
                        location,
                        Runnable { task.run() },
                        ticksToMillis(delayTicks),
                        TimeUnit.MILLISECONDS
                    )
                }
            }
        }
        return true
    }

    private fun runFoliaRegionRepeating(
        world: org.bukkit.World,
        plugin: Plugin,
        location: Location,
        initialDelayTicks: Long,
        periodTicks: Long,
        task: Runnable
    ): Any? {
        val regionScheduler = world.javaClass.methods.firstOrNull { it.name == "getRegionScheduler" }?.invoke(world)
            ?: return null
        val regionX = location.blockX shr 4
        val regionZ = location.blockZ shr 4
        val ticksMethod = regionScheduler.javaClass.methods.firstOrNull {
            it.name == "runAtFixedRate" && it.parameterCount == 5
        }
        if (ticksMethod != null) {
            return try {
                ticksMethod.invoke(
                    regionScheduler,
                    plugin,
                    location,
                    Consumer<Any> { task.run() },
                    initialDelayTicks,
                    periodTicks
                )
            } catch (_: IllegalArgumentException) {
                ticksMethod.invoke(
                    regionScheduler,
                    plugin,
                    location,
                    Runnable { task.run() },
                    initialDelayTicks,
                    periodTicks
                )
            }
        }
        val worldTicksMethod = regionScheduler.javaClass.methods.firstOrNull {
            it.name == "runAtFixedRate" && it.parameterCount == 7
        }
        if (worldTicksMethod != null) {
            return try {
                worldTicksMethod.invoke(
                    regionScheduler,
                    plugin,
                    world,
                    regionX,
                    regionZ,
                    Consumer<Any> { task.run() },
                    initialDelayTicks,
                    periodTicks
                )
            } catch (_: IllegalArgumentException) {
                worldTicksMethod.invoke(
                    regionScheduler,
                    plugin,
                    world,
                    regionX,
                    regionZ,
                    Runnable { task.run() },
                    initialDelayTicks,
                    periodTicks
                )
            }
        }
        val millisMethod = regionScheduler.javaClass.methods.firstOrNull {
            it.name == "runAtFixedRate" && it.parameterCount == 6
        } ?: return null
        return try {
            millisMethod.invoke(
                regionScheduler,
                plugin,
                location,
                Consumer<Any> { task.run() },
                ticksToMillis(initialDelayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
            )
        } catch (_: IllegalArgumentException) {
            millisMethod.invoke(
                regionScheduler,
                plugin,
                location,
                Runnable { task.run() },
                ticksToMillis(initialDelayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun scheduleFoliaRegionRepeatingFallback(
        world: org.bukkit.World,
        plugin: Plugin,
        location: Location,
        initialDelayTicks: Long,
        periodTicks: Long,
        task: Runnable
    ): CancellableTask? {
        val fallbackTask = FoliaRepeatingFallbackTask { runnable, delay ->
            runFoliaRegion(world, plugin, location, runnable, delay)
        }
        return if (fallbackTask.start(initialDelayTicks, periodTicks, task)) {
            fallbackTask
        } else {
            null
        }
    }

    private fun getServerScheduler(methodName: String): Any? {
        val server = Bukkit.getServer()
        val method = server.javaClass.methods.firstOrNull { it.name == methodName } ?: return null
        return method.invoke(server)
    }
}
