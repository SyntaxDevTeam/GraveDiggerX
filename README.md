[![Build Plugin](https://github.com/SyntaxDevTeam/GraveDiggerX/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/SyntaxDevTeam/GraveDiggerX/actions/workflows/build.yml) ![GitHub issues](https://img.shields.io/github/issues/SyntaxDevTeam/GraveDiggerX) ![GitHub last commit](https://img.shields.io/github/last-commit/SyntaxDevTeam/GraveDiggerX) ![GitHub Release Date](https://img.shields.io/github/release-date/SyntaxDevTeam/GraveDiggerX)
![GitHub commits since latest release (branch)](https://img.shields.io/github/commits-since/SyntaxDevTeam/GraveDiggerX/latest/main) [![Hangar Downloads](https://img.shields.io/hangar/dt/GraveDiggerX?style=flat)](https://hangar.papermc.io/SyntaxDevTeam/GraveDiggerX)

# GraveDiggerX

GraveDiggerX is a Paper/Folia plugin (API 1.21) that creates a grave when a player dies, stores inventory + XP, and lets the owner recover items via GUI or quick collect (sneak + click).

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
- **Paper/Folia 1.21.x** server (project currently built/tested against 1.21.11 API).
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
| `/gravediggerx help` | Show help and command syntax. | `grx.cmd.help` |
| `/gravediggerx reload` | Reload configuration and messages. | `grx.cmd.reload` |
| `/gravediggerx list` | Show player's active graves with coordinates. | `grx.cmd.list` |
| `/gravediggerx admin list <player>` | List active graves for selected player. | `grx.cmd.admin` |
| `/gravediggerx admin remove <player> <id>` | Remove one grave by list index. | `grx.cmd.admin` |
| `/gravediggerx admin backup list <player>` | List saved grave backups for selected player. | `grx.cmd.admin` |
| `/gravediggerx admin backup restore <player> <id>` | Restore one backup as active grave. | `grx.cmd.admin` |
| `/gravediggerx admin cleanupholograms` | Remove orphan grave holograms. | `grx.cmd.admin` |
| `/gravediggerx admin cleanupghosts` | Remove orphan ghost spirits (Allays). | `grx.cmd.admin` |
| `/gravediggerx admin cleanupgraves` | Remove orphan grave blocks. | `grx.cmd.admin` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `grx.cmd.help` | `true` | Permission for `/gdx help`. |
| `grx.cmd.reload` | `op` | Permission for `/gdx reload`. |
| `grx.cmd.list` | `true` | Permission for `/gdx list`. |
| `grx.cmd.admin` | `op` | Permission for admin commands. |
| `grx.opengrave` | `true` | Open grave GUI and collect grave contents. |
| `grx.owner` | `false` | Master node for core GraveDiggerX permissions. |
| `grx.*` | `false` | Wildcard node for all GraveDiggerX permissions. |

> OP players are allowed by the runtime permission checker.

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
