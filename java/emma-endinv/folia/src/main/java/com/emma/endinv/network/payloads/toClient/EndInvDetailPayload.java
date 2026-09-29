package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.network.BufCollections;
import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.util.ItemStackLike;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

// Server-only override: identical wire format, no client-side handler.
/** Read-only contents of one EndInv for the manager screen, largest stacks first. {@code totalTypes} may exceed {@code items.size()} when truncated. */
public record EndInvDetailPayload(UUID inventoryId, List<ItemStackLike> items, int totalTypes) implements ModPacketPayload {

    public static void encode(EndInvDetailPayload payload, RegistryFriendlyByteBuf o) {
        UUIDUtil.STREAM_CODEC.encode(o, payload.inventoryId);
        BufCollections.writeCollection(o, payload.items, (buf, like) -> ItemStackLike.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, like));
        o.writeVarInt(payload.totalTypes);
    }

    public static EndInvDetailPayload decode(RegistryFriendlyByteBuf o) {
        return new EndInvDetailPayload(
                UUIDUtil.STREAM_CODEC.decode(o),
                BufCollections.readList(o, buf -> ItemStackLike.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf)),
                o.readVarInt());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, EndInvDetailPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> encode(value, buf), EndInvDetailPayload::decode);

    public static final CustomPacketPayload.Type<EndInvDetailPayload> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("endinv_detail"));

    @Override
    public String id() { return "endinv_detail"; }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public void handle(ModPacketContext context) {}
}
