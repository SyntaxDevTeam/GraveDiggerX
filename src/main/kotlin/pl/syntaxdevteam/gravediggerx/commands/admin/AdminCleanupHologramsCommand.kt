package pl.syntaxdevteam.gravediggerx.commands.admin

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker.PermissionKey

class AdminCleanupHologramsCommand(private val plugin: GraveDiggerX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val sender = stack.sender

        if (!PermissionChecker.has(sender, PermissionKey.CMD_ADMIN)) {
            val message = plugin.messageHandler.stringMessageToComponent("error", "no-permission")
            sender.sendMessage(message)
            return
        }

        val limitPerTick = plugin.config.getInt("performance.cleanup.limit-per-tick", 250).coerceAtLeast(25)
        val started = plugin.graveManager.cleanupOrphanedHologramsBatched(limitPerTick) { removed ->
            val message = plugin.messageHandler.stringMessageToComponent(
                "admin",
                "cleaned-holograms",
                mapOf("count" to removed.toString())
            )
            sender.sendMessage(message)
        }
        if (!started) {
            sender.sendMessage(Component.text("Cleanup już działa, poczekaj aż obecny cykl się zakończy."))
            return
        }
        sender.sendMessage(Component.text("Uruchomiono cleanup hologramów w batchach (limit/tick: $limitPerTick)."))
    }
}
