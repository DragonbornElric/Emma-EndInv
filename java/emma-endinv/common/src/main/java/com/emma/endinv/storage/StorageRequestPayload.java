package com.emma.endinv.storage;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Client request from the storage tracker screen. {@code dimension}/{@code pos} name a tracked container and are
 * ignored for {@link Action#LIST}. The server answers every request with a fresh {@link StorageIndexPayload}.
 */
public record StorageRequestPayload(Action action, ResourceKey<Level> dimension, BlockPos pos) implements ModPacketPayload {

    public enum Action {
        LIST,
        UNTRACK
    }

    public static StorageRequestPayload list() {
        return new StorageRequestPayload(Action.LIST, Level.OVERWORLD, BlockPos.ZERO);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeEnum(p.action);
                buf.writeResourceKey(p.dimension);
                buf.writeBlockPos(p.pos);
            },
            buf -> new StorageRequestPayload(buf.readEnum(Action.class), buf.readResourceKey(Registries.DIMENSION), buf.readBlockPos()));

    public static final CustomPacketPayload.Type<StorageRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("storage_request"));

    @Override
    public String id() { return "storage_request"; }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public void handle(ModPacketContext context) {
        if (context.player() instanceof ServerPlayer player) {
            StorageTracker.handleRequest(player, this);
        }
    }
}
