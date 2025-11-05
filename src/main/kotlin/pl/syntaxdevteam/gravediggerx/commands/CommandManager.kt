package pl.syntaxdevteam.gravediggerx.commands

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.Plugin
import pl.syntaxdevteam.gravediggerx.GraveDiggerX

class CommandManager(private val plugin: GraveDiggerX) {

    fun registerCommands() {
        val manager: LifecycleEventManager<Plugin> = plugin.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands: Commands = event.registrar()
            commands.register(
                "gravediggerx",
                "GraveDiggerX plugin command. Type /gravediggerx help to check available commands",
                GraveDiggerXCommands(plugin)
            )
            commands.register(
                "grx",
                "GraveDiggerX plugin command. Type /gravediggerx help to check available commands",
                GraveDiggerXCommands(plugin)
            )

        }
    }
}