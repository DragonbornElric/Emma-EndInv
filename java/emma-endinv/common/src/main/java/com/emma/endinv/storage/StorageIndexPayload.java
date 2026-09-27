package com.emma.endinv.storage;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.function.Consumer;

/**
 * One part of the storage index sent to a client. Large indexes are split into several parts to stay under the
 * custom payload size limit; the client resets on {@code part == 0} and has the full index once {@code last} is set.
 *
 * <p>Lives outside {@code network/payloads/toClient} so the Folia build (which excludes that package) can encode it.
 * The client handler is installed through {@link #clientHandler} instead of referenced directly, keeping this class
 * free of client-only code.
 */
public record StorageIndexPayload(int part, boolean last, boolean admin, String message,
                                  List<TrackedContainer> containers) implements ModPacketPayload {

    /** Set by the client initializer; a no-op on dedicated servers. */
    public static volatile Consumer<StorageIndexPayload> clientHandler = payload -> {};

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageIndexPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.part);
                buf.writeBoolean(p.last);
                buf.writeBoolean(p.admin);
                buf.writeUtf(p.message);
                buf.writeCollection(p.containers, (b, c) -> TrackedContainer.encode((RegistryFriendlyByteBuf) b, c));
            },
            buf -> new StorageIndexPayload(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(),
                    buf.readList(b -> TrackedContainer.decode((RegistryFriendlyByteBuf) b))));

    public static final CustomPacketPayload.Type<StorageIndexPayload> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("storage_index"));

    @Override
    public String id() { return "storage_index"; }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public void handle(ModPacketContext context) {
        clientHandler.accept(this);
    }
}
