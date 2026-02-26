[![Build Plugin](https://github.com/SyntaxDevTeam/GraveDiggerX/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/SyntaxDevTeam/GraveDiggerX/actions/workflows/build.yml) ![GitHub issues](https://img.shields.io/github/issues/SyntaxDevTeam/GraveDiggerX) ![GitHub last commit](https://img.shields.io/github/last-commit/SyntaxDevTeam/GraveDiggerX) ![GitHub Release Date](https://img.shields.io/github/release-date/SyntaxDevTeam/GraveDiggerX)
![GitHub commits since latest release (branch)](https://img.shields.io/github/commits-since/SyntaxDevTeam/GraveDiggerX/latest/main) [![Hangar Downloads](https://img.shields.io/hangar/dt/GraveDiggerX?style=flat)](https://hangar.papermc.io/SyntaxDevTeam/GraveDiggerX)
# GraveDiggerX

GraveDiggerX is an advanced plugin for Paper servers that automatically creates graves upon a player's death. The plugin secures the player's inventory, experience, and death location, providing clear visual information and easy recovery of lost items.

| In game |
| --- |
| ![gravelook.png](https://raw.githubusercontent.com/SyntaxDevTeam/GraveDiggerX/main/assets/gravelook.png) |

## Key Features
- **Auto Graves** – After the player’s death, their inventory and experience are transferred to a gravestone in the form of the player’s head, accompanied by an informational hologram.
- **Interactive inventory recovery** – The owner can open the gravestone’s GUI or quickly collect its contents by holding the sneak key and clicking on the gravestone.
- **Death site protection** – Gravestones are immune to destruction by explosions, fluids, pistons, hoppers, or mobs, and thieves are repelled by a special effect until the gravestone expires.
- **Guardian spirit** – A glowing Allay appears above the gravestone and remains until the loot is collected or the gravestone expires (can be disabled in the configuration).
- **Automatic cleanup** – Gravestones are removed after a defined period, and the owner receives a countdown in the action bar.
- **Persistent storage** – Gravestone data is saved in `data.json`, ensuring it is not lost after a server restart.

## Requirements and Installation
1. A Paper server version 1.21.10 (or compatible) is required.
2. Download the latest version of GraveDiggerX and place the JAR file in the `plugins/` directory.
3. Start the server to generate configuration and language files.
4. Adjust the configuration as needed, then restart the server or use the `/gravediggerx reload` command.

## Configuration
The most important options are located in the `config.yml` file:

| Path | Description |
| --- | --- |
| `graves.grave-despawn` | Time (in seconds) after which the gravestone is removed. |
| `graves.max-per-player` | Maximum number of active gravestones per player; exceeding this limit prevents new gravestones from being created. |
| `graves.protected-effect-cooldown` | Cooldown time (in milliseconds) for the knockback effect applied to foreign players attempting to loot a fresh gravestone. |
| `graves.protection.*` | Enables/disables protection against explosions, fluids, mobs, pistons, and hopper item extraction. |
| `spirits.enabled` | Controls the appearance of the guardian spirit above the gravestone. |
| `language` | Selects the language file (`EN`/`PL`). You can customize messages in `lang/messages_*.yml`. |
| `update.*` | Automatic update checking and downloading (Hangar/GitHub/Modrinth). |
| `stats.enabled` | Enables anonymous usage statistics collection. |

## Commands and Permissions
The main plugin command is `/gravediggerx` (shortcut `/grx`). The following subcommands are available:

| Command | Description | Permission |
| --- | --- | --- |
| `/gravediggerx help` | Displays the plugin help and available command usage. | `grx.cmd.help` |
| `/gravediggerx reload` | Reloads configuration and language messages without restarting the server. | `grx.cmd.reload` |
| `/gravediggerx list` | Shows the executing player a numbered list of their active graves with coordinates. | `grx.cmd.list` |
| `/gravediggerx admin list <player>` | Lists all active graves for the selected player. | `grx.cmd.admin` |
| `/gravediggerx admin remove <player> <id>` | Removes one grave (by list index) for the selected player. | `grx.cmd.admin` |
| `/gravediggerx admin backup list <player>` | Lists saved grave backups for the selected player. | `grx.cmd.admin` |
| `/gravediggerx admin backup restore <player> <id>` | Restores one backup (by list index) as an active grave. | `grx.cmd.admin` |
| `/gravediggerx admin cleanupholograms` | Removes orphaned grave holograms. | `grx.cmd.admin` |
| `/gravediggerx admin cleanupghosts` | Removes orphaned guardian spirits (ghosts/allays). | `grx.cmd.admin` |
| `/gravediggerx admin cleanupgraves` | Removes orphaned grave blocks tracked by the plugin. | `grx.cmd.admin` |

Permissions:

| Permission | Default | Description |
| --- | --- | --- |
| `grx.cmd.help` | `true` | Permission to use the help command. |
| `grx.cmd.reload` | `op` | Permission to reload plugin configuration and messages. |
| `grx.cmd.list` | `true` | Permission to list your own graves. |
| `grx.cmd.admin` | `op` | Permission to use all admin subcommands. |
| `grx.opengrave` | `true` | Permission to open grave GUI and collect grave contents. |
| `grx.owner` | `false` | Master node that grants all core GraveDiggerX permissions. |
| `grx.*` | `false` | Wildcard node for all GraveDiggerX permissions. |

> **Note:** OP players are always allowed by the runtime permission checker.


## Support and Development
The plugin was designed for survival, hardcore, and RPG servers. Please report suggestions and bugs on the official Discord server or in the project repository so we can continue improving GraveDiggerX.

| SyntaxDevTeam |
| --- |
| ![syntaxdevteam_logo.png](https://raw.githubusercontent.com/SyntaxDevTeam/PunisherX/main/assets/syntaxdevteam_logo.png) |

