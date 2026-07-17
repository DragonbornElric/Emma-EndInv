package com.emma.endinv.mixin;

import com.emma.endinv.client.CachedSrcInv;
import com.emma.endinv.client.gui.recipebook.EndInvRecipeBookComponent;
import com.emma.endinv.util.recipeTransferHelper.RecipeItemProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.player.StackedContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookComponent.class)
public class RecipeBookComponentMixin {

    @Final
    @Shadow
    private StackedContents stackedContents;
    @Shadow
    protected Minecraft minecraft;
    @Shadow
    private int xOffset;
    @Shadow
    private int width;
    @Unique
    private final CachedSrcInv srcInv = CachedSrcInv.INSTANCE;

    @Inject(method = "initVisuals", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/RecipeBookMenu;fillCraftSlotsStackedContents(Lnet/minecraft/world/entity/player/StackedContents;)V"))
    private void fillEndInvStackedContents(CallbackInfo ci) {
        RecipeItemProvider.fillStackedContents(srcInv.getItemsAsList(), stackedContents);
    }

    @Inject(method = "updateStackedContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/RecipeBookMenu;fillCraftSlotsStackedContents(Lnet/minecraft/world/entity/player/StackedContents;)V"))
    private void updateStackedContentsOfEndInv(CallbackInfo ci) {
        RecipeItemProvider.fillStackedContents(srcInv.getItemsAsList(), stackedContents);
    }

    // Reposition the panel to the left edge of the screen when opened from EndInv
    // Pin the recipe book panel to the left edge when opened from EndInv.
    // Tab buttons render 30px to the LEFT of the panel origin, so xOffset = (width-147)/2 - 34
    // puts the tabs at x=4 and the panel at x=34, keeping all controls fully visible.
    @Inject(method = "initVisuals", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;xOffset:I",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD,
            shift = At.Shift.AFTER))
    private void ei$pinPanelToLeftEdge(CallbackInfo ci) {
        if ((Object) this instanceof EndInvRecipeBookComponent) {
            this.xOffset = (this.width - 147) / 2 - 34;
        }
    }
}
