package pl.syntaxdevteam.gravediggerx.permissions

import org.bukkit.command.CommandSender

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

    fun has(sender: CommandSender, key: PermissionKey): Boolean {
        if (sender.isOp) return true
        if (sender.hasPermission("*") ||
            sender.hasPermission("gdx.*") ||
            sender.hasPermission("grx.*") ||
            sender.hasPermission(PermissionKey.OWNER.node) ||
            sender.hasPermission("grx.owner")) return true
        return sender.hasPermission(key.node) || sender.hasPermission(key.node.replace("gdx.", "grx."))
    }

}
