# Emma-EndInv

Emma-EndInv is the Emma fork of Endless Inventory for Minecraft 26.1.2. It builds for three targets from one Gradle project:

- **Fabric** client/server mod
- **NeoForge** client/server mod
- **Folia** server plugin (Paper/Folia, no client mod required on the server side)

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

Output jars:

```text
java/emma-endinv/fabric/build/libs/endless_inventory-fabric-<mc_version>*.jar
java/emma-endinv/neoforge/build/libs/endless_inventory-neoforge-<mc_version>*.jar
java/emma-endinv/folia/build/libs/endless_inventory-folia-<folia_version>*.jar
```

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
