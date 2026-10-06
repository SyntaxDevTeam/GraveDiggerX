package pl.syntaxdevteam.gravediggerx.graves

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import org.joml.Vector3f
import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import java.util.*

class GraveHologramManager(private val plugin: GraveDiggerX) {

    fun createHologram(location: Location, ownerName: String, time: Int, isPublic: Boolean): List<UUID> {
        val text: Component = buildHologramText(ownerName, time, isPublic)
        val hologramLocation = location.clone().add(0.5, 1.5, 0.5)
        val world = hologramLocation.world ?: return emptyList()

        val textDisplay = world.spawn(hologramLocation, TextDisplay::class.java) { display ->
            display.text(text)
            display.billboard = Display.Billboard.CENTER
            display.isShadowed = false
            display.textOpacity = 255.toByte()
            display.backgroundColor = Color.fromARGB(120, 10, 10, 10)
            display.brightness = Display.Brightness(15, 15)
            display.isSeeThrough = true

            val transform = display.transformation
            transform.scale.set(Vector3f(1.25f, 1.25f, 1.25f))
            display.transformation = transform

            display.persistentDataContainer.set(
                NamespacedKey(plugin, "grave_hologram"),
                PersistentDataType.STRING,
                ownerName
            )
        }

        return listOf(textDisplay.uniqueId)
    }

    fun updateHologramWithTime(grave: Grave, time: Int) {
        grave.hologramIds.forEach { id ->
            val entity = Bukkit.getEntity(id)
            if (entity is TextDisplay) {
                val text: Component = buildHologramText(grave.ownerName, time, grave.isPublic)
                entity.text(text)
            }
        }
    }

    private fun buildHologramText(ownerName: String, time: Int, isPublic: Boolean): Component {
        val key = if (isPublic) "hologram-public" else "hologram"
        return plugin.messageHandler.stringMessageToComponentNoPrefix(
            "graveh",
            key,
            mapOf("player" to ownerName, "time" to TimeFormatter.format(time))
        )
    }
}