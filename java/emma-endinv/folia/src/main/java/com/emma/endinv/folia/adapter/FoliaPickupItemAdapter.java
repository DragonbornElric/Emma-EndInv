package com.emma.endinv.folia.adapter;

import com.emma.endinv.autopick.events.IPlayerPickupItemEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.bukkit.craftbukkit.entity.CraftItem;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

public class FoliaPickupItemAdapter implements IPlayerPickupItemEvent {

    private final Cancellable event;
    private final org.bukkit.entity.Item item;
    private final org.bukkit.entity.LivingEntity taker;

    public FoliaPickupItemAdapter(EntityPickupItemEvent event) {
        this(event, event.getItem(), event.getEntity());
    }

    /** Paper fires only this one when the inventory has no room for the item (canHold == 0). */
    public FoliaPickupItemAdapter(PlayerAttemptPickupItemEvent event) {
        this(event, event.getItem(), event.getPlayer());
    }

    private FoliaPickupItemAdapter(Cancellable event, org.bukkit.entity.Item item, org.bukkit.entity.LivingEntity taker) {
        this.event = event;
        this.item = item;
        this.taker = taker;
    }

    @Override
    public ItemEntity getItem() { return (ItemEntity) ((CraftItem) item).getHandle(); }

    @Override
    public Player getPlayer() { return (Player) ((CraftLivingEntity) taker).getHandle(); }

    @Override
    public void cancelVanillaPickup() { event.setCancelled(true); }
}
