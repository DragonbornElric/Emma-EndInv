package com.emma.endinv.folia;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.util.recipeTransferHelper.RecipeItemProvider;
import io.papermc.paper.inventory.recipe.ItemOrExact;
import net.minecraft.core.registries.Registries;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Folia-native replacement for ServerPlaceRecipeMixin.
 *
 * Mirrors Fabric's two-mixin approach:
 *  1. ServerPlaceRecipeMixin adds EndInv to the combined StackedItemContents so canCraft
 *     sees both player inventory and EndInv (split-ingredient recipes work).
 *  2. moveItemToGrid fallback pulls from EndInv when player inventory alone falls short.
 *
 * Here we own the entire placement: drain EndInv first, fall back to player inventory.
 */
public final class FoliaRecipeBookPlacementListener implements Listener {

    private final MinecraftServer mcServer;

    public FoliaRecipeBookPlacementListener(JavaPlugin plugin) {
        this.mcServer = ((org.bukkit.craftbukkit.CraftServer) plugin.getServer()).getServer();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRecipeBookClick(com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent event) {
        ServerPlayer sp = ((CraftPlayer) event.getPlayer()).getHandle();

        EndlessInventory endInv = ServerLevelEndInv.getEndInvForPlayer(sp).orElse(null);
        if (endInv == null) return;

        if (!(sp.containerMenu instanceof CraftingMenu) && !(sp.containerMenu instanceof InventoryMenu)) return;

        // Resolve NMS recipe
        Identifier rl = CraftNamespacedKey.toMinecraft(event.getRecipe());
        ResourceKey<Recipe<?>> rk = ResourceKey.create(Registries.RECIPE, rl);
        RecipeHolder<?> holder = mcServer.getRecipeManager().byKey(rk).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe craftRecipe)) return;

        List<Slot> inputSlots = getInputGridSlots(sp.containerMenu);

        // Build combined StackedItemContents from EndInv + player inventory — mirrors
        // ServerPlaceRecipeMixin's HEAD inject which adds EndInv on top of vanilla's player-inv fill.
        // This makes canCraft work for split-ingredient cases (e.g. planks in player inv, sticks in EndInv).
        StackedItemContents combined = new StackedItemContents();
        combined.initializeExtras(craftRecipe, null);
        RecipeItemProvider.fillStackedItemContents(endInv.getItemsAsList(), combined);
        RecipeItemProvider.fillStackedItemContents(playerInvItems(sp), combined);
        if (!combined.canCraft(craftRecipe, null)) return;

        // We own this placement now.
        event.setCancelled(true);

        // Calculate amount per ingredient slot (mirrors vanilla calculateAmountToCraft).
        // Add current grid contents so getBiggestCraftableStack includes them.
        StackedItemContents contentsWithGrid = new StackedItemContents();
        contentsWithGrid.initializeExtras(craftRecipe, null);
        RecipeItemProvider.fillStackedItemContents(endInv.getItemsAsList(), contentsWithGrid);
        RecipeItemProvider.fillStackedItemContents(playerInvItems(sp), contentsWithGrid);
        for (Slot s : inputSlots) contentsWithGrid.accountStack(s.getItem(), 1);

        int max = contentsWithGrid.getBiggestCraftableStack(craftRecipe, null);
        int amount = event.isMakeAll() ? max : 1;

        List<ItemOrExact> items = new ArrayList<>();
        if (!contentsWithGrid.canCraft(craftRecipe, amount, items::add)) return;

        int clamped = clampToMaxStackSize(amount, items);
        if (clamped != amount) {
            items.clear();
            if (!contentsWithGrid.canCraft(craftRecipe, clamped, items::add)) return;
        }

        // Return current grid contents to EndInv before placing new ingredients.
        for (Slot s : inputSlots) {
            ItemStack cur = s.getItem();
            if (cur.isEmpty()) continue;
            s.set(ItemStack.EMPTY);
            ItemStack remain = endInv.addItem(cur.copy());
            if (!remain.isEmpty()) {
                sp.drop(remain, false);
            }
        }

        // Place ingredients: drain EndInv first, fall back to player inventory for shortfalls.
        // Mirrors ServerPlaceRecipeMixin's moveItemToGrid fallback (vanilla tries player inv first
        // there; here we invert priority so EndInv is preferred as the storage source).
        PlaceRecipeHelper.placeRecipe(
                gridWidth(sp.containerMenu), gridHeight(sp.containerMenu),
                craftRecipe, craftRecipe.placementInfo().slotsToIngredientIndex(),
                (itemIdx, slotIdx, x, y) -> {
                    if (itemIdx == -1) return;
                    ItemOrExact ioe = items.get(itemIdx);
                    ItemStack probe = probeFor(ioe);
                    Slot slot = inputSlots.get(slotIdx);

                    int remaining = clamped;
                    // Step 1: drain EndInv (skip if EI doesn't hold this key).
                    if (endInv.hasItem(probe)) while (remaining > 0) {
                        ItemStack taken = endInv.takeItem(probe.copy(), remaining);
                        if (taken.isEmpty()) break;
                        addToSlot(slot, taken);
                        remaining -= taken.getCount();
                    }
                    // Step 2: fall back to player inventory (mirrors mixin's moveItemToGrid fallback).
                    if (remaining > 0) {
                        takeFromPlayerInv(sp, ioe, probe, remaining, slot);
                    }
                });

        // Trigger result-slot computation and send slot updates to client.
        net.minecraft.world.Container craftContainer = inputSlots.get(0).container;
        sp.containerMenu.slotsChanged(craftContainer);
        sp.containerMenu.broadcastChanges();
    }

    private static List<Slot> getInputGridSlots(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (menu instanceof CraftingMenu cm) return cm.getInputGridSlots();
        if (menu instanceof InventoryMenu im) return im.getInputGridSlots();
        return List.of();
    }

    private static int gridWidth(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        return (menu instanceof CraftingMenu) ? 3 : 2;
    }

    private static int gridHeight(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        return (menu instanceof CraftingMenu) ? 3 : 2;
    }

    private static ItemStack probeFor(ItemOrExact ioe) {
        return switch (ioe) {
            case ItemOrExact.Item it -> new ItemStack(it.item());
            case ItemOrExact.Exact ex -> ex.stack().copy();
        };
    }

    private static int clampToMaxStackSize(int n, List<ItemOrExact> items) {
        for (ItemOrExact ioe : items) n = Math.min(n, ioe.getMaxStackSize());
        return n;
    }

    private static List<ItemStack> playerInvItems(ServerPlayer sp) {
        Inventory inv = sp.getInventory();
        List<ItemStack> out = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static void addToSlot(Slot slot, ItemStack taken) {
        ItemStack cur = slot.getItem();
        if (cur.isEmpty()) slot.set(taken);
        else cur.grow(taken.getCount());
    }

    private static void takeFromPlayerInv(ServerPlayer sp, ItemOrExact ioe, ItemStack probe, int wanted, Slot slot) {
        Inventory inv = sp.getInventory();
        for (int i = 0; i < 36 && wanted > 0; i++) {
            ItemStack invStack = inv.getItem(i);
            if (invStack.isEmpty()) continue;
            boolean matches = switch (ioe) {
                case ItemOrExact.Item it -> invStack.is(it.item());
                case ItemOrExact.Exact ex -> ItemStack.isSameItemSameComponents(invStack, ex.stack());
            };
            if (!matches) continue;
            int take = Math.min(wanted, invStack.getCount());
            invStack.shrink(take);
            addToSlot(slot, probe.copyWithCount(take));
            wanted -= take;
        }
    }
}
