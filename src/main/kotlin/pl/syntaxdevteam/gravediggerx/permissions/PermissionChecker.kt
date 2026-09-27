package pl.syntaxdevteam.gravediggerx.permissions

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player

object PermissionChecker {

    enum class PermissionKey(val node: String) {

        OWNER("gdx.owner"),
        CMD_HELP("gdx.cmd.help"),
        CMD_RELOAD("gdx.cmd.reload"),
        CMD_LIST("gdx.cmd.list"),
        CMD_ADMIN("gdx.cmd.admin"),
        OPEN_GRAVE("gdx.opengrave");

        override fun toString(): String = node
    }

    fun displayName(key: PermissionKey): String = when (key) {
        PermissionKey.OWNER -> "Allows using all GraveDiggerX commands."
        PermissionKey.CMD_HELP -> "Allows viewing the help command."
        PermissionKey.CMD_RELOAD -> "Allows reloading the GraveDiggerX configuration."
        PermissionKey.CMD_LIST -> "Allows listing active graves."
        PermissionKey.CMD_ADMIN -> "Allows using administrative commands."
        PermissionKey.OPEN_GRAVE -> "Allows opening and collecting items from graves."
    }

    fun has(sender: CommandSender, key: PermissionKey): Boolean {
        if (sender is ConsoleCommandSender) return true
        if (sender.isOp) return true
        if (sender.hasPermission("*") ||
            sender.hasPermission("gdx.*") ||
            sender.hasPermission("grx.*") ||
            sender.hasPermission(PermissionKey.OWNER.node) ||
            sender.hasPermission("grx.owner")) return true

        val node = key.node
        val legacyNode = key.node.replace("gdx.", "grx.")

        if (sender.hasPermission(node) || sender.hasPermission(legacyNode)) {
            return true
        }

        return false
    }

    fun hasWithLegacy(sender: CommandSender, key: PermissionKey): Boolean {
        if (sender is ConsoleCommandSender) return true
        if (sender.isOp) return true
        if (sender.hasPermission("*") || sender.hasPermission("gdx.*") || sender.hasPermission("grx.*")) return true

        val legacyKeys = legacyToNew.filterValues { it == key.node }.keys
        for (oldNode in legacyKeys) {
            if (sender.hasPermission(oldNode)) {
                if (sender.hasPermission(key.node)) return true
                val urlTag = "<click:OPEN_URL:https://github.com/SyntaxDevTeam/GraveDiggerX>" +
                        "<blue><u>Click here to view the documentation</u></blue></click>"
                sender.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                        "<yellow>[GraveDiggerX]</yellow> <red>Detected deprecated permission: <gray>$oldNode</gray>\n" +
                                "Please add the new permission: <hover:show_text:'${displayName(key)}'>" +
                                "<yellow>${key.node}</yellow></hover>.\n" +
                                urlTag
                    )
                )
                return true
            }
        }
        return has(sender, key)
    }

    private val legacyToNew = mapOf(
        "grx.owner" to PermissionKey.OWNER.node,
        "grx.cmd.help" to PermissionKey.CMD_HELP.node,
        "grx.cmd.reload" to PermissionKey.CMD_RELOAD.node,
        "grx.cmd.list" to PermissionKey.CMD_LIST.node,
        "grx.cmd.admin" to PermissionKey.CMD_ADMIN.node,
        "grx.opengrave" to PermissionKey.OPEN_GRAVE.node
    )

    fun hasPermissionStartingWith(sender: CommandSender, prefix: String): Boolean {
        if (sender is ConsoleCommandSender) return true
        if (sender.isOp) return true
        if (sender.hasPermission(prefix) || sender.hasPermission("$prefix.*")) return true
        return sender is Player && sender.effectivePermissions.any { it.value && it.permission.startsWith(prefix) }
    }
}