package com.emma.endinv.network.payloads.toServer;

import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.menu.Station;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

public record SetActiveStationPayload(Station station) implements ModPacketPayload {

    public static void encode(SetActiveStationPayload payload, FriendlyByteBuf buf) {
        buf.writeByte(payload.station.ordinal());
    }

    public static SetActiveStationPayload decode(FriendlyByteBuf buf) {
        int ordinal = buf.readByte() & 0xFF;
        Station[] values = Station.values();
        return new SetActiveStationPayload(ordinal < values.length ? values[ordinal] : Station.NONE);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        encode(this, buffer);
    }

    @Override
    public String id() {
        return "set_active_station";
    }

    @Override
    public void handle(ModPacketContext context) {
        Player player = context.player();
        if (player == null) return;
        if (player.containerMenu instanceof EndlessInventoryMenu menu) {
            menu.setActiveStation(station);
            menu.broadcastChanges();
        }
    }
}
