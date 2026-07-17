package com.emma.endinv.folia.adapter;

import com.emma.endinv.autopick.events.ILivingDropsEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FoliaLivingDropsAdapter implements ILivingDropsEvent {

    private final EntityDeathEvent event;
    private final List<ItemEntity> wrappedDrops;
    private boolean canceled = false;

    public FoliaLivingDropsAdapter(EntityDeathEvent event) {
        this.event = event;
        this.wrappedDrops = new ArrayList<>();
        var entity = ((CraftLivingEntity) event.getEntity()).getHandle();
        for (org.bukkit.inventory.ItemStack stack : event.getDrops()) {
            ItemStack nms = CraftItemStack.asNMSCopy(stack);
            wrappedDrops.add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), nms));
        }
    }

    @Override
    public DamageSource getSource() {
        return ((CraftLivingEntity) event.getEntity()).getHandle().getLastDamageSource();
    }

    @Override
    public Collection<ItemEntity> getDrops() { return wrappedDrops; }

    @Override
    public void setCanceled(boolean canceled) { this.canceled = canceled; }

    public void syncBackToEvent() {
        if (canceled) { event.getDrops().clear(); return; }
        event.getDrops().clear();
        for (ItemEntity ie : wrappedDrops) {
            if (!ie.isRemoved() && !ie.getItem().isEmpty()) {
                event.getDrops().add(CraftItemStack.asBukkitCopy(ie.getItem()));
            }
        }
    }
}
