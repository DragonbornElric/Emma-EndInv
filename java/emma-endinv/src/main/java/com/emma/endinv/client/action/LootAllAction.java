package com.emma.endinv.client.action;

import com.emma.endinv.ModInfo;
import com.emma.endinv.network.payloads.toServer.QuickMoveToPagePayload;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public final class LootAllAction {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LootAllAction() {}

    /** Collects indices of lootable foreign-container slots into {@code out}.
     *  Returns the number of slots appended. */
    public static int collectForeignSlots(@NotNull Player player, AbstractContainerMenu menu, IntList out) {
        int count = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot.container instanceof Inventory)
                    && slot.hasItem()
                    && slot.mayPickup(player)) {
                out.add(i);
                count++;
            }
        }
        return count;
    }

    /** Sends a single {@link QuickMoveToPagePayload} covering every lootable foreign-container
     *  slot of the currently open menu. Returns slots queued, or 0 if nothing to do.
     *  Must be called on the client thread. */
    public static int lootAllOpenContainer() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return 0;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu instanceof InventoryMenu) return 0;
        if (menu instanceof CreativeModeInventoryScreen.ItemPickerMenu) return 0;

        IntList slots = new IntArrayList();
        int count = collectForeignSlots(player, menu, slots);
        if (count == 0) return 0;

        ModInfo.getPacketDistributor().sendToServer(new QuickMoveToPagePayload(slots));
        LOGGER.info("[endinv] loot-all: queued {} slots to EndInv", count);
        return count;
    }
}
