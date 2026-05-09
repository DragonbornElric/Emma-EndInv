package com.emma.endinv.client.action;

import com.emma.endinv.ModInfo;
import com.emma.endinv.network.payloads.toServer.SwapMenuSlotPayload;
import com.emma.endinv.util.ItemKey;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class SwapMenuSlotAction {

    private SwapMenuSlotAction() {}

    /**
     * Sends a swap request for the EndInv item {@code key} with the slot at
     * {@code menuSlotIndex} in the player's currently open menu.
     * Must be called on the client thread. Returns true if the packet was sent.
     */
    public static boolean swapWithMenuSlot(ItemKey key, int menuSlotIndex) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return false;
        AbstractContainerMenu menu = player.containerMenu;
        if (menuSlotIndex < 0 || menuSlotIndex >= menu.slots.size()) return false;
        ModInfo.getPacketDistributor().sendToServer(new SwapMenuSlotPayload(key, menuSlotIndex));
        return true;
    }
}
