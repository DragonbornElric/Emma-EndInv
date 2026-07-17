package com.emma.endinv;

import com.emma.endinv.network.IPacketDistributor;
import com.emma.endinv.network.NeoForgeNetworking;
import com.emma.endinv.network.payloads.ModPacketPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

public class NeoForgePacketDistributor implements IPacketDistributor {

    @Override
    public void sendToServer(ModPacketPayload payload) {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> EndInvNeoForgeClient.sendToServer(payload));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ModPacketPayload payload) {
        if (player == null || player.connection == null) return;
        if (!NeoForgeNetworking.canSend(
                player.connection.connection, payload.payloadId())) {
            return;
        }

        FriendlyByteBuf buffer = encode(payload);
        try {
            PacketDistributor.PLAYER.with(() -> player).send(
                    new ClientboundCustomPayloadPacket(payload.payloadId(), buffer));
        } catch (RuntimeException exception) {
            buffer.release();
            throw exception;
        }
    }

    @Override
    public void sendToAllPlayer(ModPacketPayload payload) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(player, payload);
        }
    }

    private static FriendlyByteBuf encode(ModPacketPayload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            payload.write(buffer);
            return buffer;
        } catch (RuntimeException exception) {
            buffer.release();
            throw exception;
        }
    }
}
