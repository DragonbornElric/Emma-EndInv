package com.emma.endinv.folia;

import com.emma.endinv.network.IPacketDistributor;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
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
            player.connection.send(new ClientboundCustomPayloadPacket(payload));
        }
    }

    @Override
    public void sendToAllPlayer(ModPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(player, payload);
        }
    }
}
