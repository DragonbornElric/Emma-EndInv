package com.emma.endinv.menu;

import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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
    BREWING(null, null, -1),
    /** Added in 1.4.5; last so the earlier ordinals (sent over the network) don't change. */
    ENCHANTING(null, null, -1);

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
        return this == STONECUTTER || this == GRINDSTONE || this == SMITHING || this == ENCHANTING;
    }

    public boolean isBrewing() {
        return this == BREWING;
    }

    /**
     * The block item that unlocks this station when stations are not free
     * ({@code FreeCraftingStations = false}); null for {@link #NONE}.
     */
    /** The station whose {@link #unlockItem()} is {@code item}, or null. */
    public static @Nullable Station forUnlockItem(Item item) {
        for (Station st : values()) {
            if (st.unlockItem() == item) return st;
        }
        return null;
    }

    public @Nullable Item unlockItem() {
        return switch (this) {
            case NONE -> null;
            case CRAFTING -> Items.CRAFTING_TABLE;
            case FURNACE -> Items.FURNACE;
            case SMOKER -> Items.SMOKER;
            case BLAST_FURNACE -> Items.BLAST_FURNACE;
            case STONECUTTER -> Items.STONECUTTER;
            case GRINDSTONE -> Items.GRINDSTONE;
            case SMITHING -> Items.SMITHING_TABLE;
            case BREWING -> Items.BREWING_STAND;
            case ENCHANTING -> Items.ENCHANTING_TABLE;
        };
    }
}
