package com.emma.endinv.folia.scheduler;

import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.plugin.Plugin;

/** Hops inbound plugin-message payloads from the Netty thread to the player's region thread. */
public final class PayloadDispatch {

    private PayloadDispatch() {}

    /**
     * Decode a payload (already decoded on the Netty thread) then dispatch handle()
     * on the player entity's owning region thread. Safe to call from the Netty I/O thread.
     */
    public static void dispatch(Plugin plugin, ServerPlayer serverPlayer, ModPacketPayload payload) {
        CraftPlayer craftPlayer = (CraftPlayer) serverPlayer.getBukkitEntity();
        craftPlayer.getScheduler().run(plugin, sch -> {
            payload.handle(() -> serverPlayer);
        }, null);
    }
}
