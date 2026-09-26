package com.emma.endinv.network.payloads.toServer;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.ModInfo;
import com.emma.endinv.manage.EndInvManager;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Client request from the inventory manager screen.
 * {@code target}/{@code other} are EndInv UUIDs ({@link ModInfo#DEFAULT_UUID} when unused);
 * {@code arg} carries an accessibility name or a player name depending on the action.
 * Every request is re-checked for permission on the server by {@link EndInvManager}.
 */
public record ManageEndInvPayload(EndInvManager.Action action, UUID target, UUID other, String arg) implements ModPacketPayload {

    public static ManageEndInvPayload of(EndInvManager.Action action) {
        return new ManageEndInvPayload(action, ModInfo.DEFAULT_UUID, ModInfo.DEFAULT_UUID, "");
    }

    public static ManageEndInvPayload of(EndInvManager.Action action, UUID target) {
        return new ManageEndInvPayload(action, target, ModInfo.DEFAULT_UUID, "");
    }

    public static void encode(ManageEndInvPayload payload, FriendlyByteBuf o) {
        o.writeEnum(payload.action);
        UUIDUtil.STREAM_CODEC.encode(o, payload.target);
        UUIDUtil.STREAM_CODEC.encode(o, payload.other);
        o.writeUtf(payload.arg, 64);
    }

    public static ManageEndInvPayload decode(FriendlyByteBuf o) {
        return new ManageEndInvPayload(
                o.readEnum(EndInvManager.Action.class),
                UUIDUtil.STREAM_CODEC.decode(o),
                UUIDUtil.STREAM_CODEC.decode(o),
                o.readUtf(64));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ManageEndInvPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> encode(value, buf), ManageEndInvPayload::decode);

    public static final CustomPacketPayload.Type<ManageEndInvPayload> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("manage_endinv"));

    @Override
    public String id() {
        return "manage_endinv";
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void handle(ModPacketContext context) {
        if (context.player() instanceof ServerPlayer player) {
            EndInvManager.handle(player, this);
        }
    }
}
