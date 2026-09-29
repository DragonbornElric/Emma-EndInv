package com.emma.endinv;

import com.emma.endinv.network.IPacketDistributor;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.network.FabricClientNetworking;
import com.emma.endinv.network.FabricNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class FabricPacketDistributor implements IPacketDistributor {

    @Override
    public void sendToServer(ModPacketPayload payload) {
        FabricClientNetworking.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ModPacketPayload payload) {
        FabricNetworking.sendToPlayer(player, payload);
    }

    /** Set by PlayerEvents from the server lifecycle; null on a client with no server running. */
    public static volatile MinecraftServer server;

    @Override
    public void sendToAllPlayer(ModPacketPayload payload) {
        MinecraftServer current = server;
        if (current == null) return;
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            sendToPlayer(player, payload);
        }
    }
}
