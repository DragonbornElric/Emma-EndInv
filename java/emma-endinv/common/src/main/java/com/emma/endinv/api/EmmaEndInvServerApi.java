package com.emma.endinv.api;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemState;
import net.minecraft.server.level.ServerPlayer;
import com.emma.endinv.menu.Station;
import com.emma.endinv.menu.StationUnlocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side API: read and change a player's Endless Inventory from another mod.
 * <p>
 * Works on Fabric and NeoForge servers and inside the Folia/Paper plugin. Call it on the server
 * thread (Fabric/NeoForge) or on the player's region thread (Folia). Items are matched exactly:
 * same item and same data components (enchantments, damage, name, ...), like EndInv itself.
 * Changes are saved with the world and sent to the player's client on the next server tick.
 * Methods return empty/zero results when EndInv data isn't loaded yet (before the server starts).
 */
public final class EmmaEndInvServerApi {
    private EmmaEndInvServerApi() {}

    /** How many of {@code like} (item + components, count ignored) the player's EndInv holds. */
    public static int count(ServerPlayer player, ItemStack like) {
        if (like.isEmpty()) return 0;
        return endInv(player)
                .map(inv -> inv.getItemMap().get(ItemKey.asKey(like)))
                .map(ItemState::count)
                .orElse(0);
    }

    /**
     * Put {@code stack} into the player's EndInv. The given stack is not modified.
     * @return what didn't fit (empty when everything was stored, the full stack when EndInv isn't available)
     */
    public static ItemStack insert(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        Optional<EndlessInventory> inv = endInv(player);
        return inv.isPresent() ? inv.get().addItem(stack.copy()) : stack.copy();
    }

    /**
     * Take up to {@code count} of {@code like} (item + components) out of the player's EndInv.
     * @return the items taken (possibly fewer than asked, or empty)
     */
    public static ItemStack extract(ServerPlayer player, ItemStack like, int count) {
        if (like.isEmpty() || count <= 0 || count(player, like) == 0) return ItemStack.EMPTY;
        return endInv(player).map(inv -> inv.takeItem(ItemKey.asKey(like), count)).orElse(ItemStack.EMPTY);
    }

    /** A copy of everything in the player's EndInv: each key is a 1-count stack (one per item type,
     *  with its components), each value the stored count. */
    public static Map<ItemStack, Integer> contents(ServerPlayer player) {
        Map<ItemStack, Integer> out = new LinkedHashMap<>();
        endInv(player).ifPresent(inv -> inv.getItemMap().forEach((key, state) -> {
            if (state.count() > 0) out.put(key.toStack(1), state.count());
        }));
        return out;
    }

    /**
     * Whether the station that {@code block} (crafting table, furnace, smoker, blast furnace,
     * stonecutter, grindstone, smithing table, brewing stand) unlocks can be used in the player's
     * EndInv. Always true when the server has {@code FreeCraftingStations} on.
     * @since 1.4.5
     */
    public static boolean isStationUnlocked(ServerPlayer player, Item block) {
        Station st = Station.forUnlockItem(block);
        if (st == null) return false;
        return StationUnlocks.isUnlocked(StationUnlocks.mask(endInv(player).orElse(null)), st);
    }

    /**
     * Unlock that station with one block from the player's inventory, or from EndInv when the
     * inventory has none (used up; nothing is taken in creative).
     * @return true if it was unlocked now; false when stations are free, it was already unlocked,
     *         or the player has no such block
     * @since 1.4.5
     */
    public static boolean unlockStation(ServerPlayer player, Item block) {
        Station st = Station.forUnlockItem(block);
        if (st == null) return false;
        return StationUnlocks.tryUnlockFromStorage(player, endInv(player).orElse(null), st);
    }

    /** Bookshelves in the player's EndInv enchanting station (0..15). @since 1.4.5 */
    public static int bookshelves(ServerPlayer player) {
        return endInv(player).map(EndlessInventory::getBookshelves).orElse(0);
    }

    /**
     * Put up to {@code count} bookshelves from the player's inventory, then EndInv, into the
     * enchanting station (15 at most in all). @return how many were added
     * @since 1.4.5
     */
    public static int addBookshelves(ServerPlayer player, int count) {
        return com.emma.endinv.menu.EnchantingStation.addBookshelvesFromStorage(player, endInv(player).orElse(null), count);
    }

    private static Optional<EndlessInventory> endInv(ServerPlayer player) {
        return ServerLevelEndInv.getEndInvForPlayer(player);
    }

    /** Called on the server thread (the player's region thread on Folia) when a station in a
     *  player's EndInv finishes or burns out; see {@link StationEvent}. Runs for every player whose
     *  EndInv it is, online and with or without the screen open.
     *  @since 1.4.5 */
    public static void addStationListener(java.util.function.BiConsumer<ServerPlayer, StationEvent> listener) {
        com.emma.endinv.menu.StationNotifications.addListener(listener);
    }
}
