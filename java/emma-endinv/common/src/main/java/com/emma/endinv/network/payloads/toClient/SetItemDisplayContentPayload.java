package com.emma.endinv.network.payloads.toClient;

import com.emma.endinv.client.gui.page.ItemDisplay;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.util.ItemKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**Packet that contains required {@link ItemDisplay}'s content view
 *  when {@code TransferMode==PART}
 * @param stacks
 */
public record SetItemDisplayContentPayload(List<ItemStack> stacks) implements ModPacketPayload {

    public static void encode(SetItemDisplayContentPayload payload, FriendlyByteBuf o){
        o.writeCollection(payload.stacks, FriendlyByteBuf::writeItem);
    }

    public static SetItemDisplayContentPayload decode(FriendlyByteBuf o){
        return new SetItemDisplayContentPayload(o.readList(FriendlyByteBuf::readItem));
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        encode(this, buffer);
    }

    @Override
    public String id() {
        return "itemdisplay_content";
    }

    public void handle(ModPacketContext context) {
        List<ItemKey> keys = stacks.stream().map(ItemKey::asKey).toList();
        ModPacketPayload.getClientPageMeta().ifPresent(mng -> {
            if (mng.getDisplayingPage() instanceof ItemDisplay itemDisplay) {
                itemDisplay.buildContentsWith(keys);
            }
        });
    }
}
