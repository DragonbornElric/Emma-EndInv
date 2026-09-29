# Emma-EndInv

Emma-EndInv is the Emma fork of Endless Inventory for Minecraft 26.1.2. It builds for three targets from one Gradle project:

- **Fabric** client/server mod
- **NeoForge** client/server mod
- **Folia/Paper** server plugin (one jar runs on both Folia and Paper; players use the Fabric or NeoForge client jar)

## Status

> **Work in progress.** The tested, production setup is a **Folia server running the Folia
> plugin, with players on the Fabric client jar**. The same plugin also runs on **Paper**
> (tested). Fabric and NeoForge dedicated servers are
> still being brought up to parity with the Folia plugin. A Fabric dedicated server has been
> checked (see [Which jar goes where](#which-jar-goes-where)). A NeoForge dedicated server and
> the NeoForge client with a Folia server have not been tested yet. Back up your world before
> trying either of them.

## Minecraft versions

Each Minecraft version has its own branch and its own set of jars. Use the jars built from
the branch that matches your game and server.

| Minecraft | Branch | Fabric | NeoForge | Folia/Paper plugin |
|---|---|---|---|---|
| 26.1.2 | `main` | ✅ client + server | ✅ client + server | ✅ tested in production on Folia, also tested on Paper |
| 26.2 | `mc-26.2` | ✅ builds, server starts | ✅ builds, server starts | 🧪 builds against Folia 26.2 (beta); not tested yet |
| 26.3 | `mc-26.3` | ✅ builds, server starts | ✅ builds, server starts (NeoForge 26.3 is beta) | ✅ Paper 26.3: builds, plugin enables · ⏳ Folia 26.3: **pending testing once Folia releases 26.3** |

**Folia 26.3: pending testing once released.** PaperMC hasn't released Folia for 26.3 yet
([build list](https://fill.papermc.io/v3/projects/folia)). Until it does, the 26.3 plugin is
built against Paper 26.3 and runs on Paper servers. It is written for Folia's threading and is
expected to work on Folia 26.3 too, but that hasn't been tested yet. A daily check
(`.github/workflows/folia-watch.yml`) opens an issue titled "Folia 26.3 is out: port the
Folia plugin to 26.3" as soon as Folia 26.3 ships. Watch this repository's issues, or open one
yourself if you see the release first.

## Features

**Endless Inventory (EndInv).** A per-player storage with no slot limit: each item type is one
entry with a count of up to 2,147,483,647. Press **I** to open it. It is saved in the world's data.

- **Pages:** All items, Block items, Tools, Weapons, Equipment, Food & Potion, Enchanted Books,
  and Bookmark. You can hide pages you don't use.
- **Sorting:** Default, Count, Mod + name, Registry order, or Last modified. The ⇅ button
  reverses the order.
- **Search:** separate terms with spaces; every term must match.
  - `#tag` matches an item tag.
  - `@mod` matches a mod namespace.
  - `*id` matches the full item id.
  - A term with no prefix matches the name or the id.
  - Right-click the search box to clear it.
- **Keys:**
  - **Ctrl + left-click** (click or drag over slots) moves items into or out of EndInv.
  - **A**, while hovering an item, stars it into the Bookmark page.
  - All keys can be rebound under the "Endless Inventory" category.
- **Attached panel:** the EndInv panel also appears beside chests, other containers and the
  player inventory.
  - Its layout, position, size and texture can be set in the settings screen (Shift+click the ⚙ button).
  - A plain click on ⚙ turns the attached panel on or off.
  - **Loot All** moves an open container's contents into EndInv.
  - Server owners can allow or block the panel per menu (`specifiedMenuAttachability`).
- **Auto-pickup:**
  - Block drops, drops from mobs you kill, and items you walk over go straight into EndInv.
  - XP from those goes straight to you.
  - A small pickup list in the bottom-right shows what came in.
  - The first tool, weapon or armour piece of its kind, and anything that stacks onto an item
    you already carry, goes to your normal inventory instead.
  - Each player can turn it on or off with `/autopick on|off|status`. It's saved per player on
    Fabric and NeoForge; on Folia it resets to on after a server restart.
  - Server-wide switch: `EnableAutoPick`, or `/endinv config autoPick <true|false>` (permission
    level 2). When it's off, nobody gets auto-pickup.
- **Crafting from EndInv:** recipe-book autofill takes ingredients from EndInv first. Clearing
  the grid puts items back in EndInv. Items entering EndInv still unlock recipes and advancements.
- **Built-in stations** (buttons on the EndInv screen):
  - Crafting, Furnace, Smoker, Blast Furnace, Brewing Stand, Stonecutter, Grindstone and Smithing Table.
  - Furnaces and brewing keep working after you close the screen, as long as you are online.
- **Shared inventories, Storage Tracker and admin tools:** see the sections below.
- **API for other client code:** `com.emma.endinv.api.EmmaEndInvApi`. It can loot the open
  container into EndInv, swap an EndInv item with a menu slot, and read the Storage Tracker
  index. Emma's bot bridge uses it.

The EndInv screen, attached panel, keys and pickup list are client features, so players need
the Fabric or NeoForge jar on their client. The server side (pickup, drops, recipe book,
stations, storage tracking, commands) works on every server target.

### Configuration

Server settings are in `config/endless_inventory-server.json` (on Folia, in the plugin's data
folder):

| Key | Default | Meaning |
|---|---|---|
| `EndinvCreationMode` | `CREATE_PER_PLAYER` | `CREATE_PER_PLAYER`, `USE_GLOBAL_SHARED` (one EndInv for everyone) or `NONE` |
| `DefaultAttach` | `true` | Show the attached EndInv panel on menus by default |
| `specifiedMenuAttachability.container2attachable` | `[]` | Per-menu overrides, e.g. `"minecraft:generic_9x3:false"`, `"inventory:true"` |
| `EnableAutoPick` | `true` | Auto-pickup into EndInv |
| `defaultEndinvBehavior.MaxStackSize` | `2147483647` | Max count per item type in a new EndInv |
| `defaultEndinvBehavior.EnableInfinity` | `false` | Items that reach the max count become infinite |
| `defaultEndinvBehavior.Accessibility` | `PUBLIC` | Default access level for new EndInvs |
| `defaultEndinvBehavior.ContentTransferMode` | `ALL` | `PART` syncs only the items on screen (for very large EndInvs) |
| `Admins` | `[]` | Admin names/UUIDs; empty means operators with permission level 4 |

Client settings (panel layout, hidden pages, texture) are in
`config/endless_inventory-client.json` and in the in-game settings screen.

## Repository Layout

- `java/emma-endinv/` - multi-loader Gradle project (`common`, `fabric`, `neoforge`, `folia`)
- `java/build_and_deploy.sh` - helper to build the jars and copy them to mod/plugin folders
- `README.md`, `CLAUDE.md` - repository-level documentation

## Requirements

- Java 25
- Windows: run Gradle with `gradlew.bat`
- Git Bash or another Bash-compatible shell if you want to use `java/build_and_deploy.sh`

## Build

From the repository root:

```bash
cd java/emma-endinv
./gradlew.bat :fabric:build :neoforge:build :folia:build
```

Build a single target with `:fabric:build`, `:neoforge:build`, or `:folia:build`.

**Linux / macOS:** use `./gradlew`. `gradle.properties` pins `org.gradle.java.home` to the
Windows PrismLauncher runtime, so override it with any installed JDK 21+ (the Java 25
toolchain for compiling is provisioned by Gradle itself):

```bash
cd java/emma-endinv
./gradlew -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64 :fabric:build
```

Output jars:

```text
java/emma-endinv/fabric/build/libs/endless_inventory-fabric-<mc_version>*.jar
java/emma-endinv/neoforge/build/libs/endless_inventory-neoforge-<mc_version>*.jar
java/emma-endinv/folia/build/libs/endless_inventory-folia-<folia_version>*.jar
```

## Which jar goes where

| Server | Server side | Client side |
|---|---|---|
| Folia or Paper (production) | the Folia plugin jar in `plugins/` (same jar for both) | Fabric or NeoForge jar |
| Fabric dedicated server | **the same Fabric jar** in `mods/` (+ Fabric API) | Fabric jar |
| NeoForge dedicated server | the NeoForge jar in `mods/` (built as a client/server mod; not yet tested on a dedicated server) | NeoForge jar |
| Single player | nothing extra (the client jar runs the integrated server) | Fabric or NeoForge jar |

The Fabric jar is a client **and** server mod. Verified 2026-09-29 on a Fabric 26.1.2
dedicated server (Fabric Loader 0.19.2, Fabric API 0.155.3): the server loads
`endless_inventory 1.3.0`, a Fabric client with the same jar joins,
and Emma's bridge reports `endinv_available: true` and moves a chest's contents into her
EndInv (`@loot`). The rail lab in EmmaMinecraft261 (`tools/rail_lab/`) runs this setup.

**Fabric server behaves like the Folia plugin** (checked on the same lab server, 2026-09-29):

| Feature | Fabric (how) | Checked |
|---|---|---|
| Mob killed by a player: loot-table, custom and equipment drops go to the killer's EndInv, XP straight to the killer | Fabric-only mixins around `LivingEntity.dropAllDeathLoot` (`fabric/mixin/fabric/`) | cow, zombie, skeleton: drops in EndInv, XP gained, nothing left on the ground |
| Walk-over pickup uses the Folia rule (`AutoPickHelper.shouldMoveTo`): an item that stacks onto something carried stays in the inventory | common `ItemEntityPickupMixin` | dirt stacked into the inventory, cobblestone into EndInv |
| Recipe book takes ingredients from EndInv first, clears the grid into EndInv, and a full inventory doesn't block a placement | common `ServerPlaceRecipeMixin` | sticks crafted from EndInv planks; inventory planks untouched |
| EndInv saved every 60 s when changed | `PlayerEvents` server tick | data file rewritten every minute |
| Re-sync after every respawn (also leaving the End) | `ServerPlayerEvents.AFTER_RESPAWN` | not live-tested |

**NeoForge** has the same behaviour, built from NeoForge events in `NeoForgeEvents`:
- `LivingDropsEvent` and `LivingExperienceDropEvent` for death loot and XP.
- A 60 s save on the server tick.
- `PlayerRespawnEvent` for re-sync.
- The same common mixins for pickup and the recipe book.

It compiles, but it hasn't been tested on a server yet.

A player killed by a player keeps vanilla drops on Fabric and NeoForge. The Folia plugin sends them to the
killer's EndInv, because `PlayerDeathEvent` is an `EntityDeathEvent`.

The Fabric pickup has one difference from Folia, and it is deliberate. Paper fires
`EntityPickupItemEvent` only when the player inventory has room. So on Folia, autopick stops
once the inventory is full. On Fabric, pickup still goes to EndInv when the inventory is full.

Known quirk in the shared rule: swords, pickaxes and elytra are compared by item class
(`hasSuch`). In 26.1 these are plain `Item`s, so carrying any plain item (for example dirt)
counts as "already has one". The first sword therefore goes to EndInv too. This happens on
both loaders.

## Deploy Helper

Build all three jars and deploy the Fabric jar to the default PrismLauncher instances (Emma, Elric, CameraBot26.1) and to EmmaAI on emmabrain over ssh:

```bash
cd java
./build_and_deploy.sh
```

Other options:

```bash
./build_and_deploy.sh --folia-mods-dir "/path/to/folia/plugins"
./build_and_deploy.sh --neoforge-mods-dir "/path/to/neoforge/mods"
./build_and_deploy.sh --mods-dir "/path/to/extra/fabric/mods"
./build_and_deploy.sh --skip-neoforge --skip-folia
./build_and_deploy.sh --emmabrain-only
```

Built jars are also copied into `java/dist/`. The Folia jar is staged there but not auto-deployed.

## Shared and Combined Inventories

An Endless Inventory (EndInv) does not have to belong to one player. Several players can use the same EndInv, and the contents of one EndInv can be merged into another.

**Access levels**, set by the owner or an admin:

- **Public** - anyone can use it.
- **Restricted** - only the owner and players on its share list.
- **Private** - only the owner.

**Using a shared inventory:** open the EndInv settings and click **Manage inventories…**. Pick any EndInv you have access to and click **Use this**. From then on your pickups, crafting and the EndInv screen use that inventory, so everyone who selects it shares one pool of items. Your choice is saved in the world's EndInv data and survives restarts, including on Folia.

**Sharing:** the owner (or an admin) types a player name and clicks **Share** / **Unshare** to edit the list used by *Restricted* access.

**Combining inventories** (admin only):

- **Take all** - move every item from the selected EndInv into the one you are using.
- **Move all** - move every item from the selected EndInv into another EndInv you choose.

A group can also have a shared EndInv that no single player owns: `/endinv new public` or `/endinv new restricted` creates one without an owner.

## Storage Tracker

Find items in your chests without opening every one of them.

**Tag a container:** craft a **Storage Tag** (paper + chest, shapeless) and right-click any storage block with it: chests (single or double), barrels, shulker boxes, hoppers, furnaces, and modded blocks that expose a vanilla container. The tag is not used up and takes no slot in the container. Rename the tag in an anvil first to label the container. **Sneak + right-click** stops tracking; only the player who tagged it, or an admin, can do that.

**How it stays current:** every tracked container in a loaded chunk is re-read once a second, so changes from players, hoppers or other mods show up within about a second. Items inside shulker boxes are indexed too. A container that is broken drops out of the index. The index is shared by everyone on the server and saved to `<world>/endinv_storage_index.dat` every minute and on shutdown, on every loader.

**Searching:** open the EndInv screen and click the chest button above the recipe book. The Storage Tracker screen has a search box (same syntax as EndInv search: `#tag`, `@mod`) and two views:

- **By item** - every stored item with its total; select one to see where it is, nearest first, with coordinates, distance and direction. Click a location to copy its coordinates.
- **By container** - every tracked container and its contents, with **Copy coords** and **Stop tracking**.

Players without the client mod (for example vanilla clients on Folia) can use chat commands:

| Command | Purpose |
|---------|---------|
| `/storage find <item>` | Where an item is stored, nearest first |
| `/storage list` | The nearest tracked containers |
| `/storage tag [count]` | Give Storage Tags (permission level 2) |
| `/autopick on\|off\|status` | Turn your own auto-pickup into EndInv on or off |

On Folia the tag interaction runs after protection plugins, so a player cannot read a container inside a claim they could not open.

## Administration

**Who is an admin:** add player names or UUIDs to the `Admins` list in the server config. If that list is empty, operators with permission level 4 are admins.

Admins see every EndInv in the manager screen (marked "(admin)") and can inspect its contents and change its access or share list. They can also run these actions on any inventory:

| Action | Effect |
|--------|--------|
| Take all | Move all items into the admin's own EndInv |
| Move all | Move all items into another EndInv |
| Delete all items | Empty the inventory |
| Delete inventory | Remove the EndInv; players using it get their own back on next use |

Each of these writes a snapshot of the source inventory first, to `<world>/endinv_backup/snapshots/`.

**Commands** (`/endinv`, permission level 2):

| Command | Purpose |
|---------|---------|
| `/endinv backup` | Back up the EndInv data file |
| `/endinv snapshots` | List saved snapshots, newest last |
| `/endinv restore <file>` | Add a snapshot's items back into its inventory, or re-create the inventory if it was deleted |
| `/endinv new public\|restricted\|private` | Create a new EndInv (public/restricted ones have no owner) |
| `/endinv ofIndex` | Show the index of your current EndInv |
| `/endinv ofIndex <i> open` | Open an EndInv by index |
| `/endinv ofIndex <i> setDefault` | Make it your selected EndInv (same as **Use this**) |
| `/endinv ofIndex <i> setOwner` | Make yourself the owner |
| `/endinv ofIndex <i> addWhitelist` / `removeWhitelist` | Add or remove yourself from the share list |
| `/endinv ofIndex <i> setAccessibility public\|restricted\|private` | Change access |
| `/endinv ofIndex <i> remove <forceRemove>` | Delete an EndInv |

Restoring adds items and does not overwrite. If you restore the snapshot taken before a **Take all** or **Move all**, the moved items end up in both places.

**Folia persistence:** Folia's autosave does not write mod saved data. The plugin writes EndInv data itself every 60 seconds when something has changed, and again on shutdown.

## Notes

- This repository contains only the standalone Emma-EndInv mod.
- It does not include the old multi-mod workspace, sibling mods, Python tooling, or deployment automation for other projects.
