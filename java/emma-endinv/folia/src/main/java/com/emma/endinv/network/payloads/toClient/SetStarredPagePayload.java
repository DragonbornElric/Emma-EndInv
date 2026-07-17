package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.util.ItemStackLike;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

public record SetStarredPagePayload(List<ItemStackLike> stacks) implements ModPacketPayload {

    public static void encode(SetStarredPagePayload payload, FriendlyByteBuf o) {
        o.writeCollection(payload.stacks, ItemStackLike::encode);
    }

    public static SetStarredPagePayload decode(FriendlyByteBuf o) {
        return new SetStarredPagePayload(o.readList(ItemStackLike::decode));
    }

    @Override
    public String id() { return "starred_item"; }

    @Override
    public void write(FriendlyByteBuf buffer) { encode(this, buffer); }

    @Override
    public void handle(ModPacketContext context) {}
}
