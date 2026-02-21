package pl.syntaxdevteam.gravediggerx.commands.admin

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker.PermissionKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AdminBackupListCommand(private val plugin: GraveDiggerX) : BasicCommand {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

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

        val playerName = args[3]
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
        val backups = plugin.graveManager.getBackupsFor(offlinePlayer.uniqueId)

        if (backups.isEmpty()) {
            val msg = plugin.messageHandler.stringMessageToComponent(
                "admin",
                "no-backups-for-player",
                mapOf("player" to playerName)
            )
            sender.sendMessage(msg)
            return
        }

        val header = plugin.messageHandler.stringMessageToComponent(
            "admin",
            "backup-list-header",
            mapOf("player" to playerName)
        )
        sender.sendMessage(header)

        backups.forEachIndexed { index, backup ->
            val loc = backup.location
            val placeholders = mapOf(
                "index" to (index + 1).toString(),
                "world" to (loc.world?.name ?: "unknown"),
                "x" to loc.blockX.toString(),
                "y" to loc.blockY.toString(),
                "z" to loc.blockZ.toString(),
                "time" to formatter.format(Instant.ofEpochMilli(backup.createdAt))
            )
            val line = plugin.messageHandler.stringMessageToComponent("admin", "backup-list-entry", placeholders)
            sender.sendMessage(line)
        }
    }
}
