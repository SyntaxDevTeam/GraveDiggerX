package pl.syntaxdevteam.gravediggerx.integrations

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

object VaultEconomyProvider {

    fun hasEnough(player: OfflinePlayer, amount: Double): Boolean {
        try {
            val provider = Bukkit.getServer().servicesManager
                .getRegistration(net.milkbowl.vault.economy.Economy::class.java)?.provider
            if (provider != null) {
                return provider.has(player, amount)
            }
        } catch (_: Exception) {}
        return false
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        try {
            val provider = Bukkit.getServer().servicesManager
                .getRegistration(net.milkbowl.vault.economy.Economy::class.java)?.provider
            if (provider != null) {
                val result = provider.withdrawPlayer(player, amount)
                return result.transactionSuccess()
            }
        } catch (_: Exception) {}
        return false
    }
}