package com.emma.endinv.api;

import com.emma.endinv.client.action.LootAllAction;
import com.emma.endinv.client.action.SwapMenuSlotAction;
import com.emma.endinv.util.ItemKey;

public final class EmmaEndInvApi {
    private EmmaEndInvApi() {}

    /** Drains every lootable slot of the currently open foreign container into the player's
     *  Endless Inventory. No-op if no container is open or the player's own inventory is open.
     *  Must be called on the client thread. Returns slots queued, or 0. */
    public static int lootAllOpenContainerToEndInv() {
        return LootAllAction.lootAllOpenContainer();
    }

    /** Programmatically swap an EndInv item identified by {@code key} with the slot at
     *  {@code menuSlotIndex} in the player's currently open menu (player.containerMenu).
     *  Slot indices are menu-relative — use menu.slots to enumerate them.
     *  <ul>
     *    <li>Slot has item, EndInv does not → item drains from slot into EndInv.</li>
     *    <li>Slot is empty, EndInv has item → item is placed into the slot.</li>
     *    <li>Both have items → atomic swap (aborts if EndInv cannot absorb the slot's full stack).</li>
     *  </ul>
     *  Respects slot.mayPickup / slot.mayPlace; no-ops on out-of-range indices.
     *  Must be called on the client thread. Returns true if the request packet was sent. */
    public static boolean swapEndInvWithMenuSlot(ItemKey key, int menuSlotIndex) {
        return SwapMenuSlotAction.swapWithMenuSlot(key, menuSlotIndex);
    }

    /** How many of {@code like} (item + components, count ignored) the client's copy of the player's
     *  EndInv holds. The copy is synced from the server; it's empty until the player joins a server
     *  that has EndInv. Client thread only. */
    public static int count(net.minecraft.world.item.ItemStack like) {
        if (like.isEmpty()) return 0;
        var state = com.emma.endinv.client.CachedSrcInv.INSTANCE.getItemMap().get(ItemKey.asKey(like));
        return state == null ? 0 : Math.max(0, state.count());
    }

    /** A copy of the client's view of the player's EndInv: each key is a 1-count stack (one per item
     *  type, with its components), each value the stored count. Client thread only. */
    public static java.util.Map<net.minecraft.world.item.ItemStack, Integer> contents() {
        java.util.Map<net.minecraft.world.item.ItemStack, Integer> out = new java.util.LinkedHashMap<>();
        com.emma.endinv.client.CachedSrcInv.INSTANCE.getItemMap().forEach((key, state) -> {
            if (state != null && state.count() > 0) out.put(key.toStack(1), state.count());
        });
        return out;
    }

    /**
     * Put one station block (crafting table, furnace, smoker, blast furnace, stonecutter, grindstone,
     * smithing table or brewing stand) into its EndInv station to unlock it, as a player does by
     * clicking the locked button holding it. The block is taken from the player's inventory, or from
     * EndInv when the inventory has none. Needs no open screen. The server does nothing when stations
     * are free, the station is already unlocked, or the player has no such block, so it is safe to
     * call again. Client thread only.
     * @return true if the request was sent ({@code block} is a station block)
     * @since 1.4.5
     */
    public static boolean unlockStation(net.minecraft.world.item.Item block) {
        com.emma.endinv.menu.Station st = com.emma.endinv.menu.Station.forUnlockItem(block);
        if (st == null) return false;
        com.emma.endinv.ModInfo.getPacketDistributor().sendToServer(
                new com.emma.endinv.network.payloads.toServer.UnlockStationPayload(st));
        return true;
    }

    /**
     * Whether the station that {@code block} unlocks can be used: TRUE or FALSE as of the open EndInv
     * screen, or the last one that was open; null when no EndInv screen has been opened yet (or
     * {@code block} is not a station block). Stations are all TRUE when the server has them free.
     * Client thread only.
     * @since 1.4.5
     */
    public static Boolean isStationUnlocked(net.minecraft.world.item.Item block) {
        com.emma.endinv.menu.Station st = com.emma.endinv.menu.Station.forUnlockItem(block);
        if (st == null) return null;
        var mc = net.minecraft.client.Minecraft.getInstance();
        int mask = mc.player != null && mc.player.containerMenu instanceof com.emma.endinv.menu.EndlessInventoryMenu menu
                ? menu.getStationUnlockMask()
                : com.emma.endinv.menu.StationUnlocks.lastSeenClientMask;
        return mask < 0 ? null : com.emma.endinv.menu.StationUnlocks.isUnlocked(mask, st);
    }

    /**
     * Put up to {@code count} bookshelves into the EndInv enchanting station (15 at most in all; each
     * one is a level of enchanting power, like bookshelves around a table). They come from the
     * player's inventory, then EndInv. Needs no open screen. Read the count with
     * {@link #bookshelves()}. Client thread only.
     * @since 1.4.5
     */
    public static void addBookshelves(int count) {
        if (count <= 0) return;
        com.emma.endinv.ModInfo.getPacketDistributor().sendToServer(
                new com.emma.endinv.network.payloads.toServer.AddBookshelvesPayload(count));
    }

    /**
     * Bookshelves in the enchanting station as of the open EndInv screen, or -1 when no EndInv
     * screen is open. Client thread only.
     * @since 1.4.5
     */
    public static int bookshelves() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null && mc.player.containerMenu instanceof com.emma.endinv.menu.EndlessInventoryMenu menu
                ? menu.getEnchanting().getBookshelves() : -1;
    }

    /** Ask the server for the storage index (every container tagged with a Storage Tag and its contents).
     *  The reply arrives asynchronously; read it with {@link #getStorageIndex()}. Client thread only. */
    public static void requestStorageIndex() {
        com.emma.endinv.client.gui.StorageTrackerScreen.requestIndex();
    }

    /** The last storage index received from the server, or empty if never requested. Each entry has the
     *  container's dimension, position and aggregated contents. Client thread only. */
    public static java.util.List<com.emma.endinv.storage.TrackedContainer> getStorageIndex() {
        return com.emma.endinv.client.gui.StorageTrackerScreen.lastIndex();
    }

    private static final java.util.List<java.util.function.Consumer<StationEvent>> STATION_LISTENERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Called on the client thread when a station in the player's EndInv finishes (a furnace, smoker
     *  or blast furnace cooked its last input, a brewing stand finished a brew) or burns out with input
     *  left, whether its screen is open or not. Needs a server running EndInv 1.4.5+ (any loader).
     *  @since 1.4.5 */
    public static void addStationListener(java.util.function.Consumer<StationEvent> listener) {
        STATION_LISTENERS.add(listener);
    }

    public static void removeStationListener(java.util.function.Consumer<StationEvent> listener) {
        STATION_LISTENERS.remove(listener);
    }

    /** Internal: delivers a server's station event to the listeners. */
    public static void fireStationEvent(StationEvent event) {
        for (var listener : STATION_LISTENERS) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().warn("EndInv station listener failed", e);
            }
        }
    }
}
