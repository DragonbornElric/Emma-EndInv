package com.emma.endinv.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

public record BrewingState(
        ItemStack ingredient, ItemStack fuel,
        ItemStack potion0, ItemStack potion1, ItemStack potion2,
        int brewTime, int fuelAmount) {

    public static final BrewingState EMPTY = new BrewingState(
            ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            0, 0);

    public static final Codec<BrewingState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("ingredient", ItemStack.EMPTY).forGetter(BrewingState::ingredient),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("fuel", ItemStack.EMPTY).forGetter(BrewingState::fuel),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("potion0", ItemStack.EMPTY).forGetter(BrewingState::potion0),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("potion1", ItemStack.EMPTY).forGetter(BrewingState::potion1),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("potion2", ItemStack.EMPTY).forGetter(BrewingState::potion2),
                    Codec.INT.optionalFieldOf("brew_time", 0).forGetter(BrewingState::brewTime),
                    Codec.INT.optionalFieldOf("fuel_amount", 0).forGetter(BrewingState::fuelAmount)
            ).apply(instance, BrewingState::new)
    );

    public boolean isEmpty() {
        return ingredient.isEmpty() && fuel.isEmpty()
                && potion0.isEmpty() && potion1.isEmpty() && potion2.isEmpty();
    }
}
