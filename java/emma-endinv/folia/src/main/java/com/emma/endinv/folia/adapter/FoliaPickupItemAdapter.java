package com.emma.endinv.folia.adapter;

import com.emma.endinv.autopick.events.IPlayerPickupItemEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.bukkit.craftbukkit.entity.CraftItem;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.entity.EntityPickupItemEvent;

public class FoliaPickupItemAdapter implements IPlayerPickupItemEvent {

    private final EntityPickupItemEvent event;

    public FoliaPickupItemAdapter(EntityPickupItemEvent event) { this.event = event; }

    @Override
    public ItemEntity getItem() { return (ItemEntity) ((CraftItem) event.getItem()).getHandle(); }

    @Override
    public Player getPlayer() { return ((CraftPlayer) event.getEntity()).getHandle(); }

    @Override
    public void cancelVanillaPickup() { event.setCancelled(true); }
}
