package com.emma.endinv.menu;

import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

public enum Station {
    NONE(null, null, -1),
    CRAFTING(null, RecipeBookType.CRAFTING, -1),
    FURNACE(RecipeType.SMELTING, RecipeBookType.FURNACE, 0),
    SMOKER(RecipeType.SMOKING, RecipeBookType.SMOKER, 3),
    BLAST_FURNACE(RecipeType.BLASTING, RecipeBookType.BLAST_FURNACE, 6),
    STONECUTTER(null, null, -1),
    GRINDSTONE(null, null, -1),
    SMITHING(null, null, -1),
    BREWING(null, null, -1);

    /** Non-null for cooking stations; null for NONE and CRAFTING. */
    public final @Nullable RecipeType<? extends AbstractCookingRecipe> cookingRecipeType;
    /** The recipe book type for this station. */
    public final @Nullable RecipeBookType recipeBookType;
    /**
     * Offset of this station's result slot within the cooking slot block
     * (result = base, input = base+1, fuel = base+2). -1 for non-cooking stations.
     */
    public final int cookingSlotBase;

    Station(
            @Nullable RecipeType<? extends AbstractCookingRecipe> recipeType,
            @Nullable RecipeBookType recipeBookType,
            int cookingSlotBase) {
        this.cookingRecipeType = recipeType;
        this.recipeBookType = recipeBookType;
        this.cookingSlotBase = cookingSlotBase;
    }

    public boolean isCooking() {
        return cookingRecipeType != null;
    }

    public boolean isInstantStation() {
        return this == STONECUTTER || this == GRINDSTONE || this == SMITHING;
    }

    public boolean isBrewing() {
        return this == BREWING;
    }
}
