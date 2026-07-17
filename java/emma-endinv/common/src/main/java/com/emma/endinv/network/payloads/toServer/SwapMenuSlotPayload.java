package com.emma.endinv.network.payloads.toServer;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.network.payloads.ModPacketContext;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemState;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Sent by the client to swap an EndInv item with an arbitrary slot in the player's
 * currently open menu. Slot indices are menu-relative (player.containerMenu.slots).
 * Respects slot.mayPickup / slot.mayPlace. Atomic: if the swap cannot complete
 * in full (e.g. EndInv can't absorb all of the slot's stack), nothing changes.
 */
public record SwapMenuSlotPayload(ItemKey key, int menuSlotIndex) implements ModPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void encode(SwapMenuSlotPayload payload, FriendlyByteBuf buf) {
        ItemKey.encode(buf, payload.key);
        buf.writeInt(payload.menuSlotIndex);
    }

    public static SwapMenuSlotPayload decode(FriendlyByteBuf buf) {
        return new SwapMenuSlotPayload(ItemKey.decode(buf), buf.readInt());
    }

    @Override
    public void write(FriendlyByteBuf buffer) { encode(this, buffer); }

    @Override
    public String id() { return "swap_menu_slot"; }

    @Override
    public void handle(ModPacketContext ctx) {
        Player player = ctx.player();
        if (!(player instanceof ServerPlayer sp)) return;

        Optional<EndlessInventory> opt = ServerLevelEndInv.getEndInvForPlayer(sp);
        if (opt.isEmpty()) {
            LOGGER.warn("SwapMenuSlotPayload: no EndInv for {}", sp.getName().getString());
            return;
        }
        EndlessInventory endInv = opt.get();

        AbstractContainerMenu menu = sp.containerMenu;
        if (menuSlotIndex < 0 || menuSlotIndex >= menu.slots.size()) {
            LOGGER.warn("SwapMenuSlotPayload: menuSlotIndex {} out of range (size={})", menuSlotIndex, menu.slots.size());
            return;
        }
        Slot slot = menu.getSlot(menuSlotIndex);

        ItemStack slotItem = slot.getItem();
        ItemState state = endInv.getItemMap().get(key);
        int endInvCount = state != null ? state.count() : 0;
        ItemStack endInvSnapshot = endInvCount > 0
                ? key.toStack(Math.min(endInvCount, key.toStack(1).getMaxStackSize()))
                : ItemStack.EMPTY;

        boolean hasSlotItem = !slotItem.isEmpty();
        boolean hasEndInvItem = !endInvSnapshot.isEmpty();

        LOGGER.debug("SwapMenuSlotPayload: slot={} slotItem={} endInvSnapshot={}", menuSlotIndex, slotItem, endInvSnapshot);

        if (hasSlotItem && !hasEndInvItem) {
            // Drain slot -> EndInv
            if (!slot.mayPickup(sp)) return;
            ItemStack remain = endInv.addItem(slotItem.copy());
            slot.setByPlayer(remain);
            slot.onTake(sp, slotItem);

        } else if (!hasSlotItem && hasEndInvItem) {
            // Place EndInv item into empty slot
            if (!slot.mayPlace(endInvSnapshot)) return;
            ItemStack taken = endInv.takeItem(endInvSnapshot);
            slot.setByPlayer(taken);

        } else if (hasSlotItem && hasEndInvItem) {
            // Atomic swap: all-or-nothing to avoid half-states
            if (!slot.mayPickup(sp) || !slot.mayPlace(endInvSnapshot)) return;
            int beforeCount = slotItem.getCount();
            ItemStack remain = endInv.addItem(slotItem.copy());
            if (!remain.isEmpty()) {
                // EndInv couldn't absorb all — rollback the partial absorption and bail
                int absorbed = beforeCount - remain.getCount();
                if (absorbed > 0) endInv.takeItem(slotItem.copyWithCount(absorbed));
                LOGGER.debug("SwapMenuSlotPayload: swap aborted, EndInv could not absorb full slot stack");
                return;
            }
            ItemStack taken = endInv.takeItem(endInvSnapshot);
            slot.setByPlayer(taken);
            slot.onTake(sp, slotItem);
        }
        // both empty: no-op

        endInv.setChanged();
    }
}
