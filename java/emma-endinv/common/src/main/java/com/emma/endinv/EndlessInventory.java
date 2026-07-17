package com.emma.endinv;


import com.emma.endinv.menu.BrewingState;
import com.emma.endinv.menu.FurnaceState;
import com.emma.endinv.menu.Station;
import com.emma.endinv.network.payloads.toClient.EndInvContent;
import com.emma.endinv.network.payloads.toClient.EndInvMetadata;
import com.emma.endinv.util.Accessibility;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemState;
import com.emma.endinv.util.SortType;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.slf4j.Logger;

import org.jetbrains.annotations.Nullable;
import java.util.*;

import static com.emma.endinv.data.EndInvCodecStrategy.*;

/**An item container may have endless storage.*/
public class EndlessInventory extends SourceInventory {//todo add content transfer mode as a field

    private static final Logger LOGGER = LogUtils.getLogger();
    @SuppressWarnings("deprecation")
    public static final Codec<Map<ItemKey, ItemState>> ITEM_MAP_CODEC = Codec.list(
            RecordCodecBuilder.<Map.Entry<ItemKey,ItemState>>create(instance -> instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf(ITEM_ID_KEY)
                            .forGetter(entry -> entry.getKey().item()),
                    CompoundTag.CODEC.optionalFieldOf(COMPONENTS_KEY)
                            .forGetter(entry -> Optional.ofNullable(entry.getKey().tag())),
                    Codec.INT.fieldOf(ITEM_COUNT_KEY).forGetter(e -> e.getValue().count()),
                    Codec.LONG.fieldOf(LAST_MOD_TIME_LONG_KEY).forGetter(e -> e.getValue().lastModTime())
            ).apply(instance, (item, tag, count, modified) ->
                    Map.entry(new ItemKey(item, tag.orElse(null)), new ItemState(count, modified))))
    ).xmap(
            lst -> {
                Map<ItemKey, ItemState> map = new Object2ObjectLinkedOpenHashMap<>(lst.size());
                for(var e : lst) map.put(e.getKey(),e.getValue());
                return map;
            },
            map -> {
                var list = new ArrayList<Map.Entry<ItemKey, ItemState>>(map.size());
                map.forEach((k,v)->list.add(Map.entry(k,v)));
                return list;
            }
    );

    public static final Codec<EndlessInventory> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ITEM_MAP_CODEC.fieldOf(ITEM_LIST_KEY).forGetter(EndlessInventory::getItemMap),
                    EndInvAffinities.CODEC.fieldOf(AFFINITY_KEY).forGetter(endinv->endinv.affinities),
                    UUIDUtil.CODEC.fieldOf(UUID_KEY).forGetter(EndlessInventory::getUuid),
                    UUIDUtil.CODEC.optionalFieldOf(OWNER_UUID_KEY)
                            .forGetter(endinv -> Optional.ofNullable(endinv.owner)),
                    Codec.list(UUIDUtil.CODEC).fieldOf(WHITE_LIST_KEY).forGetter(ei -> ei.white_list),
                    Codec.STRING.xmap(Accessibility::valueOf, Accessibility::name).fieldOf(ACCESSIBILITY_KEY).forGetter(EndlessInventory::getAccessibility),
                    Codec.INT.fieldOf(MAX_STACK_SIZE_INT_KEY).forGetter(EndlessInventory::getMaxItemStackSize),
                    Codec.BOOL.fieldOf(INFINITY_BOOL_KEY).forGetter(EndlessInventory::isInfinityMode),
                    FurnaceState.CODEC.optionalFieldOf("furnace_state", FurnaceState.EMPTY).forGetter(ei -> ei.furnaceState),
                    FurnaceState.CODEC.optionalFieldOf("smoker_state", FurnaceState.EMPTY).forGetter(ei -> ei.smokerState),
                    FurnaceState.CODEC.optionalFieldOf("blast_furnace_state", FurnaceState.EMPTY).forGetter(ei -> ei.blastFurnaceState),
                    BrewingState.CODEC.optionalFieldOf("brewing_state", BrewingState.EMPTY).forGetter(ei -> ei.brewingState)
                        ).apply(instance, (itemMap, aff, uuid, ownerUuid, wLstUid, acc, maxSize, infBool, furnaceState, smokerState, blastState, brewingState) -> {
                            EndlessInventory endInv = new EndlessInventory(uuid, aff);
                               endInv.itemMap.putAll(itemMap);
                               endInv.owner = ownerUuid.orElse(null);
                               endInv.white_list.addAll(wLstUid);
                               endInv.setAccessibility(acc);
                               endInv.setMaxItemStackSize(maxSize);
                               endInv.setInfinityMode(infBool);
                               endInv.furnaceState = furnaceState;
                               endInv.smokerState = smokerState;
                               endInv.blastFurnaceState = blastState;
                               endInv.brewingState = brewingState;
                               return endInv;
                    }
            )
    );

    @SuppressWarnings("unchecked")
    private final List<ItemStack>[] sortedViews = new List[SortType.values().length];

    private final long[] lastSortedTimes = new long[SortType.values().length];

    public final Set<UUID> viewerIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean dirty = false;
    FurnaceState furnaceState = FurnaceState.EMPTY;
    FurnaceState smokerState = FurnaceState.EMPTY;
    FurnaceState blastFurnaceState = FurnaceState.EMPTY;
    BrewingState brewingState = BrewingState.EMPTY;

    public FurnaceState getFurnaceState() { return furnaceState; }
    public void setFurnaceState(FurnaceState state) { this.furnaceState = state; }

    public FurnaceState getCookingState(Station st) {
        return switch (st) {
            case FURNACE -> furnaceState;
            case SMOKER -> smokerState;
            case BLAST_FURNACE -> blastFurnaceState;
            default -> FurnaceState.EMPTY;
        };
    }

    public void setCookingState(Station st, FurnaceState state) {
        switch (st) {
            case FURNACE -> furnaceState = state;
            case SMOKER -> smokerState = state;
            case BLAST_FURNACE -> blastFurnaceState = state;
        }
    }

    public BrewingState getBrewingState() { return brewingState; }
    public void setBrewingState(BrewingState state) { this.brewingState = state; }

    public EndlessInventory(){
        this(UUID.randomUUID());
    }

    public EndlessInventory(UUID uuid){
        this(uuid, new EndInvAffinities());
    }

    private EndlessInventory(UUID uuid, EndInvAffinities affinities){
        super(uuid, affinities);
    }

    protected List<ItemStack> getSortedView(SortType type, boolean reverse) {
        int idx = type.ordinal();
        List<ItemStack> result;
        synchronized (sortedViews) {
            if (lastSortedTimes[idx] != lastModTime || sortedViews[idx] == null) {
                List<ItemStack> view = snapshotItems();
                view.sort(ModInfo.sortHelper.getComparator(type, this));
                sortedViews[idx] = view;
                lastSortedTimes[idx] = lastModTime;
            }
            result = new ArrayList<>(sortedViews[idx]);
        }
        if(reverse) Collections.reverse(result);
        return result;
    }

    @Nullable
    public Optional<ServerPlayer> getOwner(ServerLevel level) {
        return level.getPlayers(pl->Objects.equals(pl.getUUID(),owner)).stream().findAny();
    }

    public void setChanged() {
        super.setChanged();
        if (ServerLevelEndInv.levelEndInvData != null) {
            ServerLevelEndInv.levelEndInvData.setDirty();
        }
        this.dirty = true;
    }

    /**
     * Set endinv modState to new greater state.
     * @param newState should be greater than its original state
     * @return endinv's modState that has been updated
     */
    public long updateModState(long newState){
        if (newState <= lastModTime) {
            newState = lastModTime + 1;
        }
        this.lastModTime = newState;
        return lastModTime;
    }

    public void notifyViewersRemoved(MinecraftServer server) {
        EndInvContent empty = new EndInvContent(Map.of());
        for (UUID uuid : new ArrayList<>(viewerIds)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                ModInfo.getPacketDistributor().sendToPlayer(player, empty);
            }
        }
        viewerIds.clear();
    }

    public void addToWhitelist(UUID uuid) {
        white_list.add(uuid);
        setChanged();
    }

    public void removeFromWhitelist(UUID uuid) {
        white_list.remove(uuid);
        setChanged();
    }

    public void broadcastChanges(MinecraftServer server) {
        if (!dirty) return;
        dirty = false;
        EndInvContent contentPayload = new EndInvContent(this.getItemMap());
        EndInvMetadata metaPayload = EndInvMetadata.getWith(this);
        for (UUID uuid : new ArrayList<>(viewerIds)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                ModInfo.getPacketDistributor().sendToPlayer(player, contentPayload);
                ModInfo.getPacketDistributor().sendToPlayer(player, metaPayload);
            }
        }
    }

    public void tickCookingBackground(ServerLevel level, Station st) {
        FurnaceState state = getCookingState(st);
        if (state.isEmpty()) return;

        ItemStack input   = state.input().copy();
        ItemStack fuel    = state.fuel().copy();
        ItemStack result  = state.result().copy();
        int litTime       = state.litTime();
        int litDuration   = state.litDuration();
        int cookTime      = state.cookTime();
        int cookDuration  = state.cookDuration();

        boolean hasIngredient = !input.isEmpty();
        boolean hasFuel       = !fuel.isEmpty();
        boolean changed       = false;

        boolean isLit;
        if (litTime > 0) {
            litTime--;
            isLit = litTime > 0;
            changed = true;
        } else {
            isLit = false;
        }

        if (isLit || (hasFuel && hasIngredient)) {
            if (hasIngredient) {
                Optional<? extends AbstractCookingRecipe> optRecipe =
                    getCookingRecipeFor(st, input, level);
                if (optRecipe.isPresent()) {
                    AbstractCookingRecipe recipeVal = optRecipe.get();
                    SimpleContainer recipeInput = new SimpleContainer(input);
                    ItemStack burnResult = recipeVal.assemble(recipeInput, level.registryAccess());
                    if (!burnResult.isEmpty() && canCookingBurn(result, burnResult)) {
                        if (!isLit) {
                            int newLitTime = AbstractFurnaceBlockEntity.getFuel()
                                    .getOrDefault(fuel.getItem(), 0);
                            if (newLitTime > 0) {
                                litTime    = newLitTime;
                                litDuration = newLitTime;
                                Item fuelItem = fuel.getItem();
                                fuel.shrink(1);
                                if (fuel.isEmpty() && fuelItem.hasCraftingRemainingItem()) {
                                    fuel = new ItemStack(fuelItem.getCraftingRemainingItem());
                                }
                                isLit   = true;
                                changed = true;
                            }
                        }
                        if (isLit) {
                            cookTime++;
                            if (cookDuration == 0) cookDuration = recipeVal.getCookingTime();
                            if (cookTime >= cookDuration) {
                                cookTime    = 0;
                                cookDuration = recipeVal.getCookingTime();
                                if (result.isEmpty()) {
                                    result = burnResult.copy();
                                } else {
                                    result.grow(burnResult.getCount());
                                }
                                if (st == Station.FURNACE
                                        && input.is(net.minecraft.world.item.Items.WET_SPONGE)
                                        && !fuel.isEmpty()
                                        && fuel.is(net.minecraft.world.item.Items.BUCKET)) {
                                    fuel = new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
                                }
                                input.shrink(1);
                                ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
                                if (ownerPlayer != null) {
                                    float xp = recipeVal.getExperience();
                                    if (xp > 0) {
                                        int base = Mth.floor(xp);
                                        if (xp - base > 0 && Math.random() < (xp - base)) base++;
                                        ExperienceOrb.award(level, ownerPlayer.position(), base);
                                    }
                                }
                            }
                            changed = true;
                        } else if (cookTime > 0) {
                            cookTime = 0;
                            changed  = true;
                        }
                    }
                }
            } else if (cookTime > 0) {
                cookTime = 0;
                changed  = true;
            }
        } else if (cookTime > 0) {
            cookTime = Mth.clamp(cookTime - 2, 0, cookDuration);
            changed  = true;
        }

        if (changed) {
            setCookingState(st, new FurnaceState(input, fuel, result, litTime, litDuration, cookTime, cookDuration));
            setChanged();
        }
    }

    public void tickBrewingBackground(ServerLevel level) {
        BrewingState state = brewingState;
        if (state.isEmpty()) return;

        int fuelAmount = state.fuelAmount();
        int brewTime   = state.brewTime();
        ItemStack ingredient = state.ingredient().copy();
        ItemStack fuel       = state.fuel().copy();
        ItemStack potion0    = state.potion0().copy();
        ItemStack potion1    = state.potion1().copy();
        ItemStack potion2    = state.potion2().copy();

        boolean changed = false;

        // Refuel if depleted and blaze powder available
        if (fuelAmount <= 0 && fuel.is(Items.BLAZE_POWDER)) {
            fuelAmount = 20;
            fuel.shrink(1);
            changed = true;
        }

        boolean brewable = isBrewableBackground(ingredient, potion0, potion1, potion2);

        if (brewTime > 0) {
            brewTime--;
            if (brewTime == 0) {
                // doBrew
                if (!ingredient.isEmpty()) {
                    if (!potion0.isEmpty()) potion0 = PotionBrewing.mix(ingredient, potion0);
                    if (!potion1.isEmpty()) potion1 = PotionBrewing.mix(ingredient, potion1);
                    if (!potion2.isEmpty()) potion2 = PotionBrewing.mix(ingredient, potion2);
                    ingredient.shrink(1);
                }
                changed = true;
            } else if (!brewable) {
                brewTime = 0;
                changed  = true;
            } else {
                changed  = true;
            }
        } else if (fuelAmount > 0 && brewable) {
            fuelAmount--;
            brewTime = 400;
            changed  = true;
        }

        if (changed) {
            brewingState = new BrewingState(ingredient, fuel, potion0, potion1, potion2, brewTime, fuelAmount);
            setChanged();
        }
    }

    private static boolean isBrewableBackground(
            ItemStack ingredient, ItemStack p0, ItemStack p1, ItemStack p2) {
        if (ingredient.isEmpty() || !PotionBrewing.isIngredient(ingredient)) return false;
        return (!p0.isEmpty() && PotionBrewing.hasMix(p0, ingredient))
            || (!p1.isEmpty() && PotionBrewing.hasMix(p1, ingredient))
            || (!p2.isEmpty() && PotionBrewing.hasMix(p2, ingredient));
    }

    private Optional<? extends AbstractCookingRecipe> getCookingRecipeFor(
            Station st, ItemStack input, ServerLevel level) {
        return level.getServer().getRecipeManager().getRecipeFor(
                st.cookingRecipeType,
                new SimpleContainer(input),
                level
        );
    }

    private boolean canCookingBurn(ItemStack current, ItemStack burnResult) {
        if (current.isEmpty()) return true;
        if (!ItemStack.isSameItemSameTags(current, burnResult)) return false;
        return current.getCount() + burnResult.getCount() <= Math.min(64, current.getMaxStackSize());
    }
}
