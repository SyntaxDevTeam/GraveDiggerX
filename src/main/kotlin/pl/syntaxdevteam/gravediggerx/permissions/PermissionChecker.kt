package pl.syntaxdevteam.gravediggerx.permissions

import org.bukkit.command.CommandSender

object PermissionChecker {

    enum class PermissionKey(val node: String) {

        OWNER("grx.owner"),
        CMD_HELP("grx.cmd.help"),
        CMD_RELOAD("grx.cmd.reload"),
        CMD_LIST("grx.cmd.list"),
        CMD_ADMIN("grx.cmd.admin"),
        OPEN_GRAVE("grx.opengrave");

        override fun toString(): String = node
    }

    fun has(sender: CommandSender, key: PermissionKey): Boolean {
        if (sender.isOp) return true
        if (sender.hasPermission("*") ||
            sender.hasPermission("grx.*") ||
            sender.hasPermission(PermissionKey.OWNER.node)) return true
        return sender.hasPermission(key.node)
    }

}
