package com.emma.endinv;

import com.emma.endinv.network.IPacketDistributor;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

public class NeoForgePacketDistributor implements IPacketDistributor {

    @Override
    public void sendToServer(ModPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ModPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToAllPlayer(ModPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }
}
