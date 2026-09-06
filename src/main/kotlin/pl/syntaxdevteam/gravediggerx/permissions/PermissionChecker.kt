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

        val node = key.node
        val legacyNode = key.node.replace("gdx.", "grx.")

        if (sender.isPermissionSet(node)) {
            return sender.hasPermission(node)
        }
        if (sender.isPermissionSet(legacyNode)) {
            return sender.hasPermission(legacyNode)
        }

        return when (key) {
            PermissionKey.OPEN_GRAVE,
            PermissionKey.CMD_HELP,
            PermissionKey.CMD_LIST -> true
            PermissionKey.OWNER,
            PermissionKey.CMD_ADMIN,
            PermissionKey.CMD_RELOAD -> false
        }
    }

}
