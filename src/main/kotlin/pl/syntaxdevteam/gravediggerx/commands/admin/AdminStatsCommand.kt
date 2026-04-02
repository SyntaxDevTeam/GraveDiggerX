package pl.syntaxdevteam.gravediggerx.commands.admin

import io.papermc.paper.command.brigadier.CommandSourceStack
import pl.syntaxdevteam.gravediggerx.GraveDiggerX

class AdminStatsCommand(private val plugin: GraveDiggerX) {

    fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        val stuckThresholdMs = plugin.config.getLong("graves.collection.stuck-threshold-ms", 30000L).coerceAtLeast(3000L)
        val stuckCurrent = plugin.databaseHandler.countStuckCollectionTx(stuckThresholdMs)
        plugin.runtimeMetrics.setCollectionTxStuckCurrent(stuckCurrent.toLong())
        val snapshot = plugin.runtimeMetrics.snapshot(plugin.graveManager.activeGravesCount().toLong())

        sender.sendMessage(plugin.messageHandler.stringMessageToComponent("admin", "stats-header"))
        val metrics = listOf(
            "graves_active_total" to snapshot.gravesActiveTotal.toString(),
            "collection_claim_conflict_total" to snapshot.collectionClaimConflictTotal.toString(),
            "collection_tx_stuck_current" to snapshot.collectionTxStuckCurrent.toString(),
            "collection_tx_transition_fail_total" to snapshot.collectionTxTransitionFailTotal.toString(),
            "storage_io_errors_total" to snapshot.storageIoErrorsTotal.toString(),
            "cleanup_duration_ms" to snapshot.cleanupDurationMs.toString(),
            "cleanup_duration_avg_ms" to snapshot.cleanupDurationAvgMs.toString(),
            "cleanup_items_processed_total" to snapshot.cleanupItemsProcessedTotal.toString()
        )
        metrics.forEach { (name, value) ->
            sender.sendMessage(
                plugin.messageHandler.stringMessageToComponent(
                    "admin",
                    "stats-entry",
                    mapOf("metric" to name, "value" to value)
                )
            )
        }

        if (args.size > 2 && args[2].equals("reset", ignoreCase = true)) {
            sender.sendMessage(plugin.messageHandler.stringMessageToComponent("admin", "stats-reset-not-supported"))
        }
    }
}
