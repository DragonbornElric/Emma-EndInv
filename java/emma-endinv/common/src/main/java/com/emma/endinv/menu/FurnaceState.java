package com.emma.endinv.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

public record FurnaceState(
        ItemStack input, ItemStack fuel, ItemStack result,
        int litTime, int litDuration, int cookTime, int cookDuration) {

    public static final FurnaceState EMPTY = new FurnaceState(
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 0, 0, 0, 0);

    public static final Codec<FurnaceState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("input", ItemStack.EMPTY).forGetter(FurnaceState::input),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("fuel", ItemStack.EMPTY).forGetter(FurnaceState::fuel),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("result", ItemStack.EMPTY).forGetter(FurnaceState::result),
                    Codec.INT.optionalFieldOf("lit_time", 0).forGetter(FurnaceState::litTime),
                    Codec.INT.optionalFieldOf("lit_duration", 0).forGetter(FurnaceState::litDuration),
                    Codec.INT.optionalFieldOf("cook_time", 0).forGetter(FurnaceState::cookTime),
                    Codec.INT.optionalFieldOf("cook_duration", 0).forGetter(FurnaceState::cookDuration)
            ).apply(instance, FurnaceState::new)
    );

    public boolean isEmpty() {
        return input.isEmpty() && fuel.isEmpty() && result.isEmpty() && litTime == 0;
    }
}
