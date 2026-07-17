package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.client.CachedSrcInv;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import net.minecraft.network.FriendlyByteBuf;

/**
 * holds various attributes that influent ItemDisplay,
 *  compared to {@link EndInvConfig}
 */
public record EndInvMetadata(int itemSize, int maxStackSize, boolean infinityMode, EndInvConfig config) implements ModPacketPayload {

    public static void encode(EndInvMetadata endInvMetadata,FriendlyByteBuf o){
        o.writeInt(endInvMetadata.itemSize);
        o.writeInt(endInvMetadata.maxStackSize);
        o.writeBoolean(endInvMetadata.infinityMode);
        EndInvConfig.encode(o,endInvMetadata.config);
    }

    public static EndInvMetadata decode(FriendlyByteBuf o){
        return new EndInvMetadata(
                o.readInt(),
                o.readInt(),
                o.readBoolean(),
                EndInvConfig.decode(o)
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        encode(this, buffer);
    }

    public static EndInvMetadata getWith(EndlessInventory endInv) {
        return new EndInvMetadata(
                endInv.getItemSize(),
                endInv.getMaxItemStackSize(),
                endInv.isInfinityMode(),
                EndInvConfig.getWith(endInv)
        );
    }

    public void handle(ModPacketContext context) {
        CachedSrcInv.INSTANCE.syncMetadata(this);

//        if (ScreenFramework.getInstance() != null && ScreenFramework.getInstance().meta.getDisplayingPage() instanceof ItemDisplay itemDisplay) {
//            itemDisplay.readCachedItems();
//        }
    }

    @Override
    public String id() {
        return "endinv_meta";
    }

}
