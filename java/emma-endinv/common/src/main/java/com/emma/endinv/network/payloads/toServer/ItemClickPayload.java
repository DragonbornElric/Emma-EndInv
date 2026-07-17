package com.emma.endinv.network.payloads.toServer;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.menu.page.pageManager.PageQuickMoveHandler;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemState;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**Sent when
 * slot/item clicked in {@link ItemPage}.
 * @param key
 * @param button
 * @param clickType
 */
public record ItemClickPayload(ItemKey key, int button, ClickType clickType) implements ModPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static ItemClickPayload decode(FriendlyByteBuf buf) {
        return new ItemClickPayload(
                ItemKey.decode(buf),
                buf.readInt(),
                buf.readEnum(ClickType.class)
        );
    }


    public static void encode(ItemClickPayload itemClickPayload, FriendlyByteBuf o) {
        ItemKey.encode(o, itemClickPayload.key);
        o.writeInt(itemClickPayload.button);
        o.writeEnum(itemClickPayload.clickType);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        encode(this, buffer);
    }

    @Override
    public void handle(ModPacketContext context) {
        Player player = context.player();
        assert player != null;//on server
        AbstractContainerMenu menu = player.containerMenu;
        ItemStack carried = menu.getCarried();
        LOGGER.debug("EI:ItemClickPayload.handle: player={} clickType={} button={} carriedEmpty={} key={}", player.getName().getString(), clickType, button, carried.isEmpty(), key);
        var opt = ServerLevelEndInv.getEndInvForPlayer(player);
        if(opt.isEmpty()) {
            LOGGER.warn("ItemClickPayload.handle: no EndInv for player={}", player.getName().getString());
            return;
        }
        EndlessInventory endInv = opt.get();
        ItemState state = endInv.getItemMap().get(key);
        int count = state != null ? state.count() : 0;
        ItemStack snapStack = key.toStack(count);//should be deleted at the method return.

        switch (clickType){
            case PICKUP -> {
                if(!carried.isEmpty()){
                    ItemStack remain = endInv.addItem(carried);
                    menu.setCarried(remain);
                    endInv.setChanged();
                } else {
                    count = Math.min(count,snapStack.getMaxStackSize());
                    int takenCount = button==0 ? count : (count + 1) / 2;
                    ItemStack taken = endInv.takeItem(snapStack,takenCount);
                    LOGGER.debug("ItemClickPayload.PICKUP: taken={} from key={}", taken, key);
                    if(player.isCreative() && (menu instanceof InventoryMenu)) {
                        LOGGER.info("Ignored taken item on server thread in Creative mode to prevent duplication.");
                    }else menu.setCarried(taken);
                }
            }
            case SWAP -> {
                Inventory inventory = player.getInventory();
                ItemStack inventoryItem = inventory.getItem(button);
                boolean a = !inventoryItem.isEmpty();
                boolean b = snapStack != null && !snapStack.isEmpty();
                LOGGER.debug("ItemClickPayload.SWAP: inventoryItem={} clickedKey={}", inventoryItem, key);
                if( a && !b ){
                    ItemStack remain = endInv.addItem(inventoryItem);
                    inventory.setItem(button, remain);
                    LOGGER.debug("ItemClickPayload.SWAP: added inventoryItem, remain={}", remain);
                }
                if( !a && b ){
                    ItemStack swapping = endInv.takeItem(snapStack);
                    inventory.setItem(button,swapping);
                    LOGGER.debug("ItemClickPayload.SWAP: took from endInv swapping={}", swapping);
                }
                if( a && b ){
                    ItemStack remain =  endInv.addItem(inventoryItem);
                    LOGGER.debug("ItemClickPayload.SWAP: both non-empty, remainFromAdd={}", remain);
                    if(remain.isEmpty()) {
                        ItemStack swapping =  endInv.takeItem(snapStack);
                        inventory.setItem(button, swapping);
                        LOGGER.debug("ItemClickPayload.SWAP: swap success swapping={}", swapping);
                    }else {
                        inventory.setItem(button,remain);
                    }
                }
                endInv.setChanged();
            }
            case THROW -> {
                ItemStack thrown = endInv.takeItem(snapStack, count);
                LOGGER.debug("ItemClickPayload.THROW: thrown={}", thrown);
                player.drop(thrown,true);
                endInv.setChanged();
            }
            case PICKUP_ALL -> {
                int startIndex = menu.slots.size() - 1; //changed: reversed button==0 condition
                for(int index = startIndex; index>=0 ; --index){
                    Slot scanning = menu.slots.get(index);
                    if(!(scanning.container instanceof Inventory)) break;
                    ItemStack scanningItem =scanning.getItem();
                    if (ItemStack.isSameItemSameTags(carried, scanningItem)) {
                        ItemStack taken = scanning.safeTake(scanningItem.getCount(), scanningItem.getCount(), player);
                        LOGGER.debug("ItemClickPayload.PICKUP_ALL: took {} from slot index={}", taken, index);
                        ItemStack remain = endInv.addItem(taken);
                        if(!remain.isEmpty()) scanning.set(remain);
                        endInv.setChanged();
                    }
                }
            }
            case CLONE -> {
                if(player.isCreative() && carried.isEmpty()){
                    menu.setCarried(snapStack.copyWithCount(snapStack.getMaxStackSize()));
                    LOGGER.debug("ItemClickPayload.CLONE: cloned stack={}", snapStack);
                }
            }
            case QUICK_MOVE -> {
                ItemStack taken = endInv.takeItem(snapStack, count);
                LOGGER.debug("ItemClickPayload.QUICK_MOVE: taken={}", taken);
                ItemStack remain = new PageQuickMoveHandler(menu).quickMoveFromPage(taken);
                LOGGER.debug("ItemClickPayload.QUICK_MOVE: remain after quickMove={}", remain);
                endInv.addItem(remain);
                endInv.setChanged();
            }
        }


    }

    @Override
    public String id() {
        return "item_click";
    }

}
