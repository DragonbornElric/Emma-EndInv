package com.emma.endinv.network.payloads.toServer;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.menu.EnchantingStation;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** Put bookshelves from the player's inventory or EndInv into the enchanting station, no screen needed. */
public record AddBookshelvesPayload(int count) implements ModPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, AddBookshelvesPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> encode(value, buf), AddBookshelvesPayload::decode);

    public static final CustomPacketPayload.Type<AddBookshelvesPayload> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("add_bookshelves"));

    public static void encode(AddBookshelvesPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.count);
    }

    public static AddBookshelvesPayload decode(FriendlyByteBuf buf) {
        return new AddBookshelvesPayload(buf.readVarInt());
    }

    @Override
    public String id() {
        return "add_bookshelves";
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void handle(ModPacketContext context) {
        Player player = context.player();
        if (player == null) return;
        ServerLevelEndInv.getEndInvForPlayer(player)
                .ifPresent(endInv -> EnchantingStation.addBookshelvesFromStorage(player, endInv, count));
    }
}
