package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.FriendlyByteBuf;

public record EndInvMetadata(int itemSize, int maxStackSize, boolean infinityMode, EndInvConfig config)
        implements ModPacketPayload {

    public static void encode(EndInvMetadata m, FriendlyByteBuf o) {
        o.writeInt(m.itemSize);
        o.writeInt(m.maxStackSize);
        o.writeBoolean(m.infinityMode);
        EndInvConfig.encode(o, m.config);
    }

    public static EndInvMetadata decode(FriendlyByteBuf o) {
        return new EndInvMetadata(o.readInt(), o.readInt(), o.readBoolean(), EndInvConfig.decode(o));
    }

    public static EndInvMetadata getWith(EndlessInventory endInv) {
        return new EndInvMetadata(
                endInv.getItemSize(),
                endInv.getMaxItemStackSize(),
                endInv.isInfinityMode(),
                EndInvConfig.getWith(endInv)
        );
    }

    @Override
    public String id() { return "endinv_meta"; }

    @Override
    public void write(FriendlyByteBuf buffer) { encode(this, buffer); }

    @Override
    public void handle(ModPacketContext context) {}
}
