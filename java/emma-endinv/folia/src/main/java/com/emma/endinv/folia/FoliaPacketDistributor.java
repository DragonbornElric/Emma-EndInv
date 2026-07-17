package com.emma.endinv.folia;

import com.emma.endinv.network.IPacketDistributor;
import com.emma.endinv.network.payloads.ModPacketPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sends S2C payloads via NMS connection. Thread-safe — Netty handles the write. */
public final class FoliaPacketDistributor implements IPacketDistributor {

    private final MinecraftServer server;

    public FoliaPacketDistributor(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void sendToServer(ModPacketPayload payload) {
        // server never sends to itself
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ModPacketPayload payload) {
        if (player != null && player.connection != null) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            boolean handedOff = false;
            try {
                payload.write(buffer);
                ClientboundCustomPayloadPacket packet =
                        new ClientboundCustomPayloadPacket(payload.payloadId(), buffer);
                // The 1.20.1 packet stores this buffer by reference. Once
                // Connection#send returns, the queued packet owns it.
                player.connection.send(packet);
                handedOff = true;
            } finally {
                // Release locally only when encoding, construction, or the
                // synchronous handoff failed.
                if (!handedOff) {
                    buffer.release();
                }
            }
        }
    }

    @Override
    public void sendToAllPlayer(ModPacketPayload payload) {
        for (ServerPlayer player :
                java.util.List.copyOf(server.getPlayerList().getPlayers())) {
            sendToPlayer(player, payload);
        }
    }
}
