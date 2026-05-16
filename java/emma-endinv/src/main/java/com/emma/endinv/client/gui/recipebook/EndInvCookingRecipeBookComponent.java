package com.emma.endinv.client.gui.recipebook;

import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.menu.Station;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

import java.util.List;

public class EndInvCookingRecipeBookComponent extends RecipeBookComponent<EndlessInventoryMenu>
        implements EndInvRecipeBookComponent {

    private static final WidgetSprites FILTER_SPRITES = new WidgetSprites(
        Identifier.withDefaultNamespace("recipe_book/furnace_filter_enabled"),
        Identifier.withDefaultNamespace("recipe_book/furnace_filter_disabled"),
        Identifier.withDefaultNamespace("recipe_book/furnace_filter_enabled_highlighted"),
        Identifier.withDefaultNamespace("recipe_book/furnace_filter_disabled_highlighted")
    );

    private static final List<TabInfo> FURNACE_TABS = List.of(
        new TabInfo(SearchRecipeBookCategory.FURNACE),
        new TabInfo(Items.PORKCHOP, RecipeBookCategories.FURNACE_FOOD),
        new TabInfo(Items.STONE, RecipeBookCategories.FURNACE_BLOCKS),
        new TabInfo(Items.LAVA_BUCKET, Items.EMERALD, RecipeBookCategories.FURNACE_MISC)
    );

    private static final List<TabInfo> SMOKER_TABS = List.of(
        new TabInfo(SearchRecipeBookCategory.SMOKER),
        new TabInfo(Items.PORKCHOP, RecipeBookCategories.SMOKER_FOOD)
    );

    private static final List<TabInfo> BLAST_FURNACE_TABS = List.of(
        new TabInfo(SearchRecipeBookCategory.BLAST_FURNACE),
        new TabInfo(Items.REDSTONE_ORE, RecipeBookCategories.BLAST_FURNACE_BLOCKS),
        new TabInfo(Items.IRON_SHOVEL, Items.GOLDEN_LEGGINGS, RecipeBookCategories.BLAST_FURNACE_MISC)
    );

    private final Station station;
    private final Component filterName;

    private EndInvCookingRecipeBookComponent(EndlessInventoryMenu menu, Station station,
            Component filterName, List<TabInfo> tabs) {
        super(menu, tabs);
        this.station = station;
        this.filterName = filterName;
    }

    public static EndInvCookingRecipeBookComponent forStation(EndlessInventoryMenu menu, Station station) {
        return switch (station) {
            case FURNACE -> new EndInvCookingRecipeBookComponent(menu, station,
                    Component.translatable("gui.recipebook.toggleRecipes.smeltable"), FURNACE_TABS);
            case SMOKER -> new EndInvCookingRecipeBookComponent(menu, station,
                    Component.translatable("gui.recipebook.toggleRecipes.smokable"), SMOKER_TABS);
            case BLAST_FURNACE -> new EndInvCookingRecipeBookComponent(menu, station,
                    Component.translatable("gui.recipebook.toggleRecipes.blastable"), BLAST_FURNACE_TABS);
            default -> throw new IllegalArgumentException("Not a cooking station: " + station);
        };
    }

    @Override
    protected WidgetSprites getFilterButtonTextures() {
        return FILTER_SPRITES;
    }

    @Override
    protected Component getRecipeFilterName() {
        return filterName;
    }

    @Override
    protected boolean isCraftingSlot(Slot slot) {
        return slot == menu.getCookingInputSlot(station)
            || slot == menu.getCookingFuelSlot(station)
            || slot == menu.getCookingResultSlot(station);
    }

    @Override
    protected void selectMatchingRecipes(RecipeCollection collection, StackedItemContents stackedContents) {
        collection.selectRecipes(stackedContents, display -> display instanceof FurnaceRecipeDisplay);
    }

    @Override
    protected void fillGhostRecipe(GhostSlots ghostSlots, RecipeDisplay recipe, ContextMap context) {
        ghostSlots.setResult(menu.getCookingResultSlot(station), context, recipe.result());
        if (recipe instanceof FurnaceRecipeDisplay furnaceRecipe) {
            ghostSlots.setInput(menu.getCookingInputSlot(station), context, furnaceRecipe.ingredient());
            Slot fuelSlot = menu.getCookingFuelSlot(station);
            if (fuelSlot.getItem().isEmpty()) {
                ghostSlots.setInput(fuelSlot, context, furnaceRecipe.fuel());
            }
        }
    }
}
