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
import pl.syntaxdevteam.gravediggerx.common.ConfigHandler
import pl.syntaxdevteam.gravediggerx.common.CancellableTask
import pl.syntaxdevteam.gravediggerx.common.RuntimeMetrics
import pl.syntaxdevteam.core.platform.ServerEnvironment
import pl.syntaxdevteam.gravediggerx.common.SchedulerProvider
import pl.syntaxdevteam.gravediggerx.common.VersionChecker
import pl.syntaxdevteam.gravediggerx.database.DatabaseHandler
import pl.syntaxdevteam.gravediggerx.graves.GraveManager
import pl.syntaxdevteam.gravediggerx.graves.GraveSerializer
import pl.syntaxdevteam.gravediggerx.graves.TimeGraveRemove
import pl.syntaxdevteam.gravediggerx.listeners.GraveClickListener
import pl.syntaxdevteam.gravediggerx.listeners.GraveDeathListener
import pl.syntaxdevteam.gravediggerx.listeners.GraveProtectionListener
import pl.syntaxdevteam.gravediggerx.spirits.GhostManager

class GraveDiggerX : JavaPlugin() {

    lateinit var messageHandler: MessageHandler
    lateinit var configHandler: ConfigHandler
    lateinit var logger: Logger
    lateinit var statsCollector: StatsCollector
    lateinit var pluginsManager: PluginManagerX
    lateinit var pluginConfig: FileConfiguration
    lateinit var databaseHandler: DatabaseHandler
    lateinit var runtimeMetrics: RuntimeMetrics

    lateinit var graveManager: GraveManager
    lateinit var ghostManager: GhostManager
    lateinit var timeGraveRemove: TimeGraveRemove
    private var healthSummaryTask: CancellableTask? = null
    private var txRecoveryTask: CancellableTask? = null


    override fun onEnable() {
        SyntaxCore.registerUpdateSources(
            GitHubSource("SyntaxDevTeam/GraveDiggerX"),
            ModrinthSource("G6k3MNK0")
        )
        SyntaxCore.init(this, versionType = "paper")
        this.logger = SyntaxCore.logger
        VersionChecker(this).checkAndLog()
        saveDefaultConfig()
        pluginConfig = this.config
        configHandler = ConfigHandler(this)
        configHandler.verifyAndUpdateConfig()
        applySecurityConfig()

        timeGraveRemove = TimeGraveRemove(this)

        SyntaxMessages.initialize(this)
        messageHandler = SyntaxMessages.messages
        pluginsManager = SyntaxCore.pluginManagerx
        runtimeMetrics = RuntimeMetrics()

        graveManager = GraveManager(this)
        ghostManager = GhostManager(this)

        CommandManager(this).registerCommands()

        server.pluginManager.registerEvents(GraveProtectionListener(this), this)
        server.pluginManager.registerEvents(GraveClickListener(this), this)
        server.pluginManager.registerEvents(GraveDeathListener(this), this)
        setupDatabase()

        graveManager.loadGravesFromStorage()
        statsCollector = SyntaxCore.statsCollector
        scheduleHealthSummary()
        scheduleTxRecovery()
        SyntaxCore.updateChecker.checkAsync()
    }

    override fun onDisable() {
        healthSummaryTask?.cancel()
        healthSummaryTask = null
        txRecoveryTask?.cancel()
        txRecoveryTask = null
        graveManager.removeAllGraves()
        databaseHandler.close()
        logger.err(pluginMeta.name + " " + pluginMeta.version + " has been disabled ☹️")
    }

    private fun setupDatabase() {
        databaseHandler = DatabaseHandler(this)
        if (ServerEnvironment.isFoliaBased()) {
            logger.debug("Detected Folia server, using sync database connection handling.")
            databaseHandler.connect()
            databaseHandler.ensureSchema()
            databaseHandler.clearAllCollectionClaims()
        } else if (ServerEnvironment.isPaperBased()) {
            SchedulerProvider.runAsync(this, Runnable {
                databaseHandler.connect()
                databaseHandler.ensureSchema()
                databaseHandler.clearAllCollectionClaims()
            })
        }
    }

    fun applySecurityConfig() {
        val allowLegacy = config.getBoolean("graves.serialization.allow-legacy-base64-deserialization", false)
        GraveSerializer.allowLegacyBase64Deserialize = allowLegacy
        if (allowLegacy) {
            logger.warning("Legacy base64 deserialization is ENABLED. Use only for migration windows due to security risk.")
        }
    }

    private fun scheduleHealthSummary() {
        val intervalTicks = 5L * 60L * 20L
        healthSummaryTask = SchedulerProvider.runAsyncRepeating(this, intervalTicks, intervalTicks, Runnable {
            val stuckThresholdMs = config.getLong("graves.collection.stuck-threshold-ms", 30000L).coerceAtLeast(3000L)
            val stuckCount = databaseHandler.countStuckCollectionTx(stuckThresholdMs)
            runtimeMetrics.setCollectionTxStuckCurrent(stuckCount.toLong())
            val snapshot = runtimeMetrics.snapshot(graveManager.activeGravesCount().toLong())
            logger.debug(
                    "health-summary metrics: " +
                    "graves_active_total=${snapshot.gravesActiveTotal}, " +
                    "collection_claim_conflict_total=${snapshot.collectionClaimConflictTotal}, " +
                    "collection_tx_stuck_current=${snapshot.collectionTxStuckCurrent}, " +
                    "collection_tx_transition_fail_total=${snapshot.collectionTxTransitionFailTotal}, " +
                    "storage_io_errors_total=${snapshot.storageIoErrorsTotal}, " +
                    "cleanup_duration_ms=${snapshot.cleanupDurationMs}, " +
                    "cleanup_duration_avg_ms=${snapshot.cleanupDurationAvgMs}, " +
                    "cleanup_items_processed_total=${snapshot.cleanupItemsProcessedTotal}"
            )
        })
    }

    private fun scheduleTxRecovery() {
        val intervalTicks = config.getLong("graves.collection.recovery-interval-ticks", 200L).coerceAtLeast(20L)
        txRecoveryTask = SchedulerProvider.runAsyncRepeating(this, intervalTicks, intervalTicks, Runnable {
            val changed = databaseHandler.recoverExpiredCollectionTx()
            if (changed > 0) {
                logger.warning("Recovered $changed expired collection transactions to FAILED_RECOVERABLE.")
            }
        })
    }
}
