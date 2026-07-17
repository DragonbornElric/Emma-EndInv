package com.emma.endinv.network.payloads.toServer;

import com.emma.endinv.ModInfo;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.network.payloads.toClient.SetStarredPagePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;


public record StarItemPayload(ItemStack stack,boolean isAdding) implements ModPacketPayload {

    public static void encode(StarItemPayload payload, FriendlyByteBuf o){
        o.writeItem(payload.stack);
        o.writeBoolean(payload.isAdding);
    }

    public static StarItemPayload decode(FriendlyByteBuf o){
        return new StarItemPayload(o.readItem(),o.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        encode(this, buffer);
    }

    @Override
    public String id() {
        return "star_item";
    }

    public void handle(ModPacketContext iPayloadContext) {
        ServerPlayer player = (ServerPlayer) iPayloadContext.player();
        if(player==null) return;
        ServerLevelEndInv.getEndInvForPlayer(player).ifPresent(endInv->{
            if(isAdding()) {
                endInv.affinities.addStarredItem(stack);
            }else {
                endInv.affinities.removeStarredItem(stack);
            }
            ModInfo.getPacketDistributor().sendToPlayer(player, new SetStarredPagePayload(endInv.getStarredItems()));
            endInv.setChanged();
        });
    }
}
