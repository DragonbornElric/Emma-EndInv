package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record SetItemDisplayContentPayload(List<ItemStack> stacks) implements ModPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, SetItemDisplayContentPayload> STREAM_CODEC =
            StreamCodec.of(SetItemDisplayContentPayload::encodeRegistry, SetItemDisplayContentPayload::decodeRegistry);

    public static final CustomPacketPayload.Type<SetItemDisplayContentPayload> TYPE =
            new CustomPacketPayload.Type<>(AbstractModInitializer.withModLocation("itemdisplay_content"));

    private static void encodeRegistry(RegistryFriendlyByteBuf o, SetItemDisplayContentPayload payload) {
        o.writeVarInt(payload.stacks.size());
        for (ItemStack stack : payload.stacks) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(o, stack);
        }
    }

    private static SetItemDisplayContentPayload decodeRegistry(RegistryFriendlyByteBuf o) {
        int n = o.readVarInt();
        List<ItemStack> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(o));
        }
        return new SetItemDisplayContentPayload(list);
    }

    @Override
    public String id() { return "itemdisplay_content"; }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public void handle(ModPacketContext context) {}
}
