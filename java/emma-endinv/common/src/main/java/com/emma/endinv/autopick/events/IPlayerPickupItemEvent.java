package com.emma.endinv.autopick.events;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

public interface IPlayerPickupItemEvent {

    ItemEntity getItem();

    Player getPlayer();

    /**
     * Stop the loader from also giving the item to the player once EndInv has taken it
     * (Paper resets the stack count after the event, so emptying the stack alone is not enough).
     */
    void cancelVanillaPickup();
}
