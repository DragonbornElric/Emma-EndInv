package com.emma.endinv.network;

import com.emma.endinv.AbstractModInitializer;
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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Minecraft 1.20.1 predates typed custom payloads. Keep one explicit registry
 * for the complete Emma wire surface and adapt each common payload codec to
 * Fabric's ResourceLocation/FriendlyByteBuf channels.
 */
public final class FabricNetworking {

    private static final Map<Class<? extends ModPacketPayload>, PayloadRegistration<? extends ModPacketPayload>>
            CLIENTBOUND = new HashMap<>();
    private static final Map<Class<? extends ModPacketPayload>, PayloadRegistration<? extends ModPacketPayload>>
            SERVERBOUND = new HashMap<>();
    private static final List<PayloadRegistration<? extends ModPacketPayload>> CLIENTBOUND_REGISTRATIONS =
            new ArrayList<>();
    private static final List<PayloadRegistration<? extends ModPacketPayload>> SERVERBOUND_REGISTRATIONS =
            new ArrayList<>();

    private static boolean initialized;

    private FabricNetworking() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        registerClientbound(EndInvContent.class, EndInvContent::encode, EndInvContent::decode, "endinv_content");
        registerClientbound(EndInvMetadata.class, EndInvMetadata::encode, EndInvMetadata::decode, "endinv_meta");
        registerClientbound(ItemPickedUpPayload.class, ItemPickedUpPayload::encode, ItemPickedUpPayload::decode, "auto_picked");
        registerClientbound(SetItemDisplayContentPayload.class, SetItemDisplayContentPayload::encode, SetItemDisplayContentPayload::decode, "itemdisplay_content");
        registerClientbound(SetStarredPagePayload.class, SetStarredPagePayload::encode, SetStarredPagePayload::decode, "starred_item");
        registerClientbound(MenuAttachabilityPayload.class, MenuAttachabilityPayload::encode, MenuAttachabilityPayload::decode, "menu_attachability");
        registerClientbound(SyncedConfig.class, SyncedConfig::encode, SyncedConfig::decode, "endinv_settings");

        registerServerbound(ItemClickPayload.class, ItemClickPayload::encode, ItemClickPayload::decode, "item_click");
        registerServerbound(CreativeItemModPayload.class, CreativeItemModPayload::encode, CreativeItemModPayload::decode, "item_modify");
        registerServerbound(ItemPageContext.class, ItemPageContext::encode, ItemPageContext::decode, "page_context");
        registerServerbound(OpenEndInvPayload.class, OpenEndInvPayload::encode, OpenEndInvPayload::decode, "open_endinv");
        registerServerbound(QuickMoveToPagePayload.class, QuickMoveToPagePayload::encode, QuickMoveToPagePayload::decode, "quick_move_page");
        registerServerbound(BulkQuickMoveFromPagePayload.class, BulkQuickMoveFromPagePayload::encode, BulkQuickMoveFromPagePayload::decode, "bulk_quick_move_from_page");
        registerServerbound(StarItemPayload.class, StarItemPayload::encode, StarItemPayload::decode, "star_item");
        registerServerbound(SetActiveStationPayload.class, SetActiveStationPayload::encode, SetActiveStationPayload::decode, "set_active_station");
        registerServerbound(SyncedConfig.class, SyncedConfig::encode, SyncedConfig::decode, "endinv_settings");
        registerServerbound(SwapMenuSlotPayload.class, SwapMenuSlotPayload::encode, SwapMenuSlotPayload::decode, "swap_menu_slot");
    }

    static synchronized List<PayloadRegistration<? extends ModPacketPayload>> serverboundRegistrations() {
        init();
        return List.copyOf(SERVERBOUND_REGISTRATIONS);
    }

    static synchronized List<PayloadRegistration<? extends ModPacketPayload>> clientboundRegistrations() {
        init();
        return List.copyOf(CLIENTBOUND_REGISTRATIONS);
    }

    static PayloadRegistration<ModPacketPayload> serverbound(Class<?> payloadType) {
        init();
        return getRegistration(SERVERBOUND, payloadType);
    }

    static PayloadRegistration<ModPacketPayload> clientbound(Class<?> payloadType) {
        init();
        return getRegistration(CLIENTBOUND, payloadType);
    }

    private static <T extends ModPacketPayload> void registerClientbound(
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            String id
    ) {
        PayloadRegistration<T> registration =
                new PayloadRegistration<>(AbstractModInitializer.withModLocation(id), encoder, decoder);
        CLIENTBOUND.put(type, registration);
        CLIENTBOUND_REGISTRATIONS.add(registration);
    }

    private static <T extends ModPacketPayload> void registerServerbound(
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            String id
    ) {
        PayloadRegistration<T> registration =
                new PayloadRegistration<>(AbstractModInitializer.withModLocation(id), encoder, decoder);
        SERVERBOUND.put(type, registration);
        SERVERBOUND_REGISTRATIONS.add(registration);
    }

    @SuppressWarnings("unchecked")
    private static PayloadRegistration<ModPacketPayload> getRegistration(
            Map<Class<? extends ModPacketPayload>, PayloadRegistration<? extends ModPacketPayload>> registrations,
            Class<?> type
    ) {
        PayloadRegistration<? extends ModPacketPayload> registration = registrations.get(type);
        if (registration == null) {
            throw new IllegalStateException("Unregistered EndInv payload type: " + type.getName());
        }
        return (PayloadRegistration<ModPacketPayload>) registration;
    }

    record PayloadRegistration<T extends ModPacketPayload>(
            ResourceLocation id,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder
    ) {
        void encode(ModPacketPayload payload, FriendlyByteBuf buffer) {
            encoder.accept((T) payload, buffer);
        }

        T decode(FriendlyByteBuf buffer) {
            return decoder.apply(buffer);
        }
    }
}
