package com.emma.endinv.api;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A crafting station in a player's EndInv finished or stopped, open or in the background.
 * Delivered to {@link EmmaEndInvApi#addStationListener} (client) and
 * {@link EmmaEndInvServerApi#addStationListener} (server).
 *
 * @param station the station's block (furnace, smoker, blast furnace, brewing stand)
 * @param reason  what happened
 * @param result  a copy of the station's output: for {@link Reason#DONE} cooking, everything in the
 *                output slot; for brewing, the first finished bottle; empty for {@link Reason#OUT_OF_FUEL}
 * @since 1.4.5
 */
public record StationEvent(Item station, Reason reason, ItemStack result) {

    public enum Reason {
        /** A furnace, smoker or blast furnace cooked its last input item, or a brewing stand finished a brew. */
        DONE,
        /** A furnace, smoker or blast furnace burned out with input still left and no fuel. */
        OUT_OF_FUEL
    }
}
