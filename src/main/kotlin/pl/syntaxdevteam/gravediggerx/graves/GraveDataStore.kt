package pl.syntaxdevteam.gravediggerx.graves

import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.io.File

class GraveDataStore(plugin: GraveDiggerX) {

    private val dataFile = File(plugin.dataFolder, "data.json")

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
            } catch (_: Exception) {
                if (dataFile.exists()) dataFile.delete()
                tmpFile.renameTo(dataFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

            GraveSerializer.decodeGravesFromString(content)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
