package pl.syntaxdevteam.gravediggerx.graves.rendering

import com.destroystokyo.paper.profile.PlayerProfile
import org.bukkit.Bukkit
import org.bukkit.block.Skull
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.util.UUID

class SkullProfileResolver(private val plugin: GraveDiggerX) {
    fun apply(skull: Skull, ownerId: UUID, ownerName: String?) {
        val profile = resolve(ownerId, ownerName) ?: return
        if (ModernProfileSupport.trySetProfile(skull, profile)) return

        @Suppress("DEPRECATION")
        skull.setOwningPlayer(Bukkit.getOfflinePlayer(ownerId))
    }

    private fun resolve(ownerId: UUID, ownerName: String?): PlayerProfile? = try {
        Bukkit.createProfile(ownerId, ownerName).apply { complete() }
    } catch (ex: Exception) {
        plugin.logger.warning("Failed to resolve player profile for grave owner $ownerName: ${ex.message}")
        null
    }

    private object ModernProfileSupport {
        private val resolvableProfileClass = try {
            Class.forName("io.papermc.paper.datacomponent.item.ResolvableProfile")
        } catch (_: ClassNotFoundException) {
            null
        }
        private val factory = resolvableProfileClass?.let {
            runCatching { it.getMethod("resolvableProfile", PlayerProfile::class.java) }.getOrNull()
        }
        private val setter = resolvableProfileClass?.let {
            runCatching { Skull::class.java.getMethod("setProfile", it) }.getOrNull()
        }

        fun trySetProfile(skull: Skull, profile: PlayerProfile): Boolean {
            val profileFactory = factory ?: return false
            val profileSetter = setter ?: return false
            return runCatching {
                profileSetter.invoke(skull, profileFactory.invoke(null, profile))
            }.isSuccess
        }
    }
}
