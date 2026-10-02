package com.emma.endinv.network.payloads.toServer;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.menu.Station;
import com.emma.endinv.menu.StationUnlocks;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * Unlock a station ({@code FreeCraftingStations = false}) with one of its blocks from the player's
 * inventory or EndInv, with no screen open. Sent by {@code EmmaEndInvApi.unlockStation}; does
 * nothing when the station is already unlocked or the player has no such block.
 */
public record UnlockStationPayload(Station station) implements ModPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, UnlockStationPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> encode(value, buf), UnlockStationPayload::decode);

    public static final CustomPacketPayload.Type<UnlockStationPayload> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("unlock_station"));

    public static void encode(UnlockStationPayload payload, FriendlyByteBuf buf) {
        buf.writeByte(payload.station.ordinal());
    }

    public static UnlockStationPayload decode(FriendlyByteBuf buf) {
        int ordinal = buf.readByte() & 0xFF;
        Station[] values = Station.values();
        return new UnlockStationPayload(ordinal < values.length ? values[ordinal] : Station.NONE);
    }

    @Override
    public String id() {
        return "unlock_station";
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void handle(ModPacketContext context) {
        Player player = context.player();
        if (player == null || station == Station.NONE) return;
        ServerLevelEndInv.getEndInvForPlayer(player)
                .ifPresent(endInv -> StationUnlocks.tryUnlockFromStorage(player, endInv, station));
    }
}
