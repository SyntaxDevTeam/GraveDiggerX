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
            val scheduled = runFoliaAsyncRepeating(plugin, initialDelayTicks, periodTicks, task)
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
            val scheduled = if (world != null) {
                runFoliaRegionRepeating(world, plugin, location, initialDelayTicks, periodTicks, task)
            } else {
                null
            }
            if (scheduled != null) {
                return ReflectiveTask(scheduled)
            }
            val globalScheduled = runFoliaGlobalRepeating(plugin, initialDelayTicks, periodTicks, task)
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
            cancelMethod?.invoke(task)
        }
    }

    private object NoopTask : CancellableTask {
        override fun cancel() = Unit
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
        val method = scheduler.javaClass.methods.firstOrNull {
            it.name == "runAtFixedRate" && it.parameterCount == 4
        } ?: return null
        return method.invoke(scheduler, plugin, Consumer<Any> { task.run() }, initialDelayTicks, periodTicks)
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
        val methodName = if (delayTicks == null) "run" else "runDelayed"
        val method = regionScheduler.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == if (delayTicks == null) 3 else 4
        } ?: return false
        if (delayTicks == null) {
            method.invoke(regionScheduler, plugin, location, Consumer<Any> { task.run() })
        } else {
            method.invoke(regionScheduler, plugin, location, Consumer<Any> { task.run() }, delayTicks)
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
        val method = regionScheduler.javaClass.methods.firstOrNull {
            it.name == "runAtFixedRate" && it.parameterCount == 5
        } ?: return null
        return method.invoke(
            regionScheduler,
            plugin,
            location,
            Consumer<Any> { task.run() },
            initialDelayTicks,
            periodTicks
        )
    }

    private fun getServerScheduler(methodName: String): Any? {
        val server = Bukkit.getServer()
        val method = server.javaClass.methods.firstOrNull { it.name == methodName } ?: return null
        return method.invoke(server)
    }
}
