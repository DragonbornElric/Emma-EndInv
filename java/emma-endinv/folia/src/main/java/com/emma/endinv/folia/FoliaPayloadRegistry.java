package com.emma.endinv.folia;

import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toClient.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Injects EndInv S2C payload codecs into {@code ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC}
 * so Folia can encode outbound packets for the client mod.
 * Uses sun.misc.Unsafe to bypass the unmodifiable-map capture in the anonymous StreamCodec class.
 * Call once during {@code onEnable}, before any packets are sent.
 */
public final class FoliaPayloadRegistry {

    private FoliaPayloadRegistry() {}

    @SuppressWarnings("unchecked")
    public static void register() {
        try {
            Map<Identifier, StreamCodec<? super RegistryFriendlyByteBuf, ? extends CustomPacketPayload>> codecs = new HashMap<>();
            codecs.put(SyncedConfig.TYPE.id(), SyncedConfig.STREAM_CODEC);
            codecs.put(EndInvContent.TYPE.id(), EndInvContent.STREAM_CODEC);
            codecs.put(EndInvMetadata.TYPE.id(), EndInvMetadata.STREAM_CODEC);
            codecs.put(ItemPickedUpPayload.TYPE.id(), ItemPickedUpPayload.STREAM_CODEC);
            codecs.put(com.emma.endinv.network.payloads.toClient.StationEventPayload.TYPE.id(), com.emma.endinv.network.payloads.toClient.StationEventPayload.STREAM_CODEC);
            codecs.put(SetItemDisplayContentPayload.TYPE.id(), SetItemDisplayContentPayload.STREAM_CODEC);
            codecs.put(SetStarredPagePayload.TYPE.id(), SetStarredPagePayload.STREAM_CODEC);
            codecs.put(MenuAttachabilityPayload.TYPE.id(), MenuAttachabilityPayload.STREAM_CODEC);
            codecs.put(EndInvListPayload.TYPE.id(), EndInvListPayload.STREAM_CODEC);
            codecs.put(EndInvDetailPayload.TYPE.id(), EndInvDetailPayload.STREAM_CODEC);
            codecs.put(com.emma.endinv.storage.StorageIndexPayload.TYPE.id(), com.emma.endinv.storage.StorageIndexPayload.STREAM_CODEC);

            Field gameplayField = ClientboundCustomPayloadPacket.class.getDeclaredField("GAMEPLAY_STREAM_CODEC");
            gameplayField.setAccessible(true);
            Object mappedCodec = gameplayField.get(null);

            Object innerCodec = findFieldValueAssignableTo(mappedCodec, StreamCodec.class);
            if (innerCodec == null) {
                throw new IllegalStateException("Could not find inner StreamCodec in GAMEPLAY_STREAM_CODEC");
            }

            Field mapField = findFieldAssignableTo(innerCodec, Map.class);
            if (mapField == null) {
                throw new IllegalStateException("Could not find idToType Map in inner StreamCodec");
            }

            Map<Identifier, StreamCodec<?, ?>> original = (Map<Identifier, StreamCodec<?, ?>>) mapField.get(innerCodec);
            HashMap<Identifier, StreamCodec<?, ?>> newMap = new HashMap<>(original);
            newMap.putAll((Map) codecs);

            sun.misc.Unsafe unsafe = getUnsafe();
            unsafe.putObject(innerCodec, unsafe.objectFieldOffset(mapField), newMap);

        } catch (Exception e) {
            throw new RuntimeException("Failed to register EndInv payload codecs", e);
        }
    }

    private static Object findFieldValueAssignableTo(Object obj, Class<?> type) throws Exception {
        for (Field f : obj.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object val = f.get(obj);
            if (val != null && type.isAssignableFrom(val.getClass()) && val != obj) return val;
        }
        return null;
    }

    private static Field findFieldAssignableTo(Object obj, Class<?> type) throws Exception {
        for (Field f : obj.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object val = f.get(obj);
            if (val != null && type.isAssignableFrom(val.getClass())) return f;
        }
        return null;
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }
}
