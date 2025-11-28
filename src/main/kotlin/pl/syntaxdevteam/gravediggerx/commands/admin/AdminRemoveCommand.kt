package pl.syntaxdevteam.gravediggerx.commands.admin

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker.PermissionKey

class AdminRemoveCommand(private val plugin: GraveDiggerX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val sender = stack.sender

        if (!PermissionChecker.has(sender, PermissionKey.CMD_ADMIN)) {
            val message = plugin.messageHandler.stringMessageToComponent("error", "no-permission")
            sender.sendMessage(message)
            return
        }

        if (args.size < 4) {
            val msg = plugin.messageHandler.stringMessageToComponent("error", "unknown-command")
            sender.sendMessage(msg)
            return
        }

        val playerName = args[2]
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
        val graves = plugin.graveManager.getGravesFor(offlinePlayer.uniqueId)

        if (graves.isEmpty()) {
            val msg = plugin.messageHandler.stringMessageToComponent("admin", "no-graves-for-player", mapOf("player" to playerName))
            sender.sendMessage(msg)
            return
        }

        val idArg = args[3]
        val index = idArg.toIntOrNull()
        if (index == null || index < 1 || index > graves.size) {
            val msg = plugin.messageHandler.stringMessageToComponent("error", "invalid-id")
            sender.sendMessage(msg)
            return
        }

        val grave = graves[index - 1]
        val removed = plugin.graveManager.removeGraveAt(grave.location)

        if (removed) {
            val msg = plugin.messageHandler.stringMessageToComponent("admin", "removed-grave", mapOf("player" to playerName, "id" to index.toString()))
            sender.sendMessage(msg)
        } else {
            val msg = plugin.messageHandler.stringMessageToComponent("admin", "could-not-remove-grave", mapOf("player" to playerName, "id" to index.toString()))
            sender.sendMessage(msg)
        }
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (args.size == 3) {
            return Bukkit.getOnlinePlayers().map { it.name }
                .filter { it.startsWith(args[2], ignoreCase = true) }
        }
        if (args.size == 4) {
            val player = Bukkit.getOfflinePlayer(args[2])
            val size = plugin.graveManager.getGravesFor(player.uniqueId).size
            val indices = (1..size).map { it.toString() }
            return indices.filter { it.startsWith(args[3], ignoreCase = true) }
        }
        return emptyList()
    }
}