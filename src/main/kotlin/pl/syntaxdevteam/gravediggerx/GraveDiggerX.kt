package pl.syntaxdevteam.gravediggerx

import org.bukkit.plugin.java.JavaPlugin
import pl.syntaxdevteam.message.MessageHandler
import pl.syntaxdevteam.message.SyntaxMessages
import pl.syntaxdevteam.gravediggerx.commands.CommandManager
import pl.syntaxdevteam.gravediggerx.graves.GraveManager
import pl.syntaxdevteam.gravediggerx.graves.TimeGraveRemove
import pl.syntaxdevteam.gravediggerx.listeners.GraveClickListener
import pl.syntaxdevteam.gravediggerx.listeners.GraveDeathListener
import pl.syntaxdevteam.gravediggerx.listeners.GraveProtectionListener
import pl.syntaxdevteam.gravediggerx.spirits.GhostManager

class GraveDiggerX : JavaPlugin() {

    lateinit var messageHandler: MessageHandler
        private set
    lateinit var graveManager: GraveManager
    lateinit var ghostManager: GhostManager

    lateinit var instance: GraveDiggerX
        private set

    lateinit var timeGraveRemove: TimeGraveRemove


    override fun onEnable() {
        instance = this
        timeGraveRemove = TimeGraveRemove(this)

        saveDefaultConfig()

        SyntaxMessages.initialize(this)
        messageHandler = SyntaxMessages.messages

        graveManager = GraveManager(this)
        ghostManager = GhostManager(this)

        CommandManager(this).registerCommands()

        server.pluginManager.registerEvents(GraveProtectionListener(this), this)
        server.pluginManager.registerEvents(GraveClickListener(this), this)
        server.pluginManager.registerEvents(GraveDeathListener(this), this)

    }

    override fun onDisable() {
        timeGraveRemove.cancelAll()
        ghostManager.removeAllGhosts()
    }
}