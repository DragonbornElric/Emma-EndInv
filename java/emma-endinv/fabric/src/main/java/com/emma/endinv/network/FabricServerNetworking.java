package com.emma.endinv.network;

import com.emma.endinv.network.payloads.ModPacketPayload;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class FabricServerNetworking {

    private static boolean receiversRegistered;

    private FabricServerNetworking() {
    }

    public static synchronized void init() {
        if (receiversRegistered) {
            return;
        }
        receiversRegistered = true;
        for (FabricNetworking.PayloadRegistration<? extends ModPacketPayload> registration
                : FabricNetworking.serverboundRegistrations()) {
            registerReceiver(registration);
        }
    }

    public static void sendToPlayer(ServerPlayer player, ModPacketPayload payload) {
        FabricNetworking.PayloadRegistration<ModPacketPayload> registration =
                FabricNetworking.clientbound(payload.getClass());
        FriendlyByteBuf buffer = PacketByteBufs.create();
        registration.encode(payload, buffer);
        ServerPlayNetworking.send(player, registration.id(), buffer);
    }

    private static <T extends ModPacketPayload> void registerReceiver(
            FabricNetworking.PayloadRegistration<T> registration
    ) {
        ServerPlayNetworking.registerGlobalReceiver(
                registration.id(),
                (server, player, handler, buffer, responseSender) -> {
                    T payload = registration.decode(buffer);
                    server.execute(() -> payload.handle(() -> player));
                }
        );
    }
}
