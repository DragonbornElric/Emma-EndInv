package com.emma.endinv.client.events;

import com.emma.endinv.client.event.AutoPickTipper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class PickingUpTip {

    private PickingUpTip() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> AutoPickTipper.onRenderGui(graphics));
    }
}
