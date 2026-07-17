package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.client.CachedSrcInv;
import com.emma.endinv.client.gui.page.ItemDisplay;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemState;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Map;

public record EndInvContent(Map<ItemKey, ItemState> itemMap) implements ModPacketPayload {

    public static void encode(EndInvContent content, FriendlyByteBuf o){
        o.writeMap(
                content.itemMap,
                ItemKey::encode,
                ItemState::encode
        );
    }

    public static EndInvContent decode(FriendlyByteBuf o){
        return new EndInvContent(o.readMap(Object2ObjectLinkedOpenHashMap::new,
                ItemKey::decode,
                ItemState::decode
        ));
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        encode(this, buffer);
    }

    @Override
    public String id() {
        return "endinv_content";
    }

    public void handle(ModPacketContext context){
        CachedSrcInv.INSTANCE.initializeContents(this.itemMap());

        ModPacketPayload.getClientPageMeta().ifPresent(
                mng->{
                    if(mng.getDisplayingPage() instanceof ItemDisplay itemPage){
                        itemPage.readCachedItems();//9.25 update: packet invoke page item refresh, item refresh should not send packet (ItemPageContext)
                    }
                }
        );
    }
}
