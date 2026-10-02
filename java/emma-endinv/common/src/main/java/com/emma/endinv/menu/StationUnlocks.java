package com.emma.endinv.menu;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.SourceInventory;
import com.emma.endinv.options.ServerConfigs;
import com.emma.endinv.util.ItemKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Station locking for {@code FreeCraftingStations = false}: a station's button stays locked until
 * a player clicks it holding that station's block ({@link Station#unlockItem()}), which uses up one
 * and unlocks the station for that EndInv for good.
 *
 * <p>The server sends the unlocked stations to the client as a bit mask in a menu data slot
 * (bit = {@link Station#ordinal()}); the unlock click is a vanilla menu button click with id
 * {@link #BUTTON_BASE} + ordinal. Shared by the common menu and the Folia menu.
 */
public final class StationUnlocks {

    /** Menu button ids at and above this unlock a station; below are stonecutter recipe picks. */
    public static final int BUTTON_BASE = 1000;

    /**
     * Set in the mask when the server has stations turned off ({@code CraftingStations = false}):
     * every station is locked and the client hides the buttons. Bit 14 keeps the mask a positive
     * short, which is how menu data slots go over the network.
     */
    public static final int DISABLED_BIT = 1 << 14;

    /** Every station unlocked. Also the client's value before the server's first sync. */
    public static final int ALL = allMask();

    /** Client side: the mask last seen on an open EndInv screen, or -1 before one was opened. */
    public static volatile int lastSeenClientMask = -1;

    private StationUnlocks() {}

    private static int allMask() {
        int mask = 0;
        for (Station st : Station.values()) mask |= bit(st);
        return mask;
    }

    public static int bit(Station st) {
        return 1 << st.ordinal();
    }

    /** Server side: whether stations exist at all ({@code CraftingStations}). */
    public static boolean enabled() {
        return ServerConfigs.CRAFTING_STATIONS.get();
    }

    public static boolean isDisabled(int mask) {
        return mask >= 0 && (mask & DISABLED_BIT) != 0;
    }

    public static boolean free() {
        return ServerConfigs.FREE_CRAFTING_STATIONS.get();
    }

    /**
     * Server side: the unlocked stations of {@code source}, all of them when stations are free, none
     * plus {@link #DISABLED_BIT} when stations are turned off.
     */
    public static int mask(@Nullable SourceInventory source) {
        if (!enabled()) return bit(Station.NONE) | DISABLED_BIT;
        if (!(source instanceof EndlessInventory endInv) || free()) return ALL;
        int mask = bit(Station.NONE);
        for (Station st : Station.values()) {
            if (endInv.isStationUnlocked(st)) mask |= bit(st);
        }
        return mask;
    }

    public static boolean isUnlocked(int mask, Station st) {
        return st == Station.NONE || (mask & bit(st)) != 0;
    }

    /** The station a menu button id unlocks, or null when the id is not an unlock click. */
    @Nullable
    public static Station stationForButton(int buttonId) {
        int ordinal = buttonId - BUTTON_BASE;
        Station[] values = Station.values();
        if (ordinal <= 0 || ordinal >= values.length) return null;
        return values[ordinal];
    }

    /**
     * Server side: unlock {@code st} with the stack on the player's cursor.
     * @return true when the station was unlocked (one block used up)
     */
    public static boolean tryUnlock(AbstractContainerMenu menu, Player player, @Nullable SourceInventory source, Station st) {
        if (!enabled() || free() || !(source instanceof EndlessInventory endInv) || endInv.isStationUnlocked(st)) return false;
        Item needed = st.unlockItem();
        ItemStack carried = menu.getCarried();
        if (needed == null || carried.isEmpty() || !carried.is(needed)) return false;
        if (!player.hasInfiniteMaterials()) {
            carried.shrink(1);
            menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        }
        endInv.unlockStation(st);
        return true;
    }

    /**
     * Server side, for the API and bots: unlock {@code st} with one of its blocks taken from the
     * player's inventory, or from EndInv when the inventory has none. Needs no open screen.
     * @return true when the station was unlocked (one block used up)
     */
    public static boolean tryUnlockFromStorage(Player player, @Nullable SourceInventory source, Station st) {
        if (!enabled() || free() || !(source instanceof EndlessInventory endInv) || endInv.isStationUnlocked(st)) return false;
        Item needed = st.unlockItem();
        if (needed == null) return false;
        if (!player.hasInfiniteMaterials() && !takeOne(player.getInventory(), needed)) {
            ItemKey key = ItemKey.asKey(new ItemStack(needed));
            if (!endInv.getItemMap().containsKey(key) || endInv.takeItem(key, 1).isEmpty()) return false;
        }
        endInv.unlockStation(st);
        return true;
    }

    private static boolean takeOne(Inventory inv, Item item) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }

    /**
     * Server side, a player died ({@code DropStationsOnDeath = true}, keepInventory off): take the
     * station blocks put into their EndInv (when stations aren't free) and the enchanting station's
     * bookshelves out of it, to be dropped where they died. Everyone sharing that EndInv loses them.
     * @return the stacks to drop; empty when the setting is off
     */
    public static java.util.List<ItemStack> takeDeathDrops(Player player) {
        if (!ServerConfigs.DROP_STATIONS_ON_DEATH.get()) return java.util.List.of();
        EndlessInventory endInv = com.emma.endinv.ServerLevelEndInv.getEndInvForPlayer(player).orElse(null);
        if (endInv == null) return java.util.List.of();
        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        if (!free()) {
            for (Station st : endInv.getUnlockedStations()) {
                Item block = st.unlockItem();
                if (block != null) drops.add(new ItemStack(block));
                endInv.lockStation(st);
            }
        }
        if (endInv.getBookshelves() > 0) {
            drops.add(new ItemStack(net.minecraft.world.item.Items.BOOKSHELF, endInv.getBookshelves()));
            endInv.setBookshelves(0);
        }
        return drops;
    }
}
