[![Build Plugin](https://github.com/SyntaxDevTeam/GraveDiggerX/actions/workflows/buildexplorer.yml/badge.svg?branch=main)](https://github.com/SyntaxDevTeam/GraveDiggerX/actions/workflows/buildexplorer.yml) ![GitHub issues](https://img.shields.io/github/issues/SyntaxDevTeam/GraveDiggerX) ![GitHub last commit](https://img.shields.io/github/last-commit/SyntaxDevTeam/GraveDiggerX) ![GitHub Release Date](https://img.shields.io/github/release-date/SyntaxDevTeam/GraveDiggerX)
![GitHub commits since latest release (branch)](https://img.shields.io/github/commits-since/SyntaxDevTeam/GraveDiggerX/latest/main) [![Hangar Downloads](https://img.shields.io/hangar/dt/GraveDiggerX?style=flat)](https://hangar.papermc.io/SyntaxDevTeam/GraveDiggerX)

# GraveDiggerX

GraveDiggerX is a Paper/Folia plugin (API 1.21) that creates a grave when a player dies, stores inventory + XP, and lets the owner recover items via GUI or quick collect (sneak + click).

[Przejdź do pełnej wiki po polsku](docs/wiki/Home.md) · [Pobierz GraveDiggerX](https://github.com/SyntaxDevTeam/GraveDiggerX/releases)

Na start: [pierwsze kroki](docs/wiki/Pierwsze-kroki.md), [instalacja](docs/wiki/Instalacja.md), [komendy](docs/wiki/Komendy.md), [uprawnienia](docs/wiki/Uprawnienia.md) i [konfiguracja](docs/wiki/Konfiguracja.md).

| In game |
| --- |
| ![gravelook.png](https://raw.githubusercontent.com/SyntaxDevTeam/GraveDiggerX/main/assets/gravelook.png) |

## What the plugin actually does
- **Creates a player-head grave** and stores main inventory (slots 0-35), armor, offhand, XP, location, and owner metadata.
- **Spawns a hologram with countdown** and optionally a ghost spirit (Allay) above the grave.
- **Protects grave blocks** against explosions, fluids, mobs, pistons, and hoppers (config-controlled).
- **Enforces grave access rules**: foreign players are blocked/repelled, owner can recover items using GUI or quick collect.
- **Supports expiration behavior**: `DISAPPEAR`, `DROP_ITEMS`, or `BECOME_PUBLIC`.
- **Keeps grave backups** with admin list/restore commands.
- **Provides orphan cleanup commands** for holograms, spirits, and leftover grave blocks.
- **Integrates with WorldGuard**: if a region has explicit owners/members, grave placement requires player ownership/membership.

## Requirements
- Java **21**.
- **Paper/Folia 1.21.7-26.2** server (project currently built/tested against 26.2 API).
- Optional **WorldGuard 7.x** (soft dependency; plugin can run without it).

## Installation
1. Download the latest release JAR.
2. Place it in your `plugins/` directory.
3. Start the server to generate `config.yml`, `data.json`, and language files.
4. Adjust configuration and restart the server or run `/gravediggerx reload`.

## Configuration (key options)

| Path | Description |
| --- | --- |
| `graves.grave-despawn` | Grave lifetime in seconds. |
| `graves.max-per-player` | Maximum active graves per player. |
| `graves.expiration-action` | Action on expiration: `DISAPPEAR` / `DROP_ITEMS` / `BECOME_PUBLIC`. |
| `graves.collection.claim-ttl-ms` | Collection transaction claim TTL (anti-race-condition protection). |
| `graves.backups.*` | Backup toggle and backup limits (per player / global). |
| `graves.worlds.*` | Enable graves per dimension (`overworld`, `nether`, `end`). |
| `graves.protection.*` | Grave block protections (explosions, fluids, mobs, pistons, hoppers). |
| `spirits.enabled` | Enables/disables grave spirit (Allay). |
| `teleport.enabled` | Enables or disables the grave teleportation feature. |
| `teleport.cost` | Cost required to teleport to a grave (integrated with Vault). |
| `database.type` | Data backend: `json`, `mariadb`, `mysql`, `postgresql`, `sqlite`, `h2`. |
| `database.sql.*` | SQL connection settings for SQL backends. |
| `language` | Message locale selection (`EN`, `PL`; also includes `messages_ru.yml` and `messages_uk.yml`). |
| `update.*` | Update check and auto-download sources (Hangar/GitHub/Modrinth). |
| `stats.enabled` | Anonymous usage statistics toggle. |
| `performance.cleanup.limit-per-tick` | Tick limit for admin orphan cleanup processing. |

## Commands
Main command: `/gravediggerx` (alias: `/gdx`).

| Command | Description | Permission |
| --- | --- | --- |
| `/gravediggerx help` | Show help and command syntax. | `gdx.cmd.help` |
| `/gravediggerx reload` | Reload configuration and messages. | `gdx.cmd.reload` |
| `/gravediggerx list` | Show player's active graves with coordinates. | `gdx.cmd.list` |
| `/gravediggerx tp` | Opens the graphical interface to view and teleport to active graves. | `gdx.cmd.tp` |
| `/gravediggerx admin list <player>` | List active graves for selected player. | `gdx.cmd.admin` |
| `/gravediggerx admin remove <player> <id>` | Remove one grave by list index. | `gdx.cmd.admin` |
| `/gravediggerx admin backup list <player>` | List saved grave backups for selected player. | `gdx.cmd.admin` |
| `/gravediggerx admin backup restore <player> <id>` | Restore one backup as active grave. | `gdx.cmd.admin` |
| `/gravediggerx admin cleanupholograms` | Remove orphan grave holograms. | `gdx.cmd.admin` |
| `/gravediggerx admin cleanupghosts` | Remove orphan ghost spirits (Allays). | `gdx.cmd.admin` |
| `/gravediggerx admin cleanupgraves` | Remove orphan grave blocks. | `gdx.cmd.admin` |

## Permissions

| Permission | Description |
| --- | --- |
| `gdx.opengrave` | Allows opening and collecting items from graves. |
| `gdx.cmd.tp` | Allows viewing the tp GUI. |
| `gdx.cmd.help` | Allows viewing the help command. |
| `gdx.cmd.reload` | Allows reloading the GraveDiggerX configuration. |
| `gdx.cmd.list` | Allows listing active graves. |
| `gdx.cmd.admin` | Allows using administrative commands. |
| `gdx.owner` | Allows using all GraveDiggerX commands. |
| `gdx.*` | Wildcard that provides access to all plugin permissions. |

> OP players are allowed by the runtime permission checker.

> ❤️ **Like the plugin?** If GraveDiggerX helps you out, a quick heart/like on Modrinth would mean the world to us and help the plugin reach a wider audience! Thank you for your support!

## Developer quickstart
```bash
chmod +x gradlew
./gradlew clean build
./gradlew test --console=plain
```

## Support
Please use GitHub Issues or the SyntaxDevTeam Discord to report bugs, request improvements, or propose new features.

| SyntaxDevTeam |
| --- |
| ![syntaxdevteam_logo.png](https://raw.githubusercontent.com/SyntaxDevTeam/PunisherX/main/assets/syntaxdevteam_logo.png) |
