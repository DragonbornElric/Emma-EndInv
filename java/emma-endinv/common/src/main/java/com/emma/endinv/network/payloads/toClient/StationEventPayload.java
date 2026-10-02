package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.api.EmmaEndInvApi;
import com.emma.endinv.api.StationEvent;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** A station in the player's EndInv finished or burned out (see {@code StationNotifications}). */
public record StationEventPayload(StationEvent event) implements ModPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, StationEventPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM).encode(buf, value.event.station());
                buf.writeVarInt(value.event.reason().ordinal());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, value.event.result());
            },
            buf -> {
                var station = ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM).decode(buf);
                StationEvent.Reason[] reasons = StationEvent.Reason.values();
                int r = buf.readVarInt();
                StationEvent.Reason reason = r >= 0 && r < reasons.length ? reasons[r] : StationEvent.Reason.DONE;
                return new StationEventPayload(new StationEvent(station, reason, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
            });

    public static final CustomPacketPayload.Type<StationEventPayload> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("station_event"));

    @Override
    public String id() {
        return "station_event";
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ModPacketContext context) {
        EmmaEndInvApi.fireStationEvent(event);
    }
}
