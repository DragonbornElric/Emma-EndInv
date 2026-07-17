package com.emma.endinv.event;

import com.emma.endinv.autopick.AutoPickHelper;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;

public final class FabricBlockBreakEvents {

    private FabricBlockBreakEvents() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer sp && AutoPickHelper.isEnabled(sp)) {
                BlockBreakRedirect.pushBreaker(sp);
            } else {
                BlockBreakRedirect.clearBreaker();
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            BlockBreakRedirect.clearBreaker();
        });
    }
}
