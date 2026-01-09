package pl.syntaxdevteam.gravediggerx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.ReloadPlugin
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker.PermissionKey
import pl.syntaxdevteam.gravediggerx.commands.admin.AdminCleanupHologramsCommand
import pl.syntaxdevteam.gravediggerx.commands.admin.AdminRemoveCommand

class GraveDiggerXCommands(private val plugin: GraveDiggerX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val sender = stack.sender

        if (args.isEmpty()) {
            val message = plugin.messageHandler.stringMessageToComponent("error", "unknown-command")
            sender.sendMessage(message)
            return
        }

        when (args[0].lowercase()) {
            "help" -> sendHelpDesk(stack)
            "reload" -> sendReload(stack)
            "list" -> sendList(stack)
            "admin" -> sendAdmin(stack, args)
            else -> {
                val message = plugin.messageHandler.stringMessageToComponent("error", "unknown-command")
                sender.sendMessage(message)
            }
        }
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (args.isEmpty() || args[0].isBlank()) {
            return listOf("help", "reload", "list", "admin")
        }

        if (args.size == 1) {
            return listOf("help", "reload", "list", "admin")
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.size == 2 && args[0].equals("admin", ignoreCase = true)) {
            val subcommands = listOf("list", "remove", "cleanupholograms")
            return subcommands.filter { it.startsWith(args[1], ignoreCase = true) }
        }

        if (args.size == 3 && args[0].equals("admin", ignoreCase = true)) {
            return when {
                args[1].equals("list", ignoreCase = true) -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[2], ignoreCase = true) }
                args[1].equals("remove", ignoreCase = true) -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }
        }

        if (args.size == 4 && args[0].equals("admin", ignoreCase = true) && args[1].equals("remove", ignoreCase = true)) {
            val player = Bukkit.getOfflinePlayerIfCached(args[2]) ?: return emptyList()
            val size = plugin.graveManager.getGravesFor(player.uniqueId).size
            val indices = (1..size).map { it.toString() }
            return indices.filter { it.startsWith(args[3], ignoreCase = true) }
        }

        return emptyList()
    }

    private fun sendHelpDesk(stack: CommandSourceStack) {
        val sender = stack.sender

        if (!PermissionChecker.has(sender, PermissionKey.CMD_HELP)) {
            val message = plugin.messageHandler.stringMessageToComponent("error", "no-permission")
            sender.sendMessage(message)
            return
        }

        val helpLines: List<Component> = plugin.messageHandler.getSmartMessage("help", "info", emptyMap())
        helpLines.forEach { sender.sendMessage(it) }
    }

    private fun sendReload(stack: CommandSourceStack) {
        val sender = stack.sender

        if (!PermissionChecker.has(sender, PermissionKey.CMD_RELOAD)) {
            val message = plugin.messageHandler.stringMessageToComponent("error", "no-permission")
            sender.sendMessage(message)
            return
        }
        // TODO: Add reloading database connection
        plugin.reloadConfig()
        ReloadPlugin(plugin).reloadAll()
        plugin.messageHandler.reloadMessages()

        val message = plugin.messageHandler.stringMessageToComponent("reload", "success")
        sender.sendMessage(message)
    }

    private fun sendList(stack: CommandSourceStack) {
        val sender = stack.sender
        if (sender !is Player) {
            val msg = plugin.messageHandler.stringMessageToComponent("graves", "only-player", emptyMap())
            sender.sendMessage(msg)
            return
        }

        if (!PermissionChecker.has(sender, PermissionKey.CMD_LIST)) {
            val msg = plugin.messageHandler.stringMessageToComponent("error", "no-permission")
            sender.sendMessage(msg)
            return
        }

        val graves = plugin.graveManager.getGravesFor(sender.uniqueId)
        if (graves.isEmpty()) {
            val msg = plugin.messageHandler.stringMessageToComponent("graves", "no-graves", emptyMap())
            sender.sendMessage(msg)
            return
        }

        val header = plugin.messageHandler.stringMessageToComponent("graves", "locate-header", emptyMap())
        sender.sendMessage(header)

        graves.forEachIndexed { index: Int, grave: Grave ->
            val loc = grave.location
            val placeholders = mapOf(
                "index" to (index + 1).toString(),
                "world" to (loc.world?.name ?: "unknown"),
                "x" to loc.blockX.toString(),
                "y" to loc.blockY.toString(),
                "z" to loc.blockZ.toString()
            )
            val line = plugin.messageHandler.stringMessageToComponent("graves", "locate-entry", placeholders)
            sender.sendMessage(line)
        }
    }

    private fun sendAdmin(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender

        if (!PermissionChecker.has(sender, PermissionKey.CMD_ADMIN)) {
            val message = plugin.messageHandler.stringMessageToComponent("error", "no-permission")
            sender.sendMessage(message)
            return
        }

        if (args.size < 3) {
            val msg = plugin.messageHandler.stringMessageToComponent("error", "unknown-command")
            sender.sendMessage(msg)
            return
        }

        when (args[1].lowercase()) {
            "list" -> sendAdminList(stack, args)
            "remove" -> AdminRemoveCommand(plugin).execute(stack, args)
            "cleanupholograms" -> AdminCleanupHologramsCommand(plugin).execute(stack, args)
            else -> {
                val message = plugin.messageHandler.stringMessageToComponent("error", "unknown-command")
                sender.sendMessage(message)
            }
        }
    }

    private fun sendAdminList(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender
        val playerName = args[2]

        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)

        val graves = plugin.graveManager.getGravesFor(offlinePlayer.uniqueId)
        if (graves.isEmpty()) {
            val msg = plugin.messageHandler.stringMessageToComponent("admin", "no-graves-for-player", mapOf("player" to playerName))
            sender.sendMessage(msg)
            return
        }

        val header = plugin.messageHandler.stringMessageToComponent("admin", "list-header", mapOf("player" to playerName))
        sender.sendMessage(header)

        graves.forEachIndexed { index: Int, grave: Grave ->
            val loc = grave.location
            val placeholders = mapOf(
                "index" to (index + 1).toString(),
                "world" to (loc.world?.name ?: "unknown"),
                "x" to loc.blockX.toString(),
                "y" to loc.blockY.toString(),
                "z" to loc.blockZ.toString()
            )
            val line = plugin.messageHandler.stringMessageToComponent("admin", "list-entry", placeholders)
            sender.sendMessage(line)
        }
    }

}
