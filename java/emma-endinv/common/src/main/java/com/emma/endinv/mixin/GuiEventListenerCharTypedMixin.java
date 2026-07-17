package com.emma.endinv.mixin;

import com.emma.endinv.client.events.ScreenCharTypedEvents;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiEventListener.class)
public interface GuiEventListenerCharTypedMixin {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void endinv$beforeCharTyped(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ScreenCharTypedEvents.dispatch((GuiEventListener) this, codePoint, modifiers)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
