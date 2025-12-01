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
| `/gravediggerx help` | Displays a list of available commands and basic information.| `grx.cmd.help` |
| `/gravediggerx reload` | Reloads the configuration, messages, and refreshes plugin data without restarting the server.| `grx.cmd.reload` |
| `/gravediggerx list` | Shows the owner the coordinates of all their active gravestones.| `grx.cmd.list` |
| `/gravediggerx admin remove <player> <id>` | Allows admin to delete player grave.| `grx.cmd.admin` |
| `/gravediggerx admin list <player>` | Lists all gravestones of a specific player (for administrators).| `grx.cmd.admin` |

Permissions:

| Permission | Description |
| --- | --- |
| `grx.opengrave` | Allows the owner to open the gravestone GUI and collect items using the “Collect All” button. |
| `grx.owner` | Master permission granting full access to all plugin features. |
| `grx.cmd.admin` | Gives access to administrative GraveDiggerX commands. |
| `grx.*` | Wildcard that provides access to all plugin permissions. |

> **Tip:** Operators (OPs) have full access to all plugin features without needing any additional permissions.


## Support and Development
The plugin was designed for survival, hardcore, and RPG servers. Please report suggestions and bugs on the official Discord server or in the project repository so we can continue improving GraveDiggerX.

| SyntaxDevTeam |
| --- |
| ![syntaxdevteam_logo.png](https://raw.githubusercontent.com/SyntaxDevTeam/PunisherX/main/assets/syntaxdevteam_logo.png) |

