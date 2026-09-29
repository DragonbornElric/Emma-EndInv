package com.emma.endinv.mixin;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.util.recipeTransferHelper.RecipeItemProvider;
import net.minecraft.util.Prediction;
import net.minecraft.core.Holder;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.jetbrains.annotations.Nullable;

@Mixin(ServerPlaceRecipe.class)
public class ServerPlaceRecipeMixin<R extends Recipe<?>> {

    @Shadow
    private Inventory inventory;

    @Unique
    @Nullable
    private EndlessInventory ei$endInv;

    @Inject(method = "tryPlaceRecipe", at = @At("HEAD"))
    private void ei$fillContents(RecipeHolder<R> recipe, StackedItemContents contents, CallbackInfoReturnable<?> cir) {
        ei$endInv = null;
        if (inventory.player instanceof ServerPlayer sp) {
            ei$endInv = ServerLevelEndInv.getEndInvForPlayer(sp).orElse(null);
        }
        if (ei$endInv != null) {
            RecipeItemProvider.fillStackedItemContents(ei$endInv.getItemsAsList(), contents);
        }
    }

    /**
     * EndInv first, as the Folia plugin places recipes: take the ingredient from EndInv, and only
     * when EndInv has none of it let vanilla take it from the player inventory.
     */
    @Inject(method = "moveItemToGrid", at = @At("HEAD"), cancellable = true)
    private void ei$moveFromEndInv(Slot slot, Holder<Item> item, int count, CallbackInfoReturnable<Integer> cir) {
        if (ei$endInv == null) return;

        ItemStack probe = new ItemStack(item);
        ItemStack cur = slot.getItem();
        if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, probe)) return;
        if (!ei$endInv.hasItem(probe)) return;
        ItemStack taken = ei$endInv.takeItem(probe, count);
        if (taken.isEmpty()) return;

        if (cur.isEmpty()) {
            slot.set(taken);
        } else {
            cur.grow(taken.getCount());
        }
        cir.setReturnValue(count - taken.getCount());
    }

    /** Clearing the grid puts its contents back in EndInv; what EndInv can't hold goes the vanilla way. */
    @Redirect(method = "clearGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)V"))
    private void ei$clearToEndInv(Inventory inv, ItemStack stack, boolean sendPacket, Prediction prediction) {
        if (ei$endInv != null && !stack.isEmpty()) {
            ItemStack remain = ei$endInv.addItem(stack.copy());
            stack.setCount(remain.getCount());
        }
        if (!stack.isEmpty()) {
            inv.placeItemBackInInventory(stack, sendPacket, prediction);
        }
    }

    /** The grid is cleared into EndInv, so a full player inventory doesn't block a placement. */
    @Inject(method = "testClearGrid", at = @At("HEAD"), cancellable = true)
    private void ei$gridClearsToEndInv(CallbackInfoReturnable<Boolean> cir) {
        if (inventory.player instanceof ServerPlayer sp && ServerLevelEndInv.getEndInvForPlayer(sp).isPresent()) {
            cir.setReturnValue(true);
        }
    }

}
