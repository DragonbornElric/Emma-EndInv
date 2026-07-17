package com.emma.endinv.autopick;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class EndInvRecipeUnlock {

    private EndInvRecipeUnlock() {}

    // EndInv bypasses the vanilla Inventory#add path, so mirror the same
    // inventory-changed criterion that normal pickups use for recipe/advancement unlocks.
    public static void fireVanillaInventoryChanged(ServerPlayer player, ItemStack pickedUp) {
        if (pickedUp.isEmpty()) return;
        CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), pickedUp.copy());
    }
}
