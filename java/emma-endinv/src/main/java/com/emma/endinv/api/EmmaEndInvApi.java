package com.emma.endinv.api;

import com.emma.endinv.client.action.LootAllAction;
import com.emma.endinv.client.action.SwapMenuSlotAction;
import com.emma.endinv.util.ItemKey;

public final class EmmaEndInvApi {
    private EmmaEndInvApi() {}

    /** Drains every lootable slot of the currently open foreign container into the player's
     *  Endless Inventory. No-op if no container is open or the player's own inventory is open.
     *  Must be called on the client thread. Returns slots queued, or 0. */
    public static int lootAllOpenContainerToEndInv() {
        return LootAllAction.lootAllOpenContainer();
    }

    /** Programmatically swap an EndInv item identified by {@code key} with the slot at
     *  {@code menuSlotIndex} in the player's currently open menu (player.containerMenu).
     *  Slot indices are menu-relative — use menu.slots to enumerate them.
     *  <ul>
     *    <li>Slot has item, EndInv does not → item drains from slot into EndInv.</li>
     *    <li>Slot is empty, EndInv has item → item is placed into the slot.</li>
     *    <li>Both have items → atomic swap (aborts if EndInv cannot absorb the slot's full stack).</li>
     *  </ul>
     *  Respects slot.mayPickup / slot.mayPlace; no-ops on out-of-range indices.
     *  Must be called on the client thread. Returns true if the request packet was sent. */
    public static boolean swapEndInvWithMenuSlot(ItemKey key, int menuSlotIndex) {
        return SwapMenuSlotAction.swapWithMenuSlot(key, menuSlotIndex);
    }
}
