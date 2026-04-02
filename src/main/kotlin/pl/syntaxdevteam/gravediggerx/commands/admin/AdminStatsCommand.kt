package pl.syntaxdevteam.gravediggerx.commands.admin

import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import pl.syntaxdevteam.gravediggerx.GraveDiggerX

class AdminStatsCommand(private val plugin: GraveDiggerX) {

    fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        val stuckThresholdMs = plugin.config.getLong("graves.collection.stuck-threshold-ms", 30000L).coerceAtLeast(3000L)
        val stuckNow = plugin.databaseHandler.countStuckCollectionTx(stuckThresholdMs)
        plugin.runtimeMetrics.setCollectionTxStuckCurrent(stuckNow.toLong())
        val snapshot = plugin.runtimeMetrics.snapshot(plugin.graveManager.activeGravesCount().toLong())

        sender.sendMessage(Component.text("=== GraveDiggerX runtime metrics ==="))
        sender.sendMessage(Component.text("graves_active_total=${snapshot.gravesActiveTotal}"))
        sender.sendMessage(Component.text("collection_claim_conflict_total=${snapshot.collectionClaimConflictTotal}"))
        sender.sendMessage(Component.text("collection_tx_stuck_total=${snapshot.collectionTxStuckTotal}"))
        sender.sendMessage(Component.text("collection_tx_transition_fail_total=${snapshot.collectionTxTransitionFailTotal}"))
        sender.sendMessage(Component.text("collection_tx_stuck_current=$stuckNow"))
        sender.sendMessage(Component.text("storage_io_errors_total=${snapshot.storageIoErrorsTotal}"))
        sender.sendMessage(Component.text("cleanup_duration_ms=${snapshot.cleanupDurationMs}"))
        sender.sendMessage(Component.text("cleanup_duration_avg_ms=${snapshot.cleanupDurationAvgMs}"))
        sender.sendMessage(Component.text("cleanup_items_processed_total=${snapshot.cleanupItemsProcessedTotal}"))

        if (args.size > 2 && args[2].equals("reset", ignoreCase = true)) {
            sender.sendMessage(Component.text("Uwaga: reset metryk nie jest jeszcze wspierany."))
        }
    }
}
