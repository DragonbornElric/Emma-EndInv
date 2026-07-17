package com.emma.endinv.network;

import com.emma.endinv.network.payloads.ModPacketPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

public final class FabricClientNetworking {

    private static boolean receiversRegistered;

    private FabricClientNetworking() {
    }

    public static synchronized void init() {
        if (receiversRegistered) {
            return;
        }
        receiversRegistered = true;
        for (FabricNetworking.PayloadRegistration<? extends ModPacketPayload> registration
                : FabricNetworking.clientboundRegistrations()) {
            registerReceiver(registration);
        }
    }

    public static void sendToServer(ModPacketPayload payload) {
        FabricNetworking.PayloadRegistration<ModPacketPayload> registration =
                FabricNetworking.serverbound(payload.getClass());
        FriendlyByteBuf buffer = PacketByteBufs.create();
        registration.encode(payload, buffer);
        ClientPlayNetworking.send(registration.id(), buffer);
    }

    private static <T extends ModPacketPayload> void registerReceiver(
            FabricNetworking.PayloadRegistration<T> registration
    ) {
        ClientPlayNetworking.registerGlobalReceiver(
                registration.id(),
                (client, handler, buffer, responseSender) -> {
                    T payload = registration.decode(buffer);
                    client.execute(() -> payload.handle(() -> client.player));
                }
        );
    }
}
