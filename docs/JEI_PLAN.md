# JEI testing and integration plan

Status: **planned, not started** (2026-09-29).

## Background

- JEI builds exist for every version we ship, on Fabric and NeoForge:
  - 26.1.2: `29.43.0.106`
  - 26.2: `30.38.0.231`
  - 26.3: `31.8.0.48`
- REI also has builds for all three (`26.x.8xx`), which makes it a possible later target.
- JEI is **client-only**. It loads on a Fabric/NeoForge client whatever the server is, Paper and
  Folia included. Paper and Folia can't run JEI (they're not mod loaders).
- **Recipes on Paper/Folia (to verify):** since MC 1.21.2 the server no longer sends its recipe
  list to clients. JEI then needs to be on the server too (Fabric/NeoForge server), or NeoForge's
  own recipe sync. On Paper/Folia we expect JEI to show the **item list only**: no recipe lookups
  and no "+" transfer. If confirmed, document it and don't try to work around it.

## Phase 1: test (26.1.2 first)

Client: the EmmaAI Prism instance (Fabric + bridge) plus JEI 29.43.0.106. Then a NeoForge client.

| Server (Pelican id) | JEI on server |
|---|---|
| Fabric 26.1.2 (24) | yes |
| NeoForge 26.1.2 (25) | yes |
| Paper 26.1.2 (23) | not possible |
| Folia 26.1.2 (22) | not possible |

Checks (screenshots, same method as the 1.4.3 GUI checks in TESTING.md):

1. Client loads with JEI and EndInv; no errors in the log.
2. **Layout:** the JEI item list (right side) and bookmarks (left side) don't cover the EndInv
   panel, page tabs, station buttons, search box or the attached panel beside chests and the
   player inventory. Test at a normal and a small window.
3. **Keyboard focus:** typing in JEI's search doesn't reach the EndInv search or game keys, and
   typing in EndInv's search doesn't reach JEI.
4. **R / U over EndInv grid items:** expected to fail today (the grid isn't made of real slots).
5. **"+" recipe transfer** into the EndInv crafting station and furnace: does it pull from EndInv,
   or only from the player inventory?
6. **Recipes on Paper/Folia:** confirm "item list only".
7. **Cheat mode:** giving items with auto-pickup on (they go to the inventory; that's fine).
8. JEI and the vanilla recipe book open together: no overlap, and the 1.4.3 layout still holds.

Record the results in TESTING.md.

## Phase 2: integration (only for checks that fail)

A JEI plugin in `common/.../integrate/jei/`, compiled against the JEI common API (`compileOnly`)
and loaded only when JEI is present:
- NeoForge finds it through `@JeiPlugin`.
- Fabric needs the `jei_mod_plugin` entrypoint in `fabric.mod.json`.
- Folia doesn't need it (server has no GUI), so exclude `integrate/jei/**` from `commonServerJava`.

Parts, cheapest first:
1. **Exclusion areas** (`IGuiContainerHandler.getGuiExtraAreas`) for `EndlessInventoryScreen` and
   the attached panel, so JEI moves its overlay out of the way. Small.
2. **Hover ingredient** (`getClickableIngredientUnderMouse`) for EndInv grid items, so R/U and
   JEI bookmarks work on them. Small.
3. **Recipe transfer** (`IRecipeTransferHandler`) for the EndInv crafting and cooking stations,
   filling from EndInv first. It should reuse the recipe book's EndInv-first placement (server
   side, already works on Folia) instead of a new payload. Largest part.
4. Optional: two-way sync of JEI and EndInv search text.

## Phase 3: port and document

- Merge into `mc-26.2` / `mc-26.3` (bump the JEI API pin per branch) and repeat the Phase 1 checks there.
- README: a "Works with JEI" section, including the Paper/Folia recipe limitation.
- Release as 1.5.0 (new feature) through the usual GitHub, Modrinth and CurseForge flow.
