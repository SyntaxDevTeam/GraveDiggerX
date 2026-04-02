package pl.syntaxdevteam.gravediggerx.commands.admin

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker.PermissionKey

class AdminCleanupGravesCommand(private val plugin: GraveDiggerX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val sender = stack.sender

        if (!PermissionChecker.has(sender, PermissionKey.CMD_ADMIN)) {
            val message = plugin.messageHandler.stringMessageToComponent("error", "no-permission")
            sender.sendMessage(message)
            return
        }

        val limitPerTick = plugin.config.getInt("performance.cleanup.limit-per-tick", 250).coerceAtLeast(25)
        val started = plugin.graveManager.cleanupOrphanedGravesBatched(limitPerTick) { removed ->
            val message = plugin.messageHandler.stringMessageToComponent(
                "admin",
                "cleaned-graves",
                mapOf("count" to removed.toString())
            )
            sender.sendMessage(message)
        }
        if (!started) {
            sender.sendMessage(plugin.messageHandler.stringMessageToComponent("admin", "cleanup-already-running"))
            return
        }
        sender.sendMessage(
            plugin.messageHandler.stringMessageToComponent(
                "admin",
                "cleanup-graves-started",
                mapOf("limit" to limitPerTick.toString())
            )
        )
    }
}
