package com.emma.endinv.network.payloads.toServer;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.menu.Station;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record SetActiveStationPayload(Station station) implements ModPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, SetActiveStationPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> encode(value, buf), SetActiveStationPayload::decode);

    public static final CustomPacketPayload.Type<SetActiveStationPayload> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("set_active_station"));

    public static void encode(SetActiveStationPayload payload, FriendlyByteBuf buf) {
        buf.writeByte(payload.station.ordinal());
    }

    public static SetActiveStationPayload decode(FriendlyByteBuf buf) {
        int ordinal = buf.readByte() & 0xFF;
        Station[] values = Station.values();
        return new SetActiveStationPayload(ordinal < values.length ? values[ordinal] : Station.NONE);
    }

    @Override
    public String id() {
        return "set_active_station";
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void handle(ModPacketContext context) {
        Player player = context.player();
        if (player == null) return;
        if (player.containerMenu instanceof EndlessInventoryMenu menu) {
            // A locked station (FreeCraftingStations = false) can't be opened; the client follows the synced mask.
            menu.setActiveStation(menu.isStationUnlocked(station) ? station : Station.NONE);
            menu.broadcastChanges();
        }
    }
}
