# Testing

In-game functional tests of Emma-EndInv on dedicated servers. Latest run: **2026-09-29, mod
version 1.4.1**.

## Setup

- **Servers:** 11 dedicated [Pelican](https://pelican.dev) servers, one per loader and
  Minecraft version:
  - Folia 26.1.2 (build 8) and Folia 26.2 (build 7, beta).
  - Paper 26.1.2, 26.2 and 26.3.
  - Fabric 26.1.2, 26.2 and 26.3 (with Fabric API).
  - NeoForge 26.1.2.7-beta, 26.2.0.88 and 26.3.0.34-beta.
- **Data:** each world was seeded with a copy of a real server's saved EndInv data (3
  inventories, one with 107 item types).
- **Client:** a real game client (Prism Launcher), one per loader and Minecraft version, with the
  matching mod jar.
- **Driving the client:**
  - On 26.1.2 (Fabric), a bot drove the player through Emma's bridge mod. It could read the
    inventory and the EndInv contents, break blocks, attack, click slots and run commands.
  - On 26.2 and 26.3, and for NeoForge clients, the tests used server console commands, and
    checked the results with `execute if items`, entity counts and screenshots of the game window.

## Checks

| # | Check | How | Passes when |
|---|---|---|---|
| 1 | Server loads | Start the server | The log shows the plugin/mod enabled and `Initialized EndlessInventoryData … with 3 inventories` |
| 2 | Client joins, EndInv syncs | Connect; read the EndInv the client received | The client has the full item list (107+ types) |
| 3 | Block drop → EndInv | Place an emerald block, break it with the player (26.1.2) | EndInv +1, the inventory has none |
| 4 | Walk-over pickup → EndInv | Drop 5 diamond blocks at the player's feet | EndInv +5, the inventory has **none** (no duplicate), nothing left on the ground |
| 5 | `/autopick off` / `on` / `status` | Turn auto-pickup off, drop 3 gold blocks, turn it on, ask for status | The 3 go to the normal inventory and none to EndInv; status replies "on for you" |
| 6 | Kill loot → EndInv | The player kills a cow (26.1.2: sword attacks; others: `damage … by <player>`) | Beef/leather in EndInv, none in the inventory or on the ground; XP to the player |
| 7 | Storage Tracker | `/storage tag`, right-click a chest holding 7 lapis blocks, then `/storage find minecraft:lapis_block` (26.1.2) | "Now tracking Chest…" and the find lists the 7 lapis blocks at that chest |
| 8 | Restart persistence | Restart the server, rejoin | EndInv counts are unchanged, the server reloads 3 inventories |
| 9 | EndInv screen | Press **I** (screenshot) | The EndInv screen opens with the player's items |
| 10 | Clean log | Scan the server log | No EndInv errors or exceptions |

On NeoForge clients the attached panel (EndInv beside the player inventory, with **Loot All**)
was also checked by screenshot. The cursor-stack fix was checked by picking up a stack in the
EndInv screen and hovering over the grid.

## Results (1.4.1)

| Minecraft | Server | Client | Checks | Result |
|---|---|---|---|---|
| 26.1.2 | Folia | Fabric (bridge) | 1–8, 10 | ✅ |
| 26.1.2 | Paper | Fabric (bridge) | 1–8, 10 | ✅ |
| 26.1.2 | Fabric | Fabric (bridge) | 1–8, 10 | ✅ |
| 26.1.2 | Paper | NeoForge | 1, 2, 4–6, 8–10 | ✅ |
| 26.1.2 | NeoForge | NeoForge | 1, 2, 4–6, 8–10 | ✅ |
| 26.2 | Folia (beta) | Fabric | 1, 2, 4–6, 8–10 | ✅ |
| 26.2 | Paper | Fabric | 1, 2, 4–6, 8, 10 | ✅ |
| 26.2 | Fabric | Fabric | 1, 2, 4–6, 8, 10 | ✅ |
| 26.2 | NeoForge | NeoForge | 1, 2, 4–6, 8–10 | ✅ |
| 26.3 | Paper | Fabric | 1, 2, 4–6, 8–10 | ✅ |
| 26.3 | Fabric | Fabric | 1, 2, 4–6, 8–10 | ✅ |
| 26.3 | NeoForge | NeoForge | 1, 2, 4–6, 8–10 | ✅ |
| 26.3 | Folia | — | — | ⏳ Folia 26.3 not released yet |

Also checked:
- The 1.3 → 1.4 upgrade (mod id `endless_inventory` → `emma_endinv`), on a copy of a live Folia
  server's data:
  - The 1.3.0 and 1.4.x plugins load the same 3 EndInvs.
  - Configs are migrated.
  - Items saved as `endless_inventory:*` load as `emma_endinv:*` on Fabric and NeoForge.
  - A Fabric client received its full EndInv afterwards.
- The plugin enables on Paper 26.3 (build 140).

## Bugs found and fixed (1.4.1)

| Bug | Where |
|---|---|
| Walk-over auto-pickup duplicated items: EndInv **and** inventory. Paper restores the stack after `EntityPickupItemEvent`; the event is now cancelled once EndInv takes the items | Folia/Paper plugin |
| Loot from player kills stayed on the ground: the kill's damage source was read too late; now uses the event's damage source / killer | Folia/Paper plugin |
| The I key didn't open the EndInv screen (sent the attach-only payload) | NeoForge client |
| The settings sync payload ran the server handler on the client (ClassCastException) | NeoForge client |
| NeoForge clients couldn't join Folia/Paper servers (payloads now optional) | NeoForge client |
| `/storage find minecraft:lapis_block` found nothing (full ids weren't matched) | All |
| The stack held on the cursor was hidden behind the EndInv panels | All clients |

## Not covered by these tests

Not functionally tested in this run; they work in normal play on the author's server, but have
no automated check yet:
- Recipe-book autofill from EndInv, and shift-clicking into the built-in stations (crafting,
  furnaces, brewing, stonecutter, grindstone, smithing).
- Background cooking and brewing.
- Shared and combined inventories, access levels, admin actions, snapshots and restore.
- Loot All, starring items, and search and sorting in the GUI.
- Block breaking and the Storage Tracker on 26.2/26.3 (no bot bridge there).
- Many players at once, and performance.
- Folia 26.3 (not released).

## Re-running

The harness used here is specific to the author's home lab: the Pelican panel, the emmabrain
node, Prism instances and Emma's bridge. It isn't part of this repository. To repeat a check by
hand:
1. Install the matching jars on a server and a client.
2. Join the server.
3. Follow the "How" column above with an operator account.
