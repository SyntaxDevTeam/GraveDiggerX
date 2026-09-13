package pl.syntaxdevteam.gravediggerx.integrations

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.logging.Logger

object VaultEconomyProvider {

    private val logger: Logger = Logger.getLogger("GraveDiggerX")
    private var cachedEconomy: Economy? = null

    private fun getEconomy(): Economy? {
        if (cachedEconomy != null && cachedEconomy!!.isEnabled) {
            return cachedEconomy
        }

        val servicesManager = Bukkit.getServer().servicesManager
        val registration = servicesManager.getRegistration(Economy::class.java)
        cachedEconomy = registration?.provider
            ?: servicesManager.getRegistrations(Economy::class.java).firstOrNull()?.provider

        return cachedEconomy
    }

    fun hasEnough(player: OfflinePlayer, amount: Double): Boolean {
        val eco = getEconomy() ?: run {
            logger.warning("[VaultEco] Brak aktywnego providera ekonomii! Sprawdź czy EssentialsX Economy działa.")
            return false
        }

        val balance = eco.getBalance(player)
        return balance >= amount
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        val eco = getEconomy() ?: return false
        val result = eco.withdrawPlayer(player, amount)
        return result.transactionSuccess()
    }

    fun logInit() {
        if (getEconomy() != null) {
            logger.info("The economy integration layer has been initialized successfully.")
        } else {
            logger.warning("Economy integration enabled in config, but no Vault provider was found.")
        }
    }
}