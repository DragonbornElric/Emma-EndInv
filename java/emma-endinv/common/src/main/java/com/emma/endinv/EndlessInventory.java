package com.emma.endinv;


import com.emma.endinv.menu.BrewingState;
import com.emma.endinv.menu.FurnaceState;
import com.emma.endinv.menu.Station;
import com.emma.endinv.menu.StationProcessing;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BrewingFuel;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.storage.loot.LootContext;
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
                    Item.CODEC.fieldOf(ITEM_ID_KEY).forGetter(e->e.getKey().item().builtInRegistryHolder()),
                    DataComponentPatch.CODEC.optionalFieldOf(COMPONENTS_KEY, DataComponentPatch.EMPTY).forGetter(e -> e.getKey().components()),
                    Codec.INT.fieldOf(ITEM_COUNT_KEY).forGetter(e -> e.getValue().count()),
                    Codec.LONG.fieldOf(LAST_MOD_TIME_LONG_KEY).forGetter(e -> e.getValue().lastModTime())
            ).apply(instance, (item, com, c, mod) -> Map.entry(new ItemKey(item, com), new ItemState(c, mod))))
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

    /** Station names; unknown names (a station removed in a later version) are dropped on load. */
    private static final Codec<List<Station>> UNLOCKED_STATIONS_CODEC = Codec.STRING.listOf().xmap(
            names -> names.stream().flatMap(n -> Arrays.stream(Station.values()).filter(st -> st.name().equals(n))).toList(),
            stations -> stations.stream().map(Station::name).toList());

    public static final Codec<EndlessInventory> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ITEM_MAP_CODEC.fieldOf(ITEM_LIST_KEY).forGetter(EndlessInventory::getItemMap),
                    EndInvAffinities.CODEC.fieldOf(AFFINITY_KEY).forGetter(endinv->endinv.affinities),
                    UUIDUtil.CODEC.fieldOf(UUID_KEY).forGetter(EndlessInventory::getUuid),
                    UUIDUtil.CODEC.optionalFieldOf(OWNER_UUID_KEY).forGetter(endinv -> Optional.ofNullable(endinv.owner)),
                    Codec.list(UUIDUtil.CODEC).fieldOf(WHITE_LIST_KEY).forGetter(ei -> ei.white_list),
                    Codec.STRING.xmap(Accessibility::valueOf, Accessibility::name).fieldOf(ACCESSIBILITY_KEY).forGetter(EndlessInventory::getAccessibility),
                    Codec.INT.fieldOf(MAX_STACK_SIZE_INT_KEY).forGetter(EndlessInventory::getMaxItemStackSize),
                    Codec.BOOL.fieldOf(INFINITY_BOOL_KEY).forGetter(EndlessInventory::isInfinityMode),
                    FurnaceState.CODEC.optionalFieldOf("furnace_state", FurnaceState.EMPTY).forGetter(ei -> ei.furnaceState),
                    FurnaceState.CODEC.optionalFieldOf("smoker_state", FurnaceState.EMPTY).forGetter(ei -> ei.smokerState),
                    FurnaceState.CODEC.optionalFieldOf("blast_furnace_state", FurnaceState.EMPTY).forGetter(ei -> ei.blastFurnaceState),
                    BrewingState.CODEC.optionalFieldOf("brewing_state", BrewingState.EMPTY).forGetter(ei -> ei.brewingState),
                    UNLOCKED_STATIONS_CODEC.optionalFieldOf("unlocked_stations", List.of()).forGetter(ei -> List.copyOf(ei.unlockedStations)),
                    Codec.INT.optionalFieldOf("bookshelves", 0).forGetter(ei -> ei.bookshelves)
                        ).apply(instance, (itemMap, aff, uuid, ownerUuid, wLstUid, acc, maxSize, infBool, furnaceState, smokerState, blastState, brewingState, unlocked, shelves) -> {
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
                               endInv.unlockedStations.addAll(unlocked);
                               endInv.bookshelves = Math.clamp(shelves, 0, EndlessInventory.MAX_BOOKSHELVES);
                               return endInv;
                    }
            )
    );

    @SuppressWarnings("unchecked")
    private final List<ItemStack>[] sortedViews = new List[SortType.values().length];

    private final long[] lastSortedTimes = new long[SortType.values().length];

    public final Set<UUID> viewerIds = new HashSet<>();
    private boolean dirty = false;
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

    /** Stations a player unlocked by putting their block in (used when {@code FreeCraftingStations} is off). */
    private final EnumSet<Station> unlockedStations = EnumSet.noneOf(Station.class);

    public boolean isStationUnlocked(Station st) { return unlockedStations.contains(st); }

    /** The stations unlocked by putting their block in (a copy). */
    public Set<Station> getUnlockedStations() { return EnumSet.copyOf(unlockedStations); }

    public void lockStation(Station st) {
        if (unlockedStations.remove(st)) setChanged();
    }

    /** Bookshelves put into the enchanting station: its enchanting power, like bookshelves around a table. */
    public static final int MAX_BOOKSHELVES = 15;
    private int bookshelves = 0;

    public int getBookshelves() { return bookshelves; }

    public void setBookshelves(int count) {
        int clamped = Math.clamp(count, 0, MAX_BOOKSHELVES);
        if (clamped != bookshelves) {
            bookshelves = clamped;
            setChanged();
        }
    }

    public void unlockStation(Station st) {
        if (st != Station.NONE && unlockedStations.add(st)) setChanged();
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
        float speed       = state.speedMultiplier();

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
                Optional<RecipeHolder<AbstractCookingRecipe>> optRecipe =
                    getCookingRecipeFor(st, input, level);
                if (optRecipe.isPresent()) {
                    AbstractCookingRecipe recipeVal = optRecipe.get().value();
                    ItemStack burnResult = recipeVal.assemble(new SingleRecipeInput(input));
                    if (!burnResult.isEmpty() && canCookingBurn(result, burnResult)) {
                        if (!isLit && StationProcessing.isCookingFuel(fuel)) {
                            // AbstractFurnaceBlockEntity.serverTick: data-driven burn time and speed
                            LootContext ctx = StationProcessing.lootContext(level, st, stationPos(level));
                            int newLitTime = StationProcessing.burnDuration(ctx, fuel);
                            litTime     = newLitTime;
                            litDuration = newLitTime;
                            speed       = StationProcessing.cookingSpeed(ctx, fuel);
                            if (cookDuration > 0 && cookTime < cookDuration) {
                                float progress = (float) cookTime / cookDuration;
                                cookDuration = StationProcessing.totalCookTime(recipeVal, speed);
                                cookTime = (int) Math.ceil(progress * cookDuration);
                            }
                            if (newLitTime > 0) {
                                Item fuelItem = fuel.getItem();
                                fuel.shrink(1);
                                if (fuel.isEmpty()) {
                                    ItemStackTemplate rem = fuelItem.getCraftingRemainder();
                                    fuel = rem != null ? rem.create() : ItemStack.EMPTY;
                                }
                                isLit   = true;
                                changed = true;
                            }
                        }
                        if (isLit) {
                            cookTime++;
                            if (cookDuration == 0) cookDuration = StationProcessing.totalCookTime(recipeVal, speed);
                            if (cookTime >= cookDuration) {
                                cookTime    = 0;
                                cookDuration = StationProcessing.totalCookTime(recipeVal, speed);
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
                                    float xp = recipeVal.experience();
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
            setCookingState(st, new FurnaceState(input, fuel, result, litTime, litDuration, cookTime, cookDuration, speed));
            setChanged();
        }
    }

    public void tickBrewingBackground(ServerLevel level) {
        BrewingState state = brewingState;
        if (state.isEmpty()) return;

        int fuelAmount = state.fuelAmount();
        int brewTime   = state.brewTime();
        float speed    = state.speedMultiplier();
        ItemStack ingredient = state.ingredient().copy();
        ItemStack fuel       = state.fuel().copy();
        ItemStack potion0    = state.potion0().copy();
        ItemStack potion1    = state.potion1().copy();
        ItemStack potion2    = state.potion2().copy();

        boolean changed = false;

        // Refuel if depleted and a brewing fuel (BREWING_FUEL component) is available
        BrewingFuel brewingFuel = fuel.get(net.minecraft.core.component.DataComponents.BREWING_FUEL);
        if (fuelAmount <= 0 && brewingFuel != null) {
            LootContext ctx = StationProcessing.lootContext(level, Station.BREWING, stationPos(level));
            fuelAmount = StationProcessing.brewingFuelUses(ctx, brewingFuel);
            speed = StationProcessing.brewingSpeed(ctx, brewingFuel);
            Item fuelItem = fuel.getItem();
            fuel.shrink(1);
            ItemStackTemplate rem = fuelItem.getCraftingRemainder();
            if (rem != null) {
                if (fuel.isEmpty()) fuel = rem.create();
                else addItem(rem.create());
            }
            changed = true;
        }

        boolean brewable = StationProcessing.isBrewable(level, ingredient, potion0, potion1, potion2);

        if (brewTime > 0) {
            brewTime--;
            if (brewTime == 0) {
                // doBrew
                if (!ingredient.isEmpty() && brewable) {
                    potion0 = StationProcessing.brew(level, potion0, ingredient);
                    potion1 = StationProcessing.brew(level, potion1, ingredient);
                    potion2 = StationProcessing.brew(level, potion2, ingredient);
                    Item ingredientItem = ingredient.getItem();
                    ingredient.shrink(1);
                    ItemStackTemplate rem = ingredientItem.getCraftingRemainder();
                    if (rem != null) {
                        if (ingredient.isEmpty()) ingredient = rem.create();
                        else addItem(rem.create());
                    }
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
            brewTime = StationProcessing.brewTime(speed);
            changed  = true;
        }

        if (changed) {
            brewingState = new BrewingState(ingredient, fuel, potion0, potion1, potion2, brewTime, fuelAmount, speed);
            setChanged();
        }
    }

    /** Where the virtual station "stands" for data-driven fuel values: the owner, if online. */
    private BlockPos stationPos(ServerLevel level) {
        ServerPlayer ownerPlayer = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        return ownerPlayer != null ? ownerPlayer.blockPosition() : BlockPos.ZERO;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<RecipeHolder<AbstractCookingRecipe>> getCookingRecipeFor(
            Station st, ItemStack input, ServerLevel level) {
        return (Optional<RecipeHolder<AbstractCookingRecipe>>) (Optional<?>)
                level.getServer().getRecipeManager()
                        .getRecipeFor(st.cookingRecipeType, new SingleRecipeInput(input), level);
    }

    private boolean canCookingBurn(ItemStack current, ItemStack burnResult) {
        if (current.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(current, burnResult)) return false;
        return current.getCount() + burnResult.getCount() <= Math.min(64, current.getMaxStackSize());
    }
}
