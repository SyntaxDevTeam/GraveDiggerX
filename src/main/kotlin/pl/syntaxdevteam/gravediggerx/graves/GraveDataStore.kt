package pl.syntaxdevteam.gravediggerx.graves

import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.io.File

class GraveDataStore(plugin: GraveDiggerX, private val onIoError: () -> Unit = {}) {

    private val dataFile = File(plugin.dataFolder, "data.json")
    private val logger = plugin.logger

    init {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
    }

    fun saveAllGraves(graves: Collection<Grave>) {
        try {
            val jsonArray = GraveSerializer.encodeGraves(graves)
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
                onIoError.invoke()
                logger.warning("Atomic write failed for ${dataFile.absolutePath}, fallback rename will be used: ${moveError.message}")
                if (dataFile.exists() && !dataFile.delete()) {
                    onIoError.invoke()
                    logger.warning("Could not delete old grave file before rename: ${dataFile.absolutePath}")
                }
                if (!tmpFile.renameTo(dataFile)) {
                    onIoError.invoke()
                    logger.err("Fallback rename failed while saving graves to ${dataFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            onIoError.invoke()
            logger.err("Failed to save graves to ${dataFile.absolutePath}: ${e.message}")
        }
    }

    fun loadAllGraves(): List<Grave> {
        return try {
            if (!dataFile.exists()) {
                return emptyList()
            }
            val content = dataFile.readText()
            if (content.isEmpty()) {
                return emptyList()
            }

            val decoded = GraveSerializer.decodeGravesFromString(content)
            if (decoded.isEmpty() && content.isNotBlank() && content.trim() != "[]") {
                logger.warning("Failed to decode graves from ${dataFile.absolutePath}; returning empty list.")
            }
            decoded
        } catch (e: Exception) {
            onIoError.invoke()
            logger.err("Failed to load graves from ${dataFile.absolutePath}: ${e.message}")
            emptyList()
        }
    }
}
