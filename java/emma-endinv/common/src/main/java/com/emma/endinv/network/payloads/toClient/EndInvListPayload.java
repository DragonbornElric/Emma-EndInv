package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.network.BufCollections;
import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.manage.EndInvSummary;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Inventory manager list: every EndInv the player may see, plus the result message of the last action. */
public record EndInvListPayload(boolean admin, List<EndInvSummary> entries, String message) implements ModPacketPayload {

    public static void encode(EndInvListPayload payload, FriendlyByteBuf o) {
        o.writeBoolean(payload.admin);
        BufCollections.writeCollection(o, payload.entries, (buf, entry) -> EndInvSummary.encode(entry, buf));
        o.writeUtf(payload.message);
    }

    public static EndInvListPayload decode(FriendlyByteBuf o) {
        return new EndInvListPayload(o.readBoolean(), BufCollections.readList(o, EndInvSummary::decode), o.readUtf());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, EndInvListPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> encode(value, buf), EndInvListPayload::decode);

    public static final CustomPacketPayload.Type<EndInvListPayload> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("endinv_list"));

    @Override
    public String id() { return "endinv_list"; }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public void handle(ModPacketContext context) {
        com.emma.endinv.client.gui.EndInvManagerScreen.onList(this);
    }
}
