package com.emma.endinv.manage;

import com.emma.endinv.network.BufCollections;
import com.emma.endinv.util.Accessibility;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.UUID;

/**
 * One row of the inventory manager list, as seen by the requesting player.
 * The {@code can*} flags are computed server-side; the client only uses them to enable buttons.
 */
public record EndInvSummary(UUID id,
                            int index,
                            String owner,
                            Accessibility access,
                            List<String> whitelist,
                            List<String> users,
                            int itemTypes,
                            long totalItems,
                            boolean current,
                            boolean canSelect,
                            boolean canShare,
                            boolean canManage) {

    public static final StreamCodec<RegistryFriendlyByteBuf, EndInvSummary> STREAM_CODEC =
            StreamCodec.of((buf, value) -> encode(value, buf), EndInvSummary::decode);

    public static void encode(EndInvSummary s, FriendlyByteBuf o) {
        UUIDUtil.STREAM_CODEC.encode(o, s.id);
        o.writeVarInt(s.index);
        o.writeUtf(s.owner);
        o.writeEnum(s.access);
        BufCollections.writeCollection(o, s.whitelist, FriendlyByteBuf::writeUtf);
        BufCollections.writeCollection(o, s.users, FriendlyByteBuf::writeUtf);
        o.writeVarInt(s.itemTypes);
        o.writeVarLong(s.totalItems);
        o.writeBoolean(s.current);
        o.writeBoolean(s.canSelect);
        o.writeBoolean(s.canShare);
        o.writeBoolean(s.canManage);
    }

    public static EndInvSummary decode(FriendlyByteBuf o) {
        return new EndInvSummary(
                UUIDUtil.STREAM_CODEC.decode(o),
                o.readVarInt(),
                o.readUtf(),
                o.readEnum(Accessibility.class),
                BufCollections.readList(o, FriendlyByteBuf::readUtf),
                BufCollections.readList(o, FriendlyByteBuf::readUtf),
                o.readVarInt(),
                o.readVarLong(),
                o.readBoolean(),
                o.readBoolean(),
                o.readBoolean(),
                o.readBoolean());
    }
}
