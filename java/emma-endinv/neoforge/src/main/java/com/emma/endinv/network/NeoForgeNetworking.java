package com.emma.endinv.network;

import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toClient.*;
import com.emma.endinv.network.payloads.toServer.*;
import com.emma.endinv.storage.StorageIndexPayload;
import com.emma.endinv.storage.StorageRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeNetworking {

    private NeoForgeNetworking() {}

    public static void register(IEventBus eventBus) {
        eventBus.addListener(NeoForgeNetworking::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // optional(): lets NeoForge clients join servers without NeoForge (the Folia/Paper plugin),
        // where the payloads travel over the channels the plugin registers.
        PayloadRegistrar reg = event.registrar(com.emma.endinv.ModInfo.MOD_ID).optional();

        // C2S
        reg.playToServer(ItemClickPayload.TYPE, ItemClickPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));
        reg.playToServer(CreativeItemModPayload.TYPE, CreativeItemModPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));
        reg.playToServer(ItemPageContext.TYPE, ItemPageContext.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));
        reg.playToServer(OpenEndInvPayload.TYPE, OpenEndInvPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));
        reg.playToServer(QuickMoveToPagePayload.TYPE, QuickMoveToPagePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));
        reg.playToServer(BulkQuickMoveFromPagePayload.TYPE, BulkQuickMoveFromPagePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));
        reg.playToServer(StarItemPayload.TYPE, StarItemPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));
        reg.playToServer(SetActiveStationPayload.TYPE, SetActiveStationPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));
        reg.playToServer(SwapMenuSlotPayload.TYPE, SwapMenuSlotPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));
        reg.playToServer(ManageEndInvPayload.TYPE, ManageEndInvPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));
        reg.playToServer(StorageRequestPayload.TYPE, StorageRequestPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(serverCtx((ServerPlayer) ctx.player()))));

        // S2C
        reg.playToClient(EndInvContent.TYPE, EndInvContent.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(clientCtx(ctx.player()))));
        reg.playToClient(EndInvMetadata.TYPE, EndInvMetadata.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(clientCtx(ctx.player()))));
        reg.playToClient(ItemPickedUpPayload.TYPE, ItemPickedUpPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(clientCtx(ctx.player()))));
        reg.playToClient(SetItemDisplayContentPayload.TYPE, SetItemDisplayContentPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(clientCtx(ctx.player()))));
        reg.playToClient(SetStarredPagePayload.TYPE, SetStarredPagePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(clientCtx(ctx.player()))));
        reg.playToClient(MenuAttachabilityPayload.TYPE, MenuAttachabilityPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(clientCtx(ctx.player()))));
        reg.playToClient(EndInvListPayload.TYPE, EndInvListPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(clientCtx(ctx.player()))));
        reg.playToClient(EndInvDetailPayload.TYPE, EndInvDetailPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(clientCtx(ctx.player()))));
        reg.playToClient(StorageIndexPayload.TYPE, StorageIndexPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(clientCtx(ctx.player()))));

        // NeoForge keys payload registrations by ID rather than direction, so the
        // shared settings type must be registered once with both handlers.
        reg.playBidirectional(
                SyncedConfig.TYPE,
                SyncedConfig.STREAM_CODEC,
                // Pick the side from the player itself: the two-handler form ran the server
                // handler on the client (LocalPlayer cast to ServerPlayer).
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(
                        ctx.player() instanceof ServerPlayer sp ? serverCtx(sp) : clientCtx(ctx.player()))),
                (payload, ctx) -> ctx.enqueueWork(() -> payload.handle(
                        ctx.player() instanceof ServerPlayer sp ? serverCtx(sp) : clientCtx(ctx.player()))));
    }

    private static ModPacketContext serverCtx(ServerPlayer player) { return () -> player; }
    private static ModPacketContext clientCtx(net.minecraft.world.entity.player.Player player) { return () -> player; }
}
