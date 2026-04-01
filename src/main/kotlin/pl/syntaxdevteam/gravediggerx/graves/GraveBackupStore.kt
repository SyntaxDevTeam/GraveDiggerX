package pl.syntaxdevteam.gravediggerx.graves

import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.io.File

class GraveBackupStore(plugin: GraveDiggerX) {

    private val dataFile = File(plugin.dataFolder, "backups.json")
    private val logger = plugin.logger

    init {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
    }

    fun saveAllBackups(backups: Collection<GraveBackup>) {
        try {
            val jsonArray = GraveBackupSerializer.encodeBackups(backups)
            val jsonText = GraveSerializer.gson.toJson(jsonArray)
            val tmpFile = File(dataFile.parentFile, dataFile.name + ".tmp")
            tmpFile.bufferedWriter().use { writer ->
                writer.write(jsonText)
                writer.flush()
            }

            try {
                val source = tmpFile.toPath()
                val target = dataFile.toPath()
                java.nio.file.Files.move(
                    source,
                    target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (moveError: Exception) {
                logger.warning("Atomic write failed for ${dataFile.absolutePath}, fallback rename will be used: ${moveError.message}")
                if (dataFile.exists() && !dataFile.delete()) {
                    logger.warning("Could not delete old backup file before rename: ${dataFile.absolutePath}")
                }
                if (!tmpFile.renameTo(dataFile)) {
                    logger.err("Fallback rename failed while saving backups to ${dataFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            logger.err("Failed to save backups to ${dataFile.absolutePath}: ${e.message}")
        }
    }

    fun loadAllBackups(): List<GraveBackup> {
        return try {
            if (!dataFile.exists()) {
                return emptyList()
            }
            val content = dataFile.readText()
            if (content.isEmpty()) {
                return emptyList()
            }

            val decoded = GraveBackupSerializer.decodeBackupsFromString(content)
            if (decoded.isEmpty() && content.isNotBlank() && content.trim() != "[]") {
                logger.warning("Failed to decode backups from ${dataFile.absolutePath}; returning empty list.")
            }
            decoded
        } catch (e: Exception) {
            logger.err("Failed to load backups from ${dataFile.absolutePath}: ${e.message}")
            emptyList()
        }
    }
}
