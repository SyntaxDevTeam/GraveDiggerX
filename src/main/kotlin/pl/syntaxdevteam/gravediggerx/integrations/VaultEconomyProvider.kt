package pl.syntaxdevteam.gravediggerx.integrations

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.RegisteredServiceProvider

object VaultEconomyProvider {

    private val economy: Economy?
        get() {
            val rsp: RegisteredServiceProvider<Economy>? = Bukkit.getServer().servicesManager.getRegistration(Economy::class.java)
            return rsp?.provider
        }

    fun setupEconomy(): Boolean {
        return Bukkit.getServer().pluginManager.getPlugin("Vault") != null && economy != null
    }

    fun hasEnough(player: OfflinePlayer, amount: Double): Boolean {
        val eco = economy ?: return true // Jeśli brak pluginu ekonomii, traktujemy jako darmowe/zaliczone
        return eco.has(player, amount)
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        val eco = economy ?: return true
        val result = eco.withdrawPlayer(player, amount)
        return result.transactionSuccess()
    }
}