package pl.syntaxdevteam.gravediggerx.integrations

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.RegisteredServiceProvider

object VaultEconomyProvider {

    private var economy: Economy? = null

    fun setupEconomy(): Boolean {
        if (Bukkit.getServer().pluginManager.getPlugin("Vault") == null) {
            return false
        }
        val rsp: RegisteredServiceProvider<Economy>? = Bukkit.getServer().servicesManager.getRegistration(Economy::class.java)
        economy = rsp?.provider
        return economy != null
    }

    private fun getOrUpdateEconomy(): Economy? {
        if (economy == null) {
            setupEconomy()
        }
        return economy
    }

    fun hasEnough(player: OfflinePlayer, amount: Double): Boolean {
        val eco = getOrUpdateEconomy() ?: return false
        return eco.has(player, amount)
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        val eco = getOrUpdateEconomy() ?: return false
        if (amount <= 0.0) return true
        val result = eco.withdrawPlayer(player, amount)
        return result.transactionSuccess()
    }
}