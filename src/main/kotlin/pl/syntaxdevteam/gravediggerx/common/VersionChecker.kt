package pl.syntaxdevteam.gravediggerx.common

import org.bukkit.Bukkit
import pl.syntaxdevteam.core.platform.ServerEnvironment
import pl.syntaxdevteam.gravediggerx.GraveDiggerX

class VersionChecker(private val plugin: GraveDiggerX) {

    companion object {
        private val SUPPORTED_VERSIONS: Set<SemanticVersion> = setOf(
            SemanticVersion(26, 1, 0),
            SemanticVersion(26,2, 0)
        )

        fun isVersionSupported(version: String): Boolean =
            SUPPORTED_VERSIONS.contains(SemanticVersion.parse(version))
    }

    private fun getRawVersion(): String =
        Bukkit.getServer().bukkitVersion

    fun getServerVersion(): String =
        getRawVersion().substringBefore("-")

    fun isSupported(): Boolean =
        isVersionSupported(getServerVersion())

    fun checkAndLog(): Boolean {
        val version = getServerVersion()
        return if (isSupported()) {
            val platformVersion = ServerEnvironment.describeWithVersion(version)
            plugin.logger.success("The server is running on a supported version $platformVersion.")
            true
        } else {
            plugin.logger.warning("Warning! Unsupported version $version – use with caution!")
            false
        }
    }

}
