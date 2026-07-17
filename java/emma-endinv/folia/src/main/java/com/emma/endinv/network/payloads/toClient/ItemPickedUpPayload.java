package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record ItemPickedUpPayload(ItemStack stack) implements ModPacketPayload {

    public static void encode(ItemPickedUpPayload payload, FriendlyByteBuf o) {
        o.writeItem(payload.stack);
    }

    public static ItemPickedUpPayload decode(FriendlyByteBuf o) {
        return new ItemPickedUpPayload(o.readItem());
    }

    @Override
    public String id() { return "auto_picked"; }

    @Override
    public void write(FriendlyByteBuf buffer) { encode(this, buffer); }

    @Override
    public void handle(ModPacketContext context) {}
}
