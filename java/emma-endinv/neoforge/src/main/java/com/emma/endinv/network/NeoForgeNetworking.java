package com.emma.endinv.network;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.EndInvNeoForgeClient;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toClient.EndInvContent;
import com.emma.endinv.network.payloads.toClient.EndInvMetadata;
import com.emma.endinv.network.payloads.toClient.ItemPickedUpPayload;
import com.emma.endinv.network.payloads.toClient.MenuAttachabilityPayload;
import com.emma.endinv.network.payloads.toClient.SetItemDisplayContentPayload;
import com.emma.endinv.network.payloads.toClient.SetStarredPagePayload;
import com.emma.endinv.network.payloads.toServer.BulkQuickMoveFromPagePayload;
import com.emma.endinv.network.payloads.toServer.CreativeItemModPayload;
import com.emma.endinv.network.payloads.toServer.ItemClickPayload;
import com.emma.endinv.network.payloads.toServer.ItemPageContext;
import com.emma.endinv.network.payloads.toServer.OpenEndInvPayload;
import com.emma.endinv.network.payloads.toServer.QuickMoveToPagePayload;
import com.emma.endinv.network.payloads.toServer.SetActiveStationPayload;
import com.emma.endinv.network.payloads.toServer.StarItemPayload;
import com.emma.endinv.network.payloads.toServer.SwapMenuSlotPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Raw per-payload networking for the Forge-47 API boundary.
 *
 * <p>Every Emma loader uses the same vanilla custom-payload channel and the
 * same {@link FriendlyByteBuf} field layout. This intentionally does not use
 * Forge {@code SimpleChannel}: numeric discriminators would make the Forge
 * wire format incompatible with Fabric and Folia.</p>
 */
public final class NeoForgeNetworking {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROTOCOL = "1";
    private static final Map<ResourceLocation, EventNetworkChannel> CHANNELS =
            new HashMap<>();
    private static boolean registered;

    private NeoForgeNetworking() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        // Client -> server
        registerToServer("item_click", ItemClickPayload::decode);
        registerToServer("item_modify", CreativeItemModPayload::decode);
        registerToServer("page_context", ItemPageContext::decode);
        registerToServer("open_endinv", OpenEndInvPayload::decode);
        registerToServer("quick_move_page", QuickMoveToPagePayload::decode);
        registerToServer(
                "bulk_quick_move_from_page", BulkQuickMoveFromPagePayload::decode);
        registerToServer("star_item", StarItemPayload::decode);
        registerToServer("set_active_station", SetActiveStationPayload::decode);
        registerToServer("swap_menu_slot", SwapMenuSlotPayload::decode);

        // Server -> client
        registerToClient("endinv_content", EndInvContent::decode);
        registerToClient("endinv_meta", EndInvMetadata::decode);
        registerToClient("auto_picked", ItemPickedUpPayload::decode);
        registerToClient("itemdisplay_content", SetItemDisplayContentPayload::decode);
        registerToClient("starred_item", SetStarredPagePayload::decode);
        registerToClient("menu_attachability", MenuAttachabilityPayload::decode);

        // The settings channel is intentionally bidirectional.
        EventNetworkChannel settings = createChannel("endinv_settings");
        settings.addListener((NetworkEvent.ClientCustomPayloadEvent event) ->
                receiveOnServer(event, SyncedConfig::decode));
        settings.addListener((NetworkEvent.ServerCustomPayloadEvent event) ->
                receiveOnClient(event, SyncedConfig::decode));
    }

    public static boolean canSend(Connection connection, ResourceLocation payloadId) {
        EventNetworkChannel channel = CHANNELS.get(payloadId);
        return connection != null && channel != null && channel.isRemotePresent(connection);
    }

    private static <T extends ModPacketPayload> void registerToServer(
            String path,
            Function<FriendlyByteBuf, T> decoder
    ) {
        createChannel(path).addListener(
                (NetworkEvent.ClientCustomPayloadEvent event) ->
                        receiveOnServer(event, decoder));
    }

    private static <T extends ModPacketPayload> void registerToClient(
            String path,
            Function<FriendlyByteBuf, T> decoder
    ) {
        createChannel(path).addListener(
                (NetworkEvent.ServerCustomPayloadEvent event) ->
                        receiveOnClient(event, decoder));
    }

    private static EventNetworkChannel createChannel(String path) {
        ResourceLocation id = AbstractModInitializer.withModLocation(path);
        EventNetworkChannel channel = NetworkRegistry.newEventChannel(
                id,
                () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL),
                NetworkRegistry.acceptMissingOr(PROTOCOL)
        );
        CHANNELS.put(id, channel);
        return channel;
    }

    private static <T extends ModPacketPayload> void receiveOnServer(
            NetworkEvent.ClientCustomPayloadEvent event,
            Function<FriendlyByteBuf, T> decoder
    ) {
        NetworkEvent.Context context = event.getSource().get();
        try {
            T payload = decoder.apply(event.getPayload());
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                context.enqueueWork(() -> payload.handle(serverContext(sender)));
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to decode EndInv C2S payload", exception);
        } finally {
            context.setPacketHandled(true);
        }
    }

    private static <T extends ModPacketPayload> void receiveOnClient(
            NetworkEvent.ServerCustomPayloadEvent event,
            Function<FriendlyByteBuf, T> decoder
    ) {
        NetworkEvent.Context context = event.getSource().get();
        try {
            T payload = decoder.apply(event.getPayload());
            context.enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> () -> EndInvNeoForgeClient.handlePayload(payload)));
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to decode EndInv S2C payload", exception);
        } finally {
            context.setPacketHandled(true);
        }
    }

    private static ModPacketContext serverContext(ServerPlayer player) {
        return () -> player;
    }
}
