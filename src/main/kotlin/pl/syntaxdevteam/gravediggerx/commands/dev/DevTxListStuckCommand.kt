package pl.syntaxdevteam.gravediggerx.commands.dev

import io.papermc.paper.command.brigadier.CommandSourceStack
import pl.syntaxdevteam.gravediggerx.GraveDiggerX

class DevTxListStuckCommand(private val plugin: GraveDiggerX) {

    fun execute(stack: CommandSourceStack) {
        val sender = stack.sender
        val threshold = plugin.config.getLong("graves.collection.stuck-threshold-ms", 30000L).coerceAtLeast(3000L)
        val stuck = plugin.databaseHandler.listStuckCollectionTx(threshold)
        if (stuck.isEmpty()) {
            sender.sendMessage("[GDX][dev] No stuck transactions found.")
            return
        }

        sender.sendMessage("[GDX][dev] Stuck transactions: ${stuck.size}")
        stuck.forEach { tx ->
            sender.sendMessage(
                "- txId=${tx.txId} graveId=${tx.graveId} collectorId=${tx.collectorId} state=${tx.state} updatedAt=${tx.updatedAt} expiresAt=${tx.expiresAt}"
            )
        }
    }
}
