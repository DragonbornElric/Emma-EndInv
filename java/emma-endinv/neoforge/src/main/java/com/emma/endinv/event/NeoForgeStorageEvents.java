package com.emma.endinv.event;

import com.emma.endinv.storage.StorageCommand;
import com.emma.endinv.storage.StorageIndex;
import com.emma.endinv.storage.StorageTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Storage tracker hooks: tagging containers, polling tracked containers, loading/saving the index, /storage. */
public final class NeoForgeStorageEvents {

    private NeoForgeStorageEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> StorageIndex.load(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> StorageIndex.unload(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> StorageTracker.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> StorageCommand.register(e.getDispatcher()));
        // Fires on both sides; cancelling on the client stops it opening the container and still sends the click.
        NeoForge.EVENT_BUS.addListener(NeoForgeStorageEvents::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!StorageTracker.shouldIntercept(event.getEntity(), event.getHand(), event.getLevel(), event.getPos())) return;
        if (event.getEntity() instanceof ServerPlayer player) StorageTracker.onUseBlock(player, event.getHand(), event.getPos());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
