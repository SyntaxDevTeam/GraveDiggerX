package pl.syntaxdevteam.gravediggerx.commands.admin

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.common.SchedulerProvider
import pl.syntaxdevteam.gravediggerx.graves.GraveManager
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker
import pl.syntaxdevteam.gravediggerx.permissions.PermissionChecker.PermissionKey

class AdminBackupRestoreCommand(private val plugin: GraveDiggerX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val sender = stack.sender

        if (!PermissionChecker.has(sender, PermissionKey.CMD_ADMIN)) {
            val message = plugin.messageHandler.stringMessageToComponent("error", "no-permission")
            sender.sendMessage(message)
            return
        }

        if (args.size < 5) {
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

        val index = args[4].toIntOrNull()
        if (index == null || index < 1 || index > backups.size) {
            val msg = plugin.messageHandler.stringMessageToComponent("error", "invalid-id")
            sender.sendMessage(msg)
            return
        }

        val backup = backups[index - 1]
        val location = backup.resolveLocation()
        if (location == null) {
            sender.sendMessage(plugin.messageHandler.stringMessageToComponent("admin", "backup-world-missing"))
            return
        }
        // Console and player commands may originate in another world/region on Folia.
        SchedulerProvider.runSyncAt(plugin, location, Runnable {
            val result = plugin.graveManager.restoreBackup(backup)

            val messageKey = when (result) {
                GraveManager.BackupRestoreResult.SUCCESS -> "backup-restored"
                GraveManager.BackupRestoreResult.LOCATION_OCCUPIED -> "backup-location-occupied"
                GraveManager.BackupRestoreResult.WORLD_MISSING -> "backup-world-missing"
                GraveManager.BackupRestoreResult.FAILED -> "backup-restore-failed"
            }

            val msg = plugin.messageHandler.stringMessageToComponent(
                "admin",
                messageKey,
                mapOf(
                    "player" to playerName, "id" to index.toString(),
                    "world" to (backup.worldName ?: "unknown"),
                    "x" to backup.location.blockX.toString(),
                    "y" to backup.location.blockY.toString(),
                    "z" to backup.location.blockZ.toString(),
                    "time" to plugin.config.getInt("graves.grave-despawn", 60).toString()
                )
            )
            sender.sendMessage(msg)
        })
    }
}
