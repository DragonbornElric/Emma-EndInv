package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record SetItemDisplayContentPayload(List<ItemStack> stacks) implements ModPacketPayload {

    public static void encode(SetItemDisplayContentPayload payload, FriendlyByteBuf o) {
        o.writeVarInt(payload.stacks.size());
        for (ItemStack stack : payload.stacks) {
            o.writeItem(stack);
        }
    }

    public static SetItemDisplayContentPayload decode(FriendlyByteBuf o) {
        int n = o.readVarInt();
        List<ItemStack> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(o.readItem());
        }
        return new SetItemDisplayContentPayload(list);
    }

    @Override
    public String id() { return "itemdisplay_content"; }

    @Override
    public void write(FriendlyByteBuf buffer) { encode(this, buffer); }

    @Override
    public void handle(ModPacketContext context) {}
}
