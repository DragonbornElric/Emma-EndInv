package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemState;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Map;

// Server-side stub: handle() is a no-op (toClient payloads are sent, never received, on server).
public record EndInvContent(Map<ItemKey, ItemState> itemMap) implements ModPacketPayload {

    public static void encode(RegistryFriendlyByteBuf o, EndInvContent content) {
        o.writeMap(
                content.itemMap,
                (buf, key) -> ItemKey.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, key),
                ItemState::encode
        );
    }

    public static EndInvContent decode(RegistryFriendlyByteBuf o) {
        return new EndInvContent(o.readMap(Object2ObjectLinkedOpenHashMap::new,
                buf -> ItemKey.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf),
                ItemState::decode
        ));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, EndInvContent> STREAM_CODEC =
            StreamCodec.of(EndInvContent::encode, EndInvContent::decode);

    public static final CustomPacketPayload.Type<EndInvContent> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("endinv_content"));

    @Override
    public String id() { return "endinv_content"; }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public void handle(ModPacketContext context) {}
}
