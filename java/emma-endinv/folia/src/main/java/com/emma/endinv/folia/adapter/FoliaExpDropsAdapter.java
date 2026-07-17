package com.emma.endinv.folia.adapter;

import com.emma.endinv.autopick.events.ILivingExpDropsEvent;
import net.minecraft.world.entity.player.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.entity.EntityDeathEvent;
import org.jetbrains.annotations.Nullable;

public class FoliaExpDropsAdapter implements ILivingExpDropsEvent {

    private final EntityDeathEvent event;
    private final org.bukkit.entity.Player killer;

    public FoliaExpDropsAdapter(EntityDeathEvent event, org.bukkit.entity.Player killer) {
        this.event = event;
        this.killer = killer;
    }

    @Override public int getDroppedExperience() { return event.getDroppedExp(); }
    @Override public void setDroppedExperience(int exp) { event.setDroppedExp(exp); }
    @Override public @Nullable Player getAttackingPlayer() { return ((CraftPlayer) killer).getHandle(); }
    @Override public void setCanceled(boolean canceled) { if (canceled) event.setDroppedExp(0); }
}
