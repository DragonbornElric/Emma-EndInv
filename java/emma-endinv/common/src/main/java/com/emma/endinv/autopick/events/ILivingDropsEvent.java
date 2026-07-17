package com.emma.endinv.autopick.events;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ILivingDropsEvent extends ICancelable{

    @Nullable
    DamageSource getSource();

    Collection<ItemEntity> getDrops();
}
