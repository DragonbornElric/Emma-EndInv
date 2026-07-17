package com.emma.endinv.folia;

import com.emma.endinv.IPlatform;
import com.emma.endinv.platform.ILoaderProvider;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class FoliaPlatform implements IPlatform {

    @Override
    public boolean onItemStackedOn(ItemStack clickedItem, ItemStack carriedItem, Slot slot,
                                   ClickAction action, Player player, SlotAccess access) {
        var features = player.level().enabledFeatures();
        return (carriedItem.isItemEnabled(features) && carriedItem.overrideStackedOnOther(slot, action, player))
                || (clickedItem.isItemEnabled(features) && clickedItem.overrideOtherStackedOnMe(carriedItem, slot, action, player, access));
    }

    @Override
    public boolean isModLoaded(String modid) {
        return ILoaderProvider.get().isModLoaded(modid);
    }
}
