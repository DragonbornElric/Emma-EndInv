package com.emma.endinv.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BrewingFuel;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 26.3 made furnace fuel, brewing fuel and brewing recipes data-driven (COOKING_FUEL / BREWING_FUEL
 * components resolved against a container_process loot context, BrewingRecipe in the recipe manager).
 * This mirrors what AbstractFurnaceBlockEntity and BrewingStandBlockEntity do, for the virtual stations.
 */
public final class StationProcessing {

    /** BrewingStandBlockEntity.DEFAULT_BREW_TIME. */
    public static final int BREW_TIME = 400;
    /** BrewingStandBlockEntity.DEFAULT_FUEL_USES, used for the fuel gauge. */
    public static final int DEFAULT_FUEL_USES = 20;

    private StationProcessing() {}

    private static Block blockFor(Station st) {
        return switch (st) {
            case SMOKER -> Blocks.SMOKER;
            case BLAST_FURNACE -> Blocks.BLAST_FURNACE;
            case BREWING -> Blocks.BREWING_STAND;
            default -> Blocks.FURNACE;
        };
    }

    /**
     * The loot context a placed station of this kind would use (BaseContainerBlockEntity.getLootContext),
     * so that data-driven values such as "fast_cooking" for smoker/blast furnace resolve the same way.
     */
    public static LootContext lootContext(ServerLevel level, Station st, BlockPos pos) {
        BlockState state = blockFor(st).defaultBlockState();
        BlockEntity be = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
        be.setLevel(level);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.BLOCK_ENTITY, be)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.CONTAINER, (SlotProvider) be)
                .create(LootContextParamSets.CONTAINER_PROCESS);
        return new LootContext.Builder(params).create(Optional.empty());
    }

    // ── Cooking ──────────────────────────────────────────────────────────────

    public static boolean isCookingFuel(ItemStack stack) {
        return stack.has(DataComponents.COOKING_FUEL);
    }

    /** AbstractFurnaceBlockEntity.getBurnDuration. */
    public static int burnDuration(LootContext ctx, ItemStack fuel) {
        return ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime, ctx, 0);
    }

    /** AbstractFurnaceBlockEntity.getSpeedMultiplier. */
    public static float cookingSpeed(LootContext ctx, ItemStack fuel) {
        return ResolvableFloat.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::speedMultiplier, ctx, 1.0F);
    }

    /** AbstractFurnaceBlockEntity.getTotalCookTime. */
    public static int totalCookTime(AbstractCookingRecipe recipe, float speed) {
        int time = recipe.cookingTime();
        return speed > 0.0F ? (int) Math.ceil(time / speed) : time;
    }

    // ── Brewing ──────────────────────────────────────────────────────────────

    public static boolean isBrewingFuel(ItemStack stack) {
        return stack.has(DataComponents.BREWING_FUEL);
    }

    public static boolean isBrewingReagent(ServerLevel level, ItemStack stack) {
        return level.recipeAccess().propertySet(RecipePropertySet.BREWING_REAGENTS).test(stack);
    }

    /** BrewingStandBlockEntity.getUses. */
    public static int brewingFuelUses(LootContext ctx, BrewingFuel fuel) {
        return fuel.uses().get(ctx, 0);
    }

    /** BrewingStandBlockEntity.getSpeedMultiplier. */
    public static float brewingSpeed(LootContext ctx, BrewingFuel fuel) {
        return fuel.speedMultiplier().get(ctx, 1.0F);
    }

    /** Brew time for one batch, as set in BrewingStandBlockEntity.serverTick. */
    public static int brewTime(float speed) {
        return (int) Math.ceil(BREW_TIME / (speed > 0.0F ? speed : 1.0F));
    }

    public static Optional<RecipeHolder<BrewingRecipe>> brewingRecipe(ServerLevel level, ItemStack potion, ItemStack reagent) {
        return level.recipeAccess().getRecipeFor(RecipeType.BREWING, new BrewingInput(potion, reagent), level);
    }

    /** BrewingStandBlockEntity.isBrewable. */
    public static boolean isBrewable(ServerLevel level, ItemStack reagent, ItemStack... potions) {
        if (reagent.isEmpty() || !isBrewingReagent(level, reagent)) return false;
        for (ItemStack potion : potions) {
            if (!potion.isEmpty() && brewingRecipe(level, potion, reagent).isPresent()) return true;
        }
        return false;
    }

    /** One potion slot of BrewingStandBlockEntity.doBrew: the brewed result, or the potion unchanged. */
    public static ItemStack brew(ServerLevel level, ItemStack potion, ItemStack reagent) {
        if (potion.isEmpty()) return potion;
        return brewingRecipe(level, potion, reagent)
                .map(r -> r.value().assemble(new BrewingInput(potion, reagent)))
                .orElse(potion);
    }
}
