# Emma's Endless Inventory: API for mod developers

Other mods can read a player's Endless Inventory (EndInv), put items into it, take items out, and
drive the EndInv features that players use (Loot All, swapping items into slots, the Storage
Tracker). The API is two classes in `com.emma.endinv.api`:

| Class | Side | Loaders |
|---|---|---|
| `EmmaEndInvServerApi` | Server | Fabric and NeoForge servers, integrated server (single player), Folia/Paper plugin |
| `EmmaEndInvApi` | Client | Fabric and NeoForge clients |

> `EmmaEndInvServerApi`, `EmmaEndInvApi.count` and `EmmaEndInvApi.contents` are new in **1.4.4**.
> The other client methods have existed since 1.4.0.

Mod id: **`emma_endinv`**. The ids `endless_inventory` (1.3 and earlier) and `endless-inventory`
(the upstream mod) belong to other versions and mods.

## Adding the dependency

Use it as an **optional** dependency: compile against it, and don't bundle it (no `include`/`jarJar`).

**Modrinth Maven** (any Modrinth version number works; pick the loader and MC version you build for):

```groovy
repositories {
    exclusiveContent {
        forRepository { maven { url = "https://api.modrinth.com/maven" } }
        filter { includeGroup "maven.modrinth" }
    }
}
dependencies {
    // Fabric (Loom)
    modCompileOnly "maven.modrinth:emmas-endless-inventory:1.4.4+26.1.2-fabric"
    // NeoForge (ModDevGradle)
    compileOnly "maven.modrinth:emmas-endless-inventory:1.4.4+26.1.2-neoforge"
}
```

**CurseForge Maven** (use the file id from the file's URL on CurseForge):

```groovy
repositories { maven { url = "https://cursemaven.com"; content { includeGroup "curse.maven" } } }
dependencies { compileOnly "curse.maven:emmas-endless-inventory-1718600:<fileId>" }
```

Or download the jar from the [GitHub releases](https://github.com/DragonbornElric/Emma-EndInv/releases)
and add it with `compileOnly files("libs/emma_endinv-fabric-26.1.2-1.4.4.jar")`.

Declare the optional dependency so loaders know about it:
- **Fabric** `fabric.mod.json`: `"suggests": { "emma_endinv": "*" }`
- **NeoForge** `neoforge.mods.toml`: a `[[dependencies.yourmod]]` entry with
  `modId = "emma_endinv"`, `type = "optional"`, `side = "BOTH"`

## Only call it when EndInv is installed

Check once, then only touch API classes behind that check. Keep API calls in a separate class, so
your mod still loads without EndInv.

```java
// Fabric
boolean endInv = FabricLoader.getInstance().isModLoaded("emma_endinv");
// NeoForge
boolean endInv = ModList.get().isLoaded("emma_endinv");
```

Emma's bridge mod does it with a class check instead, which works on any loader:

```java
private static Boolean available;
public static boolean isAvailable() {
    if (available != null) return available;
    try {
        Class.forName("com.emma.endinv.api.EmmaEndInvApi");
        available = true;
    } catch (ClassNotFoundException | LinkageError e) {
        available = false;
    }
    return available;
}
```

## Server API: `EmmaEndInvServerApi`

Call it on the server thread. In the Folia plugin, call it on the player's region thread (for
example from `player.getScheduler().run(...)`).

Items match **exactly**, the same way EndInv stores them: same item and same data components
(enchantments, damage, custom name, ...). A plain diamond sword and an enchanted one are
different entries. Counts are `int` and can be far above 64. Changes save with the world and reach
the player's client on the next tick.

| Method | Returns |
|---|---|
| `int count(ServerPlayer player, ItemStack like)` | How many of that item the player has (the count of `like` is ignored) |
| `ItemStack insert(ServerPlayer player, ItemStack stack)` | What didn't fit: empty if everything was stored, the whole stack if EndInv isn't available. `stack` is not modified |
| `ItemStack extract(ServerPlayer player, ItemStack like, int count)` | The items taken: at most `count`, possibly fewer or empty |
| `Map<ItemStack, Integer> contents(ServerPlayer player)` | A copy of everything: one 1-count stack per item type, mapped to its stored count |
| `boolean isStationUnlocked(ServerPlayer player, Item block)` | Whether the station that block (crafting table, furnace, smoker, blast furnace, stonecutter, grindstone, smithing table, brewing stand, enchanting table) opens can be used. Always true when the server has `FreeCraftingStations` on, always false when it has `CraftingStations` off. *1.4.5* |
| `boolean unlockStation(ServerPlayer player, Item block)` | Puts one of that block from the inventory, else EndInv, into its station. True if it unlocked now. *1.4.5* |
| `int bookshelves(ServerPlayer player)` | Bookshelves in the enchanting station (0..15). *1.4.5* |
| `int addBookshelves(ServerPlayer player, int count)` | Puts up to `count` bookshelves from the inventory, else EndInv, into the enchanting station. Returns how many went in. *1.4.5* |

"The player's EndInv" is the one the player currently uses: their own, or a shared one they
selected. All methods return empty or zero results before EndInv data is loaded.

### Example: quest reward straight into EndInv

```java
public static void giveReward(ServerPlayer player, ItemStack reward) {
    ItemStack leftover = EndInvCompat.isAvailable()
            ? EmmaEndInvServerApi.insert(player, reward)
            : reward;
    if (!leftover.isEmpty()) player.getInventory().placeItemBackInInventory(leftover);
}
```

### Example: pay with items from EndInv

```java
public static boolean pay(ServerPlayer player, ItemStack currency, int price) {
    if (EmmaEndInvServerApi.count(player, currency) < price) return false;
    ItemStack paid = EmmaEndInvServerApi.extract(player, currency, price);
    return paid.getCount() == price;
}
```

### Folia/Paper plugins

The Folia/Paper plugin (`EmmaEndInv`) contains the same `EmmaEndInvServerApi`. It uses Mojang's
server classes (`ServerPlayer`, `ItemStack`), so your plugin needs paperweight-userdev. Get the
player with `((CraftPlayer) bukkitPlayer).getHandle()`. Declare the dependency in your
`paper-plugin.yml` so you can see its classes:

```yaml
dependencies:
  server:
    EmmaEndInv:
      load: BEFORE
      required: false
      join-classpath: true
```

## Client API: `EmmaEndInvApi`

Call it on the client thread (render thread). It works on the client's synced copy of the
player's EndInv, which is filled after joining a server that has EndInv.

| Method | What it does |
|---|---|
| `int count(ItemStack like)` | How many of that item the player has |
| `Map<ItemStack, Integer> contents()` | A copy of everything: one 1-count stack per item type, mapped to its count |
| `int lootAllOpenContainerToEndInv()` | Same as the **Loot All** button: moves every slot of the open chest/container into EndInv. Returns the number of slots queued, 0 if no container is open |
| `boolean swapEndInvWithMenuSlot(ItemKey key, int menuSlotIndex)` | Swaps an EndInv item with a slot of the open menu (`player.containerMenu`). Empty slot: the item is placed from EndInv. EndInv doesn't have it: the slot empties into EndInv. Both: they swap. Respects `mayPickup`/`mayPlace`. Returns true if the request was sent |
| `void requestStorageIndex()` | Asks the server for the Storage Tracker index (every tagged chest and its contents) |
| `boolean unlockStation(Item block)` | Puts one station block (see the server method) from the inventory, else EndInv, into its station; no screen needed. Safe to repeat: the server ignores it when the station is free or already unlocked, or there is no such block. Returns true if the request was sent. *1.4.5* |
| `Boolean isStationUnlocked(Item block)` | As of the open EndInv screen, or the last one open; null before any. *1.4.5* |
| `void addBookshelves(int count)` | Puts up to `count` bookshelves from the inventory, else EndInv, into the enchanting station (15 at most); no screen needed. *1.4.5* |
| `int bookshelves()` | Bookshelves in the enchanting station as of the open EndInv screen; -1 when none is open. *1.4.5* |
| `List<TrackedContainer> getStorageIndex()` | The last index received: dimension, position, label and contents of each tracked container |

`ItemKey` (`com.emma.endinv.util.ItemKey`) is EndInv's item + components key:
`ItemKey.asKey(stack)` makes one, and `key.toStack(count)` turns it back into a stack.

### How Emma's bridge uses it (real example)

[Emma](https://www.twitch.tv/Elric_Heart) is an AI player whose bridge mod plays Minecraft through
these calls. Simplified from her `EndinvBridge.java`:

**Know what she owns.** GOAP planning counts EndInv items as owned, so she doesn't mine things she
already has:

```java
Map<String, Integer> owned = new HashMap<>();
EmmaEndInvApi.contents().forEach((stack, n) ->
        owned.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), n, Integer::sum));
```

**Equip the best armor or tool from EndInv.** She scores the enchanted stacks, then swaps the
winner into the right slot of her open inventory menu:

```java
for (var entry : EmmaEndInvApi.contents().entrySet()) {
    ItemStack candidate = entry.getKey();
    if (isBetterHelmet(candidate)) {
        EmmaEndInvApi.swapEndInvWithMenuSlot(ItemKey.asKey(candidate), HELMET_SLOT_INDEX);
        break;
    }
}
```

**Empty chests into EndInv** after opening them:

```java
int queued = EmmaEndInvApi.lootAllOpenContainerToEndInv();
```

**Find where things are stored**, with the Storage Tracker:

```java
EmmaEndInvApi.requestStorageIndex();               // reply arrives a moment later
for (TrackedContainer c : EmmaEndInvApi.getStorageIndex()) {
    if (c.items().stream().anyMatch(s -> s.item() == Items.IRON_INGOT)) walkTo(c.pos());
}
```

`TrackedContainer` fields: `dimension`, `pos`, `partner` (the other half of a double chest),
`blockName`, `label`, `owner`, `ownerName`, `updated`, `items` and `nested` (the contents of shulker
boxes and other item containers stored inside). Each item is an `ItemStackLike(item, count, components)`.

## Stability

Only the two classes in `com.emma.endinv.api` are the public API. Methods there keep working
across minor versions and are only removed in a major version, with notice in the changelog.
Everything else (`CachedSrcInv`, `SourceInventory`, menus, payloads) is internal and can change in
any release, so don't call it directly.

## Help

Questions and requests for more API: [GitHub issues](https://github.com/DragonbornElric/Emma-EndInv/issues),
or ask on the livestream at [twitch.tv/Elric_Heart](https://www.twitch.tv/Elric_Heart).
