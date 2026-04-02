package pl.syntaxdevteam.gravediggerx.commands.dev

import io.papermc.paper.command.brigadier.CommandSourceStack
import java.util.UUID
import pl.syntaxdevteam.gravediggerx.GraveDiggerX

class DevTxUnlockCommand(private val plugin: GraveDiggerX) {

    fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        if (args.size < 5) {
            sender.sendMessage("[GDX][dev] Usage: /gdx dev tx unlock <graveId|txId> <reason>")
            return
        }

        val identifier = args[3]
        val reason = args.drop(4).joinToString(" ").trim()
        if (reason.isBlank()) {
            sender.sendMessage("[GDX][dev] Reason cannot be empty.")
            return
        }
        val actor = sender.name
        val threshold = plugin.config.getLong("graves.collection.stuck-threshold-ms", 30000L).coerceAtLeast(3000L)
        val stuckSnapshot = plugin.databaseHandler.listStuckCollectionTx(threshold)
        val changed = plugin.databaseHandler.unlockCollectionTx(identifier, actor, reason)
        if (changed <= 0) {
            sender.sendMessage("[GDX][dev] No matching stuck tx found for '$identifier'.")
            return
        }

        val parsedId = runCatching { UUID.fromString(identifier) }.getOrNull()
        if (parsedId != null) {
            val byTx = stuckSnapshot.firstOrNull { it.txId == parsedId }?.graveId
            plugin.graveManager.releaseCollectionLockById(byTx ?: parsedId)
        }

        sender.sendMessage("[GDX][dev] Unlocked transactions: $changed")
    }
}
