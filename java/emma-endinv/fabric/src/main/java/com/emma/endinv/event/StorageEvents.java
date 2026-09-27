package com.emma.endinv.event;

import com.emma.endinv.storage.StorageIndex;
import com.emma.endinv.storage.StorageTracker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Storage tracker hooks: tagging containers, polling tracked containers, and loading/saving the index. */
public final class StorageEvents {

    private StorageEvents() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(StorageIndex::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(StorageIndex::unload);
        ServerTickEvents.END_SERVER_TICK.register(StorageTracker::tick);
        // Runs on both sides; returning SUCCESS on the client stops it opening the container and still sends the click.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!StorageTracker.shouldIntercept(player, hand, level, hit.getBlockPos())) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) StorageTracker.onUseBlock(serverPlayer, hand, hit.getBlockPos());
            return InteractionResult.SUCCESS;
        });
    }
}
