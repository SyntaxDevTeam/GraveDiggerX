package pl.syntaxdevteam.gravediggerx.integrations

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.logging.Logger

object VaultEconomyProvider {

    private val logger: Logger = Logger.getLogger("GraveDiggerX")
    val isAvailable: Boolean
        get() {
            val plugin = Bukkit.getPluginManager().getPlugin("Vault")
            return plugin != null && plugin.isEnabled
        }

    private fun getEconomy(): net.milkbowl.vault.economy.Economy? {
        if (!isAvailable) return null

        val servicesManager = Bukkit.getServer().servicesManager
        val registration = servicesManager.getRegistration(net.milkbowl.vault.economy.Economy::class.java)
        return registration?.provider
            ?: servicesManager.getRegistrations(net.milkbowl.vault.economy.Economy::class.java).firstOrNull()?.provider
    }

    fun hasEnough(player: OfflinePlayer, amount: Double): Boolean {
        if (!isAvailable) return false
        val eco = getEconomy() ?: return false
        return eco.getBalance(player) >= amount
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        if (!isAvailable) return false
        val eco = getEconomy() ?: return false
        val result = eco.withdrawPlayer(player, amount)
        return result.transactionSuccess()
    }

    fun logInit() {
        if (isAvailable && getEconomy() != null) {
            logger.info("The economy integration layer has been initialized successfully.")
        } else {
            logger.info("Vault not found or disabled. Economy features will be disabled.")
        }
    }
}