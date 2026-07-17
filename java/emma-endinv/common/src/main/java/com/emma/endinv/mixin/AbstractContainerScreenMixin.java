package com.emma.endinv.mixin;

import com.emma.endinv.client.events.ScreenAttachment;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void endinv$mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (ScreenAttachment.handleMouseDrag((AbstractContainerScreen<?>) (Object) this,
                mouseX, mouseY, button, deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }
}
