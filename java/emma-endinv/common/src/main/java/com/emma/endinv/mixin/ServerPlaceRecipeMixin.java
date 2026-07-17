package com.emma.endinv.mixin;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.util.recipeTransferHelper.RecipeItemProvider;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jetbrains.annotations.Nullable;

@Mixin(ServerPlaceRecipe.class)
public class ServerPlaceRecipeMixin<C extends net.minecraft.world.Container> {

    @Shadow
    private Inventory inventory;
    @Shadow
    protected StackedContents stackedContents;

    @Unique
    @Nullable
    private EndlessInventory ei$endInv;

    @Inject(method = "recipeClicked", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;fillStackedContents(Lnet/minecraft/world/entity/player/StackedContents;)V",
            shift = At.Shift.AFTER))
    private void ei$fillContents(ServerPlayer player, Recipe<C> recipe, boolean craftAll, CallbackInfo ci) {
        ei$endInv = ServerLevelEndInv.getEndInvForPlayer(player).orElse(null);
        if (ei$endInv != null) {
            RecipeItemProvider.fillStackedContents(ei$endInv.getItemsAsList(), stackedContents);
        }
    }

    @Inject(method = "moveItemToGrid", at = @At("HEAD"), cancellable = true)
    private void ei$moveFromEndInv(Slot slot, ItemStack requested, CallbackInfo ci) {
        if (ei$endInv == null) return;
        if (inventory.findSlotMatchingUnusedItem(requested) != -1) return;
        if (!ei$endInv.hasItem(requested)) return;
        ItemStack taken = ei$endInv.takeItem(requested, 1);
        if (taken.isEmpty()) return;

        ItemStack cur = slot.getItem();
        if (cur.isEmpty()) {
            slot.set(taken);
        } else {
            cur.grow(taken.getCount());
        }
        ci.cancel();
    }

}
