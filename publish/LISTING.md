# Store listings: Modrinth and CurseForge

Copy-paste text and settings for publishing **Emma's Endless Inventory**. Jars come from the
[GitHub releases](https://github.com/DragonbornElric/Emma-EndInv/releases).

## Project settings (both sites)

| Field | Value |
|---|---|
| Name | Emma's Endless Inventory |
| Slug / URL | `emmas-endless-inventory` |
| Author | Elric Heart |
| Summary (≤ 120 chars) | Endless storage with auto-pickup, crafting from storage, built-in stations and a chest tracker. Fabric, NeoForge, Folia/Paper. |
| License | MIT |
| Source code | https://github.com/DragonbornElric/Emma-EndInv |
| Issue tracker | https://github.com/DragonbornElric/Emma-EndInv/issues |
| Wiki / docs | https://github.com/DragonbornElric/Emma-EndInv#readme |
| Support | GitHub issues, or ask on the livestream (twitch.tv/Elric_Heart); no Discord |
| Icon | `publish/logo.png` (512×512; the mod icon uses the same art) |
| Environment | Client **and** server required |
| Categories | Storage, Utility, Management; also Game Mechanics on Modrinth |

**Modrinth:** one project that holds all three jars. Tag each file with its loaders.

**CurseForge:** two projects:
- *Minecraft → Mods* for the Fabric and NeoForge jars.
- *Bukkit Plugins* for the Folia/Paper jar.

Link each one to the other in its description.

## Files per version

| Minecraft | File | Modrinth loaders | CurseForge project / loader |
|---|---|---|---|
| 26.1.2 | `emma_endinv-fabric-26.1.2-<ver>.jar` | Fabric | Mods / Fabric |
| 26.1.2 | `emma_endinv-neoforge-26.1.2-<ver>.jar` | NeoForge | Mods / NeoForge |
| 26.1.2 | `emma_endinv-folia-26.1.2-<ver>.jar` | Paper, Folia | Bukkit Plugins |
| 26.2 | `emma_endinv-fabric-26.2-<ver>.jar` | Fabric | Mods / Fabric |
| 26.2 | `emma_endinv-neoforge-26.2-<ver>.jar` | NeoForge | Mods / NeoForge |
| 26.2 | `emma_endinv-folia-26.2-<ver>.jar` | Paper, Folia | Bukkit Plugins |
| 26.3 | `emma_endinv-fabric-26.3-<ver>.jar` | Fabric | Mods / Fabric |
| 26.3 | `emma_endinv-neoforge-26.3-<ver>.jar` | NeoForge | Mods / NeoForge |
| 26.3 | `emma_endinv-folia-26.3-<ver>.jar` | Paper only (Folia 26.3 pending) | Bukkit Plugins |

Version number on the site: `<ver>+<mc>`, e.g. `1.4.1+26.2`. Release channel: Release.

Dependencies:
- Fabric: **Fabric API**, required.
- Tell players they need the mod on the client too. The Folia/Paper plugin is useful with the
  Fabric or NeoForge client jar; vanilla clients only get the server-side features.

## Description (Modrinth body; CurseForge mod project)

```markdown
# Emma's Endless Inventory

**Endless per-player storage for Minecraft 26.x**: every item type is one entry with a count of up to 2,147,483,647. Press **I** to open it.

Runs on **Fabric** and **NeoForge** (client + server), and as a **Folia/Paper** server plugin that works with Fabric or NeoForge clients.

## Features
- **Endless Inventory** – pages (blocks, tools, weapons, equipment, food & potions, enchanted books, bookmarks), sorting, search (`#tag`, `@mod`, `*id`, `minecraft:item`)
- **Attached panel** – EndInv appears beside chests and your inventory, with **Loot All**
- **Auto-pickup** – block drops, loot from mobs you kill, and items you walk over go straight into EndInv (XP to you). Per player: `/autopick on|off|status`
- **Crafting from storage** – the recipe book fills crafting tables and furnaces from EndInv first
- **Built-in stations** – crafting table, furnace, smoker, blast furnace, brewing stand, stonecutter, grindstone and smithing table, straight from EndInv; furnaces and brewing keep running while you're online
- **Storage Tracker** – tag your chests with a Storage Tag and search where everything is (`/storage find <item>`)
- **Shared inventories** – public / restricted / private EndInvs, sharing, admin tools, snapshots and restore

## Installation
Install on **both sides**:

| Server | Server installs | Players install |
|---|---|---|
| Folia or Paper | the plugin jar in `plugins/` | Fabric (+ Fabric API) or NeoForge jar |
| Fabric | the Fabric jar + Fabric API | the same Fabric jar + Fabric API |
| NeoForge | the NeoForge jar | the same NeoForge jar |
| Single player | – | Fabric or NeoForge jar |

Minecraft and mod versions must match on server and clients.

## Tested
Every version is tested in-game on dedicated Folia, Paper, Fabric and NeoForge servers before release. See [TESTING.md](https://github.com/DragonbornElric/Emma-EndInv/blob/main/TESTING.md).

## Support
Need help? **Open an issue on [GitHub](https://github.com/DragonbornElric/Emma-EndInv/issues)**, or catch the livestream at **[twitch.tv/Elric_Heart](https://www.twitch.tv/Elric_Heart)** and ask. Comments here aren't monitored for support.

## Credits
A fork of **[Endless Inventory](https://modrinth.com/mod/endless-inventory)** by Kay Zhang (kwwsyk). This fork ports it to Minecraft 26.x and adds the Folia/Paper plugin, shared inventories, the storage tracker and admin tools. Please report problems with this fork on [GitHub](https://github.com/DragonbornElric/Emma-EndInv/issues), not to the original mod.

MIT licensed. Built live on stream: **[twitch.tv/Elric_Heart](https://www.twitch.tv/Elric_Heart)**
```

## Description (CurseForge Bukkit plugin project)

```markdown
# Emma's Endless Inventory – Folia/Paper plugin

Server plugin for **Emma's Endless Inventory**: endless per-player storage, auto-pickup, kill loot and XP to the player, crafting from storage, built-in stations, shared inventories and a chest storage tracker. Runs on **Folia** and **Paper** (26.1.2, 26.2; 26.3 on Paper).

**Players need the client mod** (Fabric or NeoForge) for the Endless Inventory screen: [Emma's Endless Inventory mod](<link to the CurseForge mod project>). Players without it can still use `/storage` and `/autopick`.

Tested in-game on Folia and Paper servers before every release: [TESTING.md](https://github.com/DragonbornElric/Emma-EndInv/blob/main/TESTING.md).

**Need help?** Open an issue on [GitHub](https://github.com/DragonbornElric/Emma-EndInv/issues) or ask on the livestream at [twitch.tv/Elric_Heart](https://www.twitch.tv/Elric_Heart).

A fork of Endless Inventory by Kay Zhang (kwwsyk). MIT licensed. Source and issues: [GitHub](https://github.com/DragonbornElric/Emma-EndInv). Built live on stream: [twitch.tv/Elric_Heart](https://www.twitch.tv/Elric_Heart)
```

## Changelog for 1.4.1 (per file)

```markdown
Fixed (found by in-game tests on dedicated servers):
- Folia/Paper plugin: walk-over auto-pickup duplicated items (EndInv and inventory)
- Folia/Paper plugin: loot from mobs you kill stayed on the ground
- NeoForge: the I key didn't open the Endless Inventory; settings sync error; NeoForge clients can now join Folia/Paper servers
- `/storage find minecraft:<item>` now matches full item ids
- The stack held on the cursor is drawn on top of the Endless Inventory panels
Upgrading from 1.3: delete the old `endless_inventory-…jar` (mod id is now `emma_endinv`); your data carries over.
```
