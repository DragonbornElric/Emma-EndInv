package com.emma.endinv.menu;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.SourceInventory;
import com.emma.endinv.options.ServerConfigs;
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

    /** Every station unlocked. Also the client's value before the server's first sync. */
    public static final int ALL = allMask();

    private StationUnlocks() {}

    private static int allMask() {
        int mask = 0;
        for (Station st : Station.values()) mask |= bit(st);
        return mask;
    }

    public static int bit(Station st) {
        return 1 << st.ordinal();
    }

    public static boolean free() {
        return ServerConfigs.FREE_CRAFTING_STATIONS.get();
    }

    /** Server side: the unlocked stations of {@code source}, all of them when stations are free. */
    public static int mask(@Nullable SourceInventory source) {
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
        if (free() || !(source instanceof EndlessInventory endInv) || endInv.isStationUnlocked(st)) return false;
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
}
