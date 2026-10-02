package com.emma.endinv.network;

import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toClient.*;
import com.emma.endinv.storage.StorageIndexPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class FabricClientNetworking {

    private FabricClientNetworking() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(EndInvContent.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
        ClientPlayNetworking.registerGlobalReceiver(EndInvMetadata.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
        ClientPlayNetworking.registerGlobalReceiver(ItemPickedUpPayload.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
        ClientPlayNetworking.registerGlobalReceiver(com.emma.endinv.network.payloads.toClient.StationEventPayload.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
        ClientPlayNetworking.registerGlobalReceiver(SetItemDisplayContentPayload.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
        ClientPlayNetworking.registerGlobalReceiver(SetStarredPagePayload.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
        ClientPlayNetworking.registerGlobalReceiver(MenuAttachabilityPayload.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
        ClientPlayNetworking.registerGlobalReceiver(SyncedConfig.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
        ClientPlayNetworking.registerGlobalReceiver(EndInvListPayload.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
        ClientPlayNetworking.registerGlobalReceiver(EndInvDetailPayload.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
        ClientPlayNetworking.registerGlobalReceiver(StorageIndexPayload.TYPE,
                (payload, context) -> context.client().execute(() -> payload.handle(context(context.player()))));
    }

    private static ModPacketContext context(net.minecraft.world.entity.player.Player player) { return () -> player; }

    public static void sendToServer(ModPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
