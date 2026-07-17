package com.emma.endinv.folia;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.util.recipeTransferHelper.RecipeItemProvider;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.recipebook.PlaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;

/**
 * Folia-native equivalent of the ServerPlaceRecipe mixins used by the mod
 * loaders. Minecraft 1.20.1's recipe-book implementation only counts the
 * player's normal inventory, so this listener owns placement whenever the
 * player has an Endless Inventory and accounts both stores together.
 */
public final class FoliaRecipeBookPlacementListener implements Listener {

    private final MinecraftServer server;

    public FoliaRecipeBookPlacementListener(JavaPlugin plugin) {
        this.server = ((CraftServer) plugin.getServer()).getServer();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRecipeBookClick(com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent event) {
        ServerPlayer player = ((CraftPlayer) event.getPlayer()).getHandle();
        if (!(player.containerMenu instanceof RecipeBookMenu<?> menu)) {
            return;
        }

        EndlessInventory endInv = ServerLevelEndInv.getEndInvForPlayer(player).orElse(null);
        if (endInv == null) {
            return;
        }

        ResourceLocation id = CraftNamespacedKey.toMinecraft(event.getRecipe());
        Recipe<?> recipe = server.getRecipeManager().byKey(id).orElse(null);
        if (recipe == null) {
            return;
        }

        StackedContents available = new StackedContents();
        player.getInventory().fillStackedContents(available);
        menu.fillCraftSlotsStackedContents(available);
        RecipeItemProvider.fillStackedContents(endInv.getItemsAsList(), available);
        if (!available.canCraft(recipe, null)) {
            return;
        }

        int biggest = available.getBiggestCraftableStack(recipe, null);
        int amount = calculateAmount(menu, recipe, event.isMakeAll(), biggest);
        if (amount <= 0) {
            return;
        }

        IntList ingredients = new IntArrayList();
        if (!available.canCraft(recipe, ingredients, amount)) {
            return;
        }

        for (int stackingId : ingredients) {
            ItemStack ingredient = StackedContents.fromStackingIndex(stackingId);
            amount = Math.min(amount, ingredient.getMaxStackSize());
        }
        ingredients.clear();
        if (amount <= 0 || !available.canCraft(recipe, ingredients, amount)) {
            return;
        }

        // Cancel the vanilla player-inventory-only placement and perform the
        // same grid layout with a combined EndInv/player source.
        event.setCancelled(true);
        clearGridToEndInv(player, menu, endInv);

        int finalAmount = amount;
        PlaceRecipe<Integer> placer = new PlaceRecipe<>() {
            @Override
            public void addItemToSlot(Iterator<Integer> ids, int slotIndex, int count, int x, int y) {
                if (!ids.hasNext()) {
                    return;
                }
                ItemStack prototype = StackedContents.fromStackingIndex(ids.next());
                if (prototype.isEmpty()) {
                    return;
                }

                Slot slot = menu.getSlot(slotIndex);
                int remaining = moveFromEndInv(endInv, prototype, count, slot);
                if (remaining > 0) {
                    moveFromPlayerInventory(player.getInventory(), prototype, remaining, slot);
                }
            }
        };
        placer.placeRecipe(
                menu.getGridWidth(),
                menu.getGridHeight(),
                menu.getResultSlotIndex(),
                recipe,
                ingredients.iterator(),
                finalAmount
        );

        Container craftContainer = firstCraftContainer(menu);
        if (craftContainer != null) {
            menu.slotsChanged(craftContainer);
        }
        player.getInventory().setChanged();
        menu.broadcastChanges();
        endInv.broadcastChanges(server);
    }

    private static int calculateAmount(
            RecipeBookMenu<?> menu,
            Recipe<?> recipe,
            boolean makeAll,
            int biggest
    ) {
        if (makeAll) {
            return biggest;
        }
        if (!recipeMatches(menu, recipe)) {
            return 1;
        }

        int amount = 64;
        for (int slotIndex = 0; slotIndex < menu.getSize(); slotIndex++) {
            if (!menu.shouldMoveToInventory(slotIndex)) {
                continue;
            }
            ItemStack stack = menu.getSlot(slotIndex).getItem();
            if (!stack.isEmpty()) {
                amount = Math.min(amount, stack.getCount());
            }
        }
        return Math.min(biggest, amount + 1);
    }

    private static void clearGridToEndInv(
            ServerPlayer player,
            RecipeBookMenu<?> menu,
            EndlessInventory endInv
    ) {
        for (int slotIndex = 0; slotIndex < menu.getSize(); slotIndex++) {
            if (!menu.shouldMoveToInventory(slotIndex)) {
                continue;
            }
            Slot slot = menu.getSlot(slotIndex);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            slot.set(ItemStack.EMPTY);
            ItemStack remainder = endInv.addItem(stack.copy());
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
        }
        menu.clearCraftingContent();
    }

    private static int moveFromEndInv(
            EndlessInventory endInv,
            ItemStack prototype,
            int wanted,
            Slot destination
    ) {
        int remaining = wanted;
        while (remaining > 0 && endInv.hasItem(prototype)) {
            ItemStack taken = endInv.takeItem(prototype.copy(), remaining);
            if (taken.isEmpty()) {
                break;
            }
            addToSlot(destination, taken);
            remaining -= taken.getCount();
        }
        return remaining;
    }

    private static void moveFromPlayerInventory(
            Inventory inventory,
            ItemStack prototype,
            int wanted,
            Slot destination
    ) {
        int remaining = wanted;
        while (remaining > 0) {
            int index = inventory.findSlotMatchingUnusedItem(prototype);
            if (index == Inventory.NOT_FOUND_INDEX) {
                return;
            }
            ItemStack source = inventory.getItem(index);
            int count = Math.min(remaining, source.getCount());
            ItemStack moved = source.copyWithCount(count);
            source.shrink(count);
            if (source.isEmpty()) {
                inventory.setItem(index, ItemStack.EMPTY);
            }
            addToSlot(destination, moved);
            remaining -= count;
        }
    }

    private static void addToSlot(Slot slot, ItemStack stack) {
        ItemStack existing = slot.getItem();
        if (existing.isEmpty()) {
            slot.set(stack.copy());
        } else {
            existing.grow(stack.getCount());
            slot.setChanged();
        }
    }

    private static Container firstCraftContainer(RecipeBookMenu<?> menu) {
        for (int slotIndex = 0; slotIndex < menu.getSize(); slotIndex++) {
            if (menu.shouldMoveToInventory(slotIndex)) {
                return menu.getSlot(slotIndex).container;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean recipeMatches(RecipeBookMenu<?> menu, Recipe<?> recipe) {
        return ((RecipeBookMenu) menu).recipeMatches((Recipe) recipe);
    }
}
