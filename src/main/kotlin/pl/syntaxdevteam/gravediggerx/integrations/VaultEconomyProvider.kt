package pl.syntaxdevteam.gravediggerx.integrations

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.RegisteredServiceProvider

object VaultEconomyProvider {
    var economy: Economy? = null
        private set

    fun setupEconomy(): Boolean {
        if (Bukkit.getServer().pluginManager.getPlugin("Vault") == null) {
            return false
        }
        val rsp: RegisteredServiceProvider<Economy>? = Bukkit.getServer().servicesManager.getRegistration(Economy::class.java)
        if (rsp != null) {
            economy = rsp.provider
        }
        return economy != null
    }

    fun hasEnough(player: OfflinePlayer, amount: Double): Boolean {
        val eco = economy ?: return false
        return eco.has(player, amount)
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        val eco = economy ?: return false
        val result = eco.withdrawPlayer(player, amount)
        return result.transactionSuccess()
    }
}