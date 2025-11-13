package pl.syntaxdevteam.gravediggerx

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import pl.syntaxdevteam.core.SyntaxCore
import pl.syntaxdevteam.core.logging.Logger
import pl.syntaxdevteam.core.manager.PluginManagerX
import pl.syntaxdevteam.core.stats.StatsCollector
import pl.syntaxdevteam.core.update.GitHubSource
import pl.syntaxdevteam.core.update.ModrinthSource
import pl.syntaxdevteam.message.MessageHandler
import pl.syntaxdevteam.message.SyntaxMessages
import pl.syntaxdevteam.gravediggerx.commands.CommandManager
import pl.syntaxdevteam.gravediggerx.database.DatabaseHandler
import pl.syntaxdevteam.gravediggerx.graves.GraveManager
import pl.syntaxdevteam.gravediggerx.graves.TimeGraveRemove
import pl.syntaxdevteam.gravediggerx.listeners.GraveClickListener
import pl.syntaxdevteam.gravediggerx.listeners.GraveDeathListener
import pl.syntaxdevteam.gravediggerx.listeners.GraveProtectionListener
import pl.syntaxdevteam.gravediggerx.spirits.GhostManager

class GraveDiggerX : JavaPlugin() {

    lateinit var messageHandler: MessageHandler
    lateinit var logger: Logger
    lateinit var statsCollector: StatsCollector
    lateinit var pluginsManager: PluginManagerX
    lateinit var pluginConfig: FileConfiguration
    lateinit var databaseHandler: DatabaseHandler

    lateinit var graveManager: GraveManager
    lateinit var ghostManager: GhostManager
    lateinit var timeGraveRemove: TimeGraveRemove


    override fun onEnable() {
        SyntaxCore.registerUpdateSources(
            GitHubSource("SyntaxDevTeam/GraveDiggerX"),
            ModrinthSource("")
        )
        SyntaxCore.init(this)
        saveDefaultConfig()
        pluginConfig = this.config
        logger = SyntaxCore.logger
        timeGraveRemove = TimeGraveRemove(this)

        SyntaxMessages.initialize(this)
        messageHandler = SyntaxMessages.messages
        pluginsManager = SyntaxCore.pluginManagerx

        graveManager = GraveManager(this)
        ghostManager = GhostManager(this)

        CommandManager(this).registerCommands()

        server.pluginManager.registerEvents(GraveProtectionListener(this), this)
        server.pluginManager.registerEvents(GraveClickListener(this), this)
        server.pluginManager.registerEvents(GraveDeathListener(this), this)
        setupDatabase()

        graveManager.loadGravesFromStorage()
        statsCollector = SyntaxCore.statsCollector
        SyntaxCore.updateChecker.checkAsync()
    }

    override fun onDisable() {
        timeGraveRemove.cancelAll()
        ghostManager.removeAllGhosts()
        if (this::graveManager.isInitialized) {
            graveManager.flushSavesSync()
        }
        databaseHandler.closeConnection()
    }

    private fun setupDatabase() {
        databaseHandler = DatabaseHandler(this)
        if (server.name.contains("Folia")) {
            logger.debug("Detected Folia server, using sync database connection handling.")
            databaseHandler.openConnection()
            databaseHandler.createTables()
        }else{
            this.server.scheduler.runTaskAsynchronously(this, Runnable {
                databaseHandler.openConnection()
                databaseHandler.createTables()
            })
        }

    }
}