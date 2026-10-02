package com.emma.endinv.menu;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ModInfo;
import com.emma.endinv.menu.BrewingState;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.SourceInventory;
import com.emma.endinv.client.CachedSrcInv;
import com.emma.endinv.client.gui.ScreenFramework;
import com.emma.endinv.client.option.ClientConfigs;
import com.emma.endinv.client.option.EIMConfig;
import com.emma.endinv.menu.page.PageType;
import com.emma.endinv.menu.page.PageTypeRegistry;
import com.emma.endinv.menu.page.pageManager.PageMetaDataManager;
import com.emma.endinv.menu.page.pageManager.PageQuickMoveHandler;
import com.emma.endinv.network.payloads.PageData;
import com.emma.endinv.util.SortType;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BrewingFuel;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

import static com.emma.endinv.ModRegistries.Items;
import static com.emma.endinv.ModRegistries.Menus;
import static com.emma.endinv.ServerLevelEndInv.getEndInvForPlayer;

/**The menu who links to EndlessInventory
 * Implementation: maybe let EIS implements them
 */
public class EndlessInventoryMenu extends RecipeBookMenu implements PageMetaDataManager, PageQuickMoveHandler.PageQuickMoveOverride {


    private final SourceInventory sourceInventory;

    private final CraftingContainer craftMatrix = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer craftResult = new ResultContainer();

    // Cooking stations — 3 slots each (0=input, 1=fuel, 2=result), 3 stations = 9 slots total
    // Station.cookingSlotBase: FURNACE=0, SMOKER=3, BLAST_FURNACE=6
    // Actual menu slot index = COOKING_SLOT_START + station.cookingSlotBase + [0|1|2]
    public static final Station[] COOKING_STATIONS = {Station.FURNACE, Station.SMOKER, Station.BLAST_FURNACE};
    private final java.util.EnumMap<Station, SimpleContainer> cookingContainers = new java.util.EnumMap<>(Station.class);
    private final java.util.EnumMap<Station, DataSlot[]> cookingDataSlots = new java.util.EnumMap<>(Station.class);
    private final java.util.EnumMap<Station, Map<ResourceKey<Recipe<?>>, Integer>> cookingRecipesUsed = new java.util.EnumMap<>(Station.class);
    /** Server-only: speed multiplier of the fuel last burnt in each cooking station (26.3 COOKING_FUEL). */
    private final java.util.EnumMap<Station, Float> cookingSpeeds = new java.util.EnumMap<>(Station.class);

    // Stonecutter
    private final SimpleContainer stonecutterInput = new SimpleContainer(1) {
        @Override public void setChanged() { super.setChanged(); EndlessInventoryMenu.this.slotsChanged(this); }
    };
    private final SimpleContainer stonecutterResult = new SimpleContainer(1);
    private List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> stonecutterRecipes = List.of();
    private ItemStack stonecutterLastInput = ItemStack.EMPTY;
    private final DataSlot stonecutterSelectedRecipe = DataSlot.standalone();

    // Grindstone
    private final SimpleContainer grindstoneRepairSlots = new SimpleContainer(2) {
        @Override public void setChanged() { super.setChanged(); EndlessInventoryMenu.this.slotsChanged(this); }
    };
    private final SimpleContainer grindstoneResult = new SimpleContainer(1);
    private int grindstoneXpReward = 0;

    // Smithing
    private final SimpleContainer smithingInput = new SimpleContainer(3) {
        @Override public void setChanged() { super.setChanged(); EndlessInventoryMenu.this.slotsChanged(this); }
    };
    private final SimpleContainer smithingResult = new SimpleContainer(1);

    // Brewing
    private final SimpleContainer brewingContainer = new SimpleContainer(5);
    private final DataSlot brewingTimeSlot = DataSlot.standalone();
    private final DataSlot brewingFuelSlot = DataSlot.standalone();
    /** Server-only: speed multiplier of the brewing fuel last consumed (26.3 BREWING_FUEL). */
    private float brewingSpeed = 1.0F;

    private static final int CRAFT_GRID_WIDTH = 3;
    private static final int CRAFT_GRID_HEIGHT = 3;
    private static final int RESULT_SLOT_INDEX = 0;
    private static final int CRAFT_SLOT_START = RESULT_SLOT_INDEX + 1;
    private static final int CRAFT_SLOT_COUNT = CRAFT_GRID_WIDTH * CRAFT_GRID_HEIGHT;
    private static final int CRAFT_SLOT_END = CRAFT_SLOT_START + CRAFT_SLOT_COUNT;         // = 10

    private static final int COOKING_SLOT_START = CRAFT_SLOT_END;                          // = 10
    private static final int COOKING_SLOT_END   = COOKING_SLOT_START + 9;                  // = 19

    private static final int STONECUTTER_SLOT_START = COOKING_SLOT_END;                    // = 19
    private static final int STONECUTTER_INPUT      = STONECUTTER_SLOT_START;              // = 19
    private static final int STONECUTTER_RESULT     = STONECUTTER_SLOT_START + 1;          // = 20
    private static final int STONECUTTER_SLOT_END   = STONECUTTER_SLOT_START + 2;          // = 21

    private static final int GRINDSTONE_SLOT_START  = STONECUTTER_SLOT_END;                // = 21
    private static final int GRINDSTONE_INPUT1      = GRINDSTONE_SLOT_START;               // = 21
    private static final int GRINDSTONE_INPUT2      = GRINDSTONE_SLOT_START + 1;           // = 22
    private static final int GRINDSTONE_RESULT      = GRINDSTONE_SLOT_START + 2;           // = 23
    private static final int GRINDSTONE_SLOT_END    = GRINDSTONE_SLOT_START + 3;           // = 24

    private static final int SMITHING_SLOT_START    = GRINDSTONE_SLOT_END;                 // = 24
    private static final int SMITHING_TEMPLATE      = SMITHING_SLOT_START;                 // = 24
    private static final int SMITHING_BASE          = SMITHING_SLOT_START + 1;             // = 25
    private static final int SMITHING_ADDITION      = SMITHING_SLOT_START + 2;             // = 26
    private static final int SMITHING_RESULT        = SMITHING_SLOT_START + 3;             // = 27
    private static final int SMITHING_SLOT_END      = SMITHING_SLOT_START + 4;             // = 28

    private static final int BREWING_SLOT_START     = SMITHING_SLOT_END;                   // = 28
    private static final int BREWING_POTION0        = BREWING_SLOT_START;                  // = 28
    private static final int BREWING_POTION1        = BREWING_SLOT_START + 1;              // = 29
    private static final int BREWING_POTION2        = BREWING_SLOT_START + 2;              // = 30
    private static final int BREWING_INGREDIENT     = BREWING_SLOT_START + 3;              // = 31
    private static final int BREWING_FUEL_SLOT      = BREWING_SLOT_START + 4;              // = 32
    private static final int BREWING_SLOT_END       = BREWING_SLOT_START + 5;              // = 33

    private static final int PLAYER_INV_SLOT_COUNT = 27;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INV_START = BREWING_SLOT_END;                          // = 33
    private static final int PLAYER_INV_END = PLAYER_INV_START + PLAYER_INV_SLOT_COUNT + HOTBAR_SLOT_COUNT; // = 69

    public final Player player;
    int quickcraftStatus;
    int quickcraftType;
    Set<Slot> quickcraftSlots = new HashSet<>();
    private final DataSlot rowsData = DataSlot.standalone();
    private final DataSlot itemSize = DataSlot.standalone();
    private final DataSlot maxStackSize = DataSlot.standalone();
    private static final int CRAFTING_ROWS = CRAFT_GRID_HEIGHT;

    private final DataSlot infinityMode = DataSlot.standalone();
    /** Unlocked stations as a bit mask ({@link StationUnlocks}); all of them until the server says otherwise. */
    private final DataSlot stationUnlockMask = DataSlot.standalone();
    /** Enchanting station; its two slots come after the player inventory so earlier slot indices stay put. */
    private final EnchantingStation enchanting;
    private static final int ENCHANT_ITEM  = PLAYER_INV_END;                                 // = 69
    private static final int ENCHANT_LAPIS = PLAYER_INV_END + 1;                             // = 70
    private int displayingPageIndex;
    private String displayingPageId;
    private PageType displayingPageType;
    private int baseRows = 1;
    private int visibleRows = 1;
    private Station activeStation = Station.NONE;
    public SortType sortType;
    public String searching;
    private boolean reverseSort;


    //Client constructor
    //should be only invoked on client thread
    public static EndlessInventoryMenu createClient(int id, Inventory playerInv){
        EIMConfig.Param param = ClientConfigs.EIM_CONFIG.get().adjust();
        PageData layout = new PageData(
                ScreenFramework.displayingPageType.registerName,
                param.rows(),
                param.columns(),
                ScreenFramework.sortType,
                ScreenFramework.reverseSort,
                ScreenFramework.searching
        );
        var ret = new EndlessInventoryMenu(id, playerInv, null);
        ret.init(layout);
        ret.switchPageWithId(layout.pageRegKey());
        ret.buildSlotLayout(playerInv);
        return ret;
    }

    public static MenuProvider provide(int rows){
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.empty();
            }

            @Override @Nullable
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return createServer(id,inventory,player,rows);
            }
        };
    }

    //Server constructor
    @Nullable
    public static AbstractContainerMenu createServer(int i, Inventory inventory, Player player, int rows) {
        EndlessInventory endlessInventory = getEndInvForPlayer(player).orElse(null);
        if(endlessInventory==null) return null;
        var ret = new EndlessInventoryMenu(i, inventory, endlessInventory);
        ret.init(new PageData(rows,9));
        ret.buildSlotLayout(inventory);
        ret.loadCookingStates();
        ret.loadBrewingState();
        return ret;
    }

    public static EndlessInventoryMenu createWithTemp(int i,Inventory inventory, Player player){
        EndlessInventory endInv = ServerLevelEndInv.TEMP_ENDINV_REG.get((ServerPlayer) player);
        if(endInv==null) throw new IllegalStateException("Try to create tmp menu without tmp EndInv.");
        var ret = new EndlessInventoryMenu(i,inventory,endInv);
        ret.init(PageData.DEFAULT);
        ret.buildSlotLayout(inventory);
        ret.loadCookingStates();
        ret.loadBrewingState();
        return ret;
    }

    //Common constructor
    public EndlessInventoryMenu(int id , Inventory playerInv,@Nullable EndlessInventory endlessInventory){
        super(Menus.getEndInvMenuType(),id);
        this.player = playerInv.player;
        this.sourceInventory = endlessInventory != null ? endlessInventory
                : (com.emma.endinv.platform.ILoaderProvider.get().isClient() ? CachedSrcInv.INSTANCE : new SourceInventory.Empty());

        this.enchanting = new EnchantingStation(this.player, this.sourceInventory);

        PageType initialType = PageTypeRegistry.byId(PageType.DEFAULT_KEY);
        if (initialType == null && PageTypeRegistry.size() > 0) {
            initialType = PageTypeRegistry.byIndex(0);
        }
        if (initialType == null) {
            initialType = PageType.ALL_ITEMS;
        }
        this.displayingPageType = initialType;
        this.displayingPageId = initialType.registerName;
        this.displayingPageIndex = Math.max(0, PageTypeRegistry.getIndexOf(this.displayingPageId));

        // pages are client-side; slots are built after layout initialization
        //build data slots
        itemSize.set( endlessInventory!=null ? endlessInventory.getItemSize() : 0);
        maxStackSize.set(endlessInventory!=null? endlessInventory.getMaxItemStackSize() : Integer.MAX_VALUE);
        infinityMode.set(endlessInventory!=null && endlessInventory.isInfinityMode() ? 1 : 0);
        addDataSlot(rowsData);
        addDataSlot(itemSize);
        addDataSlot(maxStackSize);
        addDataSlot(infinityMode);

        // Initialise per-station cooking containers and their 4 DataSlots (litTime, litDuration, cookTime, cookDuration)
        for (Station st : COOKING_STATIONS) {
            cookingContainers.put(st, new SimpleContainer(3));
            DataSlot[] ds = {DataSlot.standalone(), DataSlot.standalone(), DataSlot.standalone(), DataSlot.standalone()};
            cookingDataSlots.put(st, ds);
            for (DataSlot d : ds) addDataSlot(d);
            cookingRecipesUsed.put(st, new HashMap<>());
        }

        addDataSlot(stonecutterSelectedRecipe);
        addDataSlot(brewingTimeSlot);
        addDataSlot(brewingFuelSlot);
        // Last, so a client talking to a server without it keeps every station unlocked.
        stationUnlockMask.set(StationUnlocks.mask(endlessInventory));
        addDataSlot(stationUnlockMask);
        enchanting.addDataSlots(this::addDataSlot);
    }

    public void applyPageData(PageData pageData){
        init(pageData);
    }

    private void init(PageData pageData){
        int rows = Math.max(1, pageData.rows());
        this.baseRows = rows;
        int stationRows = (activeStation != Station.NONE) ? CRAFTING_ROWS : 0;
        this.visibleRows = stationRows > 0 ? Math.max(1, rows - stationRows) : rows;
        rowsData.set(this.visibleRows);
        this.sortType = pageData.sortType();
        this.searching = pageData.search();
        this.reverseSort = pageData.reverseSort();
    }

    private void buildSlotLayout(Inventory playerInventory) {
        if (!this.slots.isEmpty()) {
            return;
        }
        int craftX = 30;
        int craftRowsForPosition = Math.max(1, baseRows - CRAFTING_ROWS);
        int craftY = 18 * craftRowsForPosition + 18 + 5;
        int resultX = craftX + CRAFT_GRID_WIDTH * 18 + 40;
        int resultY = craftY + 18;

        // Crafting slots (slots 0–9)
        this.addSlot(new CraftingResultSlot(this.player, this.craftMatrix, this.craftResult, 0, resultX, resultY));
        for (int row = 0; row < CRAFT_GRID_HEIGHT; ++row) {
            for (int col = 0; col < CRAFT_GRID_WIDTH; ++col) {
                this.addSlot(new CraftingGridSlot(this.craftMatrix, col + row * CRAFT_GRID_WIDTH, craftX + col * 18, craftY + row * 18));
            }
        }

        // Cooking slots (slots 10–18): 3 stations × 3 slots, all mapped to the same screen position.
        // Only the active station's slots are visible (isActive() returns true for the matching station).
        // Slot layout within each station matches furnace.png: input=(56,craftY), fuel=(56,craftY+36), result=(116,craftY+18)
        for (Station st : COOKING_STATIONS) {
            SimpleContainer container = cookingContainers.get(st);
            this.addSlot(new EICookingResultSlot(container, 2, 116, craftY + 18, st));
            this.addSlot(new EICookingInputSlot(container, 0, 56, craftY, st));
            this.addSlot(new EICookingFuelSlot(container, 1, 56, craftY + 36, st));
        }

        // Stonecutter slots (19–20). Texture positions: input=(20,33), result=(143,33) from texture top.
        // stationY = craftY - 5; slot.y = stationY + textureY = craftY - 5 + textureY
        this.addSlot(new EIStonecutterInputSlot(stonecutterInput, 0, 20, craftY + 28));
        this.addSlot(new EIStonecutterResultSlot(stonecutterResult, 0, 143, craftY + 28));

        // Grindstone slots (21–23). Texture positions: input1=(49,19), input2=(49,40), result=(129,34)
        this.addSlot(new EIGrindstoneInputSlot(grindstoneRepairSlots, 0, 49, craftY + 14));
        this.addSlot(new EIGrindstoneInputSlot(grindstoneRepairSlots, 1, 49, craftY + 35));
        this.addSlot(new EIGrindstoneResultSlot(grindstoneResult, 0, 129, craftY + 29));

        // Smithing slots (24–27). Texture positions: template=(8,48), base=(26,48), addition=(44,48), result=(98,48)
        this.addSlot(new EISmithingInputSlot(smithingInput, 0, 8,  craftY + 43, Station.SMITHING));
        this.addSlot(new EISmithingInputSlot(smithingInput, 1, 26, craftY + 43, Station.SMITHING));
        this.addSlot(new EISmithingInputSlot(smithingInput, 2, 44, craftY + 43, Station.SMITHING));
        this.addSlot(new EISmithingResultSlot(smithingResult, 0, 98, craftY + 43));

        // Brewing slots (28–32). Texture positions: ingredient=(79,17), fuel=(17,17),
        //   potion0=(56,51), potion1=(79,58), potion2=(102,51).
        // Potions shifted to craftY+44 to stay within the 62-px station area; minor visual offset accepted.
        this.addSlot(new EIBrewingPotionSlot(brewingContainer, 0, 56,  craftY + 44));
        this.addSlot(new EIBrewingPotionSlot(brewingContainer, 1, 79,  craftY + 44));
        this.addSlot(new EIBrewingPotionSlot(brewingContainer, 2, 102, craftY + 44));
        this.addSlot(new EIBrewingIngredientSlot(brewingContainer, 3, 79, craftY + 12));
        this.addSlot(new EIBrewingFuelSlot(brewingContainer, 4, 17, craftY + 12));

        int invY = 18 * baseRows + 31;
        addStandardInventorySlots(playerInventory, 8, invY);

        // Enchanting slots (69–70), after the player inventory. Texture positions: item=(15,47), lapis=(35,47).
        for (Slot slot : enchanting.createSlots(craftY, Station.ENCHANTING, () -> activeStation)) this.addSlot(slot);
    }

    // ── Slot index helpers ───────────────────────────────────────────────────

    private int cookingResultIdx(Station st) { return COOKING_SLOT_START + st.cookingSlotBase; }
    private int cookingInputIdx(Station st)  { return COOKING_SLOT_START + st.cookingSlotBase + 1; }
    private int cookingFuelIdx(Station st)   { return COOKING_SLOT_START + st.cookingSlotBase + 2; }

    public Slot getCookingResultSlot(Station st) { return slots.get(cookingResultIdx(st)); }
    public Slot getCookingInputSlot(Station st)  { return slots.get(cookingInputIdx(st)); }
    public Slot getCookingFuelSlot(Station st)   { return slots.get(cookingFuelIdx(st)); }

    public List<Slot> getCraftingSlots(){
        return slots.subList(CRAFT_SLOT_START, CRAFT_SLOT_END);
    }

    public List<Slot> getPlayerInvSlots(){
        return slots.subList(PLAYER_INV_START, PLAYER_INV_END);
    }

    public Slot getResultSlot() {
        return slots.get(RESULT_SLOT_INDEX);
    }

    // Backward-compat furnace accessors used by the recipe book component
    public Slot getFurnaceResultSlot() { return getCookingResultSlot(Station.FURNACE); }
    public Slot getFurnaceInputSlot()  { return getCookingInputSlot(Station.FURNACE); }
    public Slot getFurnaceFuelSlot()   { return getCookingFuelSlot(Station.FURNACE); }

    public List<Slot> getInputGridSlots() {
        return getCraftingSlots();
    }

    public int getGridWidth() {
        return CRAFT_GRID_WIDTH;
    }

    public int getGridHeight() {
        return CRAFT_GRID_HEIGHT;
    }

    @TestOnly
    public boolean validateSlotStatus() throws IllegalStateException{
        for(Slot slot : getCraftingSlots()){
            if(!(slot instanceof CraftingGridSlot)) throw new IllegalStateException("getCraftingSlots do not correspond menu's crafter slots.");
        }
        for(Slot slot : getPlayerInvSlots()){
            if(!(slot.container instanceof Inventory)) throw new IllegalStateException("getPlayerInvSlots contains slots whose container is not inventory");
        }
        if(!(slots.get(RESULT_SLOT_INDEX) instanceof CraftingResultSlot)) throw new IllegalStateException("the first slot is not the crafting result slot, check ADD SLOT process");
        return true;
    }

    public boolean isCrafterEnabled(){
        return true;
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        if (activeStation.recipeBookType != null) return activeStation.recipeBookType;
        return RecipeBookType.CRAFTING;
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedItemContents stackedContents) {
        if (activeStation.isCooking()) {
            SimpleContainer cont = cookingContainers.get(activeStation);
            stackedContents.accountStack(cont.getItem(0), 1);
            stackedContents.accountStack(cont.getItem(1), 1);
        } else {
            craftMatrix.fillStackedContents(stackedContents);
        }
    }

    @Override
    public PostPlaceAction handlePlacement(boolean useMaxItems, boolean allowDroppingItemsToClear,
                                           RecipeHolder<?> recipe, ServerLevel level, Inventory inventory) {
        if (recipe.value() instanceof CraftingRecipe) {
            return handleCraftingPlacement(recipe, level, inventory, useMaxItems, allowDroppingItemsToClear);
        } else if (recipe.value() instanceof AbstractCookingRecipe) {
            return handleFurnacePlacement(recipe, level, inventory, useMaxItems, allowDroppingItemsToClear);
        }
        return PostPlaceAction.NOTHING;
    }

    @SuppressWarnings("unchecked")
    private PostPlaceAction handleCraftingPlacement(RecipeHolder<?> recipe, ServerLevel level, Inventory inventory,
                                                    boolean useMaxItems, boolean allowDroppingItemsToClear) {
        RecipeHolder<CraftingRecipe> typedRecipe = (RecipeHolder<CraftingRecipe>) recipe;
        List<Slot> inputSlots = getCraftingSlots();
        return ServerPlaceRecipe.placeRecipe(new ServerPlaceRecipe.CraftingMenuAccess<CraftingRecipe>() {
            @Override
            public void fillCraftSlotsStackedContents(StackedItemContents contents) {
                EndlessInventoryMenu.this.fillCraftSlotsStackedContents(contents);
            }
            @Override
            public void clearCraftingContent() {
                craftResult.clearContent();
                craftMatrix.clearContent();
            }
            @Override
            public boolean recipeMatches(RecipeHolder<CraftingRecipe> r) {
                return r.value().matches(craftMatrix.asCraftInput(), level);
            }
        }, 3, 3, inputSlots, inputSlots, inventory, typedRecipe, useMaxItems, allowDroppingItemsToClear);
    }

    @SuppressWarnings("unchecked")
    private PostPlaceAction handleFurnacePlacement(RecipeHolder<?> recipe, ServerLevel level, Inventory inventory,
                                                   boolean useMaxItems, boolean allowDroppingItemsToClear) {
        RecipeHolder<AbstractCookingRecipe> typedRecipe = (RecipeHolder<AbstractCookingRecipe>) recipe;
        SimpleContainer container = cookingContainers.get(activeStation);
        Slot inputSlot = getCookingInputSlot(activeStation);
        Slot resultSlot = getCookingResultSlot(activeStation);
        List<Slot> slotsToClear = List.of(inputSlot, resultSlot);
        return ServerPlaceRecipe.placeRecipe(new ServerPlaceRecipe.CraftingMenuAccess<AbstractCookingRecipe>() {
            @Override
            public void fillCraftSlotsStackedContents(StackedItemContents contents) {
                contents.accountStack(container.getItem(0), 1);
                contents.accountStack(container.getItem(1), 1);
            }
            @Override
            public void clearCraftingContent() {
                slotsToClear.forEach(s -> s.set(ItemStack.EMPTY));
            }
            @Override
            public boolean recipeMatches(RecipeHolder<AbstractCookingRecipe> r) {
                return r.value().matches(new SingleRecipeInput(container.getItem(0)), level);
            }
        }, 1, 1, List.of(inputSlot), slotsToClear, inventory, typedRecipe, useMaxItems, allowDroppingItemsToClear);
    }

    // ── Active station ───────────────────────────────────────────────────────

    public void setActiveStation(Station newStation) {
        if (this.activeStation == newStation) return;
        Station oldStation = this.activeStation;
        this.activeStation = newStation;

        if (oldStation == Station.CRAFTING) {
            returnCraftingToPlayer();
        } else if (oldStation.isInstantStation()) {
            returnInstantStationToPlayer(oldStation);
        }

        int stationRows = (newStation != Station.NONE) ? CRAFTING_ROWS : 0;
        this.visibleRows = stationRows > 0 ? Math.max(1, baseRows - stationRows) : baseRows;
        rowsData.set(this.visibleRows);
    }

    public Station getActiveStation() {
        return activeStation;
    }

    public boolean isCraftingVisible() {
        return activeStation == Station.CRAFTING;
    }

    public boolean isFurnaceVisible() {
        return activeStation == Station.FURNACE;
    }

    /** Returns the active station if it is a cooking station, null otherwise. */
    @Nullable
    public Station getActiveCookingStation() {
        return activeStation.isCooking() ? activeStation : null;
    }

    // ── Cooking tick (called every server tick via broadcastChanges) ──────────

    @Override
    public void broadcastChanges() {
        tickCookingStations();
        refreshStationUnlocks();
        enchanting.tick();
        super.broadcastChanges();
    }

    // ── Station unlocks (FreeCraftingStations = false) ───────────────────────

    /** Server side: re-read the unlocked stations; a station that became locked (config change) closes. */
    private void refreshStationUnlocks() {
        if (!(player instanceof ServerPlayer)) return;
        stationUnlockMask.set(StationUnlocks.mask(sourceInventory));
        if (!isStationUnlocked(activeStation)) setActiveStation(Station.NONE);
    }

    /** Client side: the synced mask of unlocked stations ({@link StationUnlocks}). */
    public int getStationUnlockMask() {
        return stationUnlockMask.get();
    }

    private boolean moveStackIntoEnchanting(ItemStack stack) {
        int idx = EnchantingStation.isLapis(stack) ? ENCHANT_LAPIS : ENCHANT_ITEM;
        int before = stack.getCount();
        this.moveItemStackTo(stack, idx, idx + 1, false);
        return stack.getCount() < before;
    }

    /** The enchanting station (slots, costs and clues synced to the client, bookshelf count). */
    public EnchantingStation getEnchanting() {
        return enchanting;
    }

    public boolean isStationUnlocked(Station st) {
        if (player instanceof ServerPlayer) return StationUnlocks.isUnlocked(StationUnlocks.mask(sourceInventory), st);
        return StationUnlocks.isUnlocked(stationUnlockMask.get(), st);
    }

    private void tickCookingStations() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel level = serverPlayer.level();
        for (Station st : COOKING_STATIONS) {
            tickStation(st, level);
        }
        tickBrewingStationMenu(level);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void tickStation(Station st, ServerLevel level) {
        SimpleContainer container = cookingContainers.get(st);
        DataSlot[] ds = cookingDataSlots.get(st);
        Map<ResourceKey<Recipe<?>>, Integer> recipesUsed = cookingRecipesUsed.get(st);

        ItemStack fuel = container.getItem(1);
        ItemStack ingredient = container.getItem(0);
        boolean hasIngredient = !ingredient.isEmpty();
        boolean hasFuel = !fuel.isEmpty();

        int litTime = ds[0].get();
        boolean wasLit = litTime > 0;
        int litDuration = ds[1].get();
        int cookTime = ds[2].get();
        int cookDuration = ds[3].get();
        float speed = cookingSpeeds.getOrDefault(st, 1.0F);

        boolean isLit;
        if (litTime > 0) {
            litTime--;
            isLit = litTime > 0;
        } else {
            isLit = false;
        }

        if (isLit || (hasFuel && hasIngredient)) {
            if (hasIngredient) {
                Optional<RecipeHolder<AbstractCookingRecipe>> optRecipe =
                    (Optional<RecipeHolder<AbstractCookingRecipe>>) (Optional<?>)
                        level.getServer().getRecipeManager()
                             .getRecipeFor(st.cookingRecipeType, new SingleRecipeInput(ingredient), level);
                if (optRecipe.isPresent()) {
                    AbstractCookingRecipe recipeVal = optRecipe.get().value();
                    ItemStack burnResult = recipeVal.assemble(new SingleRecipeInput(ingredient));
                    if (!burnResult.isEmpty() && canCookingBurn(container, burnResult)) {
                        if (!isLit && StationProcessing.isCookingFuel(fuel)) {
                            // AbstractFurnaceBlockEntity.serverTick: data-driven burn time and speed
                            LootContext ctx = StationProcessing.lootContext(level, st, player.blockPosition());
                            int newLitTime = StationProcessing.burnDuration(ctx, fuel);
                            litTime = newLitTime;
                            litDuration = newLitTime;
                            speed = StationProcessing.cookingSpeed(ctx, fuel);
                            cookingSpeeds.put(st, speed);
                            if (cookDuration > 0 && cookTime < cookDuration) {
                                float progress = (float) cookTime / cookDuration;
                                cookDuration = StationProcessing.totalCookTime(recipeVal, speed);
                                cookTime = (int) Math.ceil(progress * cookDuration);
                            }
                            if (newLitTime > 0) {
                                consumeCookingFuel(container);
                                isLit = true;
                            }
                        }
                        if (isLit) {
                            cookTime++;
                            if (cookDuration == 0) cookDuration = StationProcessing.totalCookTime(recipeVal, speed);
                            if (cookTime >= cookDuration) {
                                cookTime = 0;
                                cookDuration = StationProcessing.totalCookTime(recipeVal, speed);
                                completeCookingBurn(container, ingredient, burnResult, st);
                                recipesUsed.merge(optRecipe.get().id(), 1, Integer::sum);
                                if (container.getItem(0).isEmpty() && player instanceof ServerPlayer sp) {
                                    StationNotifications.notify(sp, st, com.emma.endinv.api.StationEvent.Reason.DONE, container.getItem(2));
                                }
                            }
                        } else {
                            cookTime = 0;
                        }
                    } else {
                        cookTime = 0;
                    }
                }
            } else {
                cookTime = 0;
            }
        } else if (cookTime > 0) {
            cookTime = Mth.clamp(cookTime - 2, 0, cookDuration);
        }

        if (wasLit && !isLit && !container.getItem(0).isEmpty() && container.getItem(1).isEmpty()
                && player instanceof ServerPlayer sp) {
            StationNotifications.notify(sp, st, com.emma.endinv.api.StationEvent.Reason.OUT_OF_FUEL, ItemStack.EMPTY);
        }
        ds[0].set(litTime);
        ds[1].set(litDuration);
        ds[2].set(cookTime);
        ds[3].set(cookDuration);
    }

    private boolean canCookingBurn(SimpleContainer container, ItemStack burnResult) {
        ItemStack current = container.getItem(2);
        if (current.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(current, burnResult)) return false;
        int merged = current.getCount() + burnResult.getCount();
        return merged <= Math.min(64, current.getMaxStackSize());
    }

    private void consumeCookingFuel(SimpleContainer container) {
        ItemStack fuel = container.getItem(1);
        Item fuelItem = fuel.getItem();
        fuel.shrink(1);
        if (fuel.isEmpty()) {
            ItemStackTemplate remainder = fuelItem.getCraftingRemainder();
            container.setItem(1, remainder != null ? remainder.create() : ItemStack.EMPTY);
        }
    }

    private void completeCookingBurn(SimpleContainer container, ItemStack ingredient, ItemStack burnResult, Station st) {
        ItemStack current = container.getItem(2);
        if (current.isEmpty()) {
            container.setItem(2, burnResult.copy());
        } else {
            current.grow(burnResult.getCount());
        }
        if (st == Station.FURNACE
                && ingredient.is(net.minecraft.world.item.Items.WET_SPONGE)
                && !container.getItem(1).isEmpty()
                && container.getItem(1).is(net.minecraft.world.item.Items.BUCKET)) {
            container.setItem(1, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
        }
        ingredient.shrink(1);
    }

    public void awardCookingXP(Station st, Player player) {
        Map<ResourceKey<Recipe<?>>, Integer> recipesUsed = cookingRecipesUsed.get(st);
        if (recipesUsed == null || recipesUsed.isEmpty()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel level = serverPlayer.level();
        List<RecipeHolder<?>> toAward = new ArrayList<>();
        for (var entry : recipesUsed.entrySet()) {
            level.recipeAccess().byKey(entry.getKey()).ifPresent(recipe -> {
                toAward.add(recipe);
                spawnCookingExperience(level, serverPlayer.position(), entry.getValue(),
                    ((AbstractCookingRecipe) recipe.value()).experience());
            });
        }
        serverPlayer.awardRecipes(toAward);
        for (var recipe : toAward) serverPlayer.triggerRecipeCrafted(recipe, List.of());
        recipesUsed.clear();
    }

    private static void spawnCookingExperience(ServerLevel level, Vec3 pos, int count, float expPerCraft) {
        int xp = Mth.floor(count * expPerCraft);
        float frac = Mth.frac(count * expPerCraft);
        if (frac != 0 && level.getRandom().nextFloat() < frac) xp++;
        ExperienceOrb.award(level, pos, xp);
    }

    public boolean isFurnaceLit() {
        if (!activeStation.isCooking()) return false;
        return cookingDataSlots.get(activeStation)[0].get() > 0;
    }

    public float getLitProgress() {
        if (!activeStation.isCooking()) return 0f;
        DataSlot[] ds = cookingDataSlots.get(activeStation);
        int duration = ds[1].get();
        return duration == 0 ? 0f : ds[0].get() / (float) duration;
    }

    /** Whether {@code st}'s fire is burning, open or not (each cooking station's data is synced). */
    public boolean isCookingLit(Station st) {
        DataSlot[] ds = cookingDataSlots.get(st);
        return ds != null && ds[0].get() > 0;
    }

    /** How far {@code st}'s current item is cooked, 0..1. */
    public float getCookProgress(Station st) {
        DataSlot[] ds = cookingDataSlots.get(st);
        if (ds == null || ds[3].get() == 0) return 0f;
        return ds[2].get() / (float) ds[3].get();
    }

    public float getBurnProgress() {
        if (!activeStation.isCooking()) return 0f;
        DataSlot[] ds = cookingDataSlots.get(activeStation);
        int total = ds[3].get();
        return total == 0 ? 0f : ds[2].get() / (float) total;
    }

    // ── Crafting result update ───────────────────────────────────────────────

    public void setCraftingVisible(boolean visible) {
        setActiveStation(visible ? Station.CRAFTING : Station.NONE);
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == this.craftMatrix) {
            this.updateCraftingResult();
        }
        for (Station st : COOKING_STATIONS) {
            if (container == cookingContainers.get(st)) {
                updateCookingTime(st);
                break;
            }
        }
        if (container == stonecutterInput)    updateStonecutterResult();
        if (container == grindstoneRepairSlots) computeGrindstoneResult();
        if (container == smithingInput)         updateSmithingResult();
        super.slotsChanged(container);
    }

    private void updateCookingTime(Station st) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel level = serverPlayer.level();
        ItemStack input = cookingContainers.get(st).getItem(0);
        DataSlot[] ds = cookingDataSlots.get(st);
        if (!input.isEmpty()) {
            var optRecipe = level.getServer().getRecipeManager()
                .getRecipeFor(st.cookingRecipeType, new SingleRecipeInput(input), level);
            ds[3].set(optRecipe.map(r -> r.value().cookingTime()).orElse(200));
        } else {
            ds[3].set(0);
        }
        ds[2].set(0);
    }

    private void returnCraftingToPlayer() {
        if (player.level().isClientSide()) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < craftMatrix.getContainerSize(); ++i) {
            ItemStack stack = craftMatrix.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
            }
        }
        // Clear the result slot without returning it to the player to avoid duplication on close
        craftResult.removeItemNoUpdate(RESULT_SLOT_INDEX);
        craftMatrix.setChanged();
        craftResult.setChanged();
    }

    // ── Stonecutter ──────────────────────────────────────────────────────────

    private void updateStonecutterResult() {
        ItemStack input = stonecutterInput.getItem(0);
        if (ItemStack.isSameItemSameComponents(input, stonecutterLastInput)) return;
        stonecutterLastInput = input.copy();
        if (input.isEmpty()) {
            stonecutterRecipes = List.of();
        } else {
            stonecutterRecipes = player.level().recipeAccess().stonecutterRecipes().selectByInput(input).entries();
        }
        if (player instanceof ServerPlayer) {
            if (input.isEmpty() || stonecutterRecipes.isEmpty()) {
                stonecutterSelectedRecipe.set(-1);
                stonecutterResult.setItem(0, ItemStack.EMPTY);
            } else {
                stonecutterSelectedRecipe.set(0);
                setupStonecutterResult(0);
            }
        }
    }

    private void setupStonecutterResult(int index) {
        if (index < 0 || index >= stonecutterRecipes.size()) {
            stonecutterResult.setItem(0, ItemStack.EMPTY);
            return;
        }
        stonecutterRecipes.get(index).recipe().recipe().ifPresent(holder -> {
            ItemStack result = holder.value().assemble(new SingleRecipeInput(stonecutterInput.getItem(0)));
            stonecutterResult.setItem(0, result);
        });
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == EnchantingStation.BOOKSHELF_INSERT_BUTTON || id == EnchantingStation.BOOKSHELF_TAKE_BUTTON) {
            return isStationUnlocked(Station.ENCHANTING) && enchanting.clickButton(this, player, id, false);
        }
        if (activeStation == Station.ENCHANTING && id >= 0 && id < 3) {
            return enchanting.clickButton(this, player, id, true);
        }
        Station toUnlock = StationUnlocks.stationForButton(id);
        if (toUnlock != null) {
            if (!StationUnlocks.tryUnlock(this, player, sourceInventory, toUnlock)) return false;
            stationUnlockMask.set(StationUnlocks.mask(sourceInventory));
            return true;
        }
        if (activeStation == Station.STONECUTTER && id >= 0 && id < stonecutterRecipes.size()) {
            stonecutterSelectedRecipe.set(id);
            setupStonecutterResult(id);
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    public List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> getStonecutterRecipes() { return stonecutterRecipes; }
    public int getSelectedStonecutterRecipe() { return stonecutterSelectedRecipe.get(); }
    public boolean hasStonecutterInput() { return !stonecutterInput.getItem(0).isEmpty(); }

    // ── Grindstone ───────────────────────────────────────────────────────────

    private void computeGrindstoneResult() {
        if (!(player instanceof ServerPlayer)) return;
        ItemStack top    = grindstoneRepairSlots.getItem(0);
        ItemStack bottom = grindstoneRepairSlots.getItem(1);
        grindstoneXpReward = 0;

        if (top.isEmpty() && bottom.isEmpty()) {
            grindstoneResult.setItem(0, ItemStack.EMPTY);
            return;
        }
        if (!top.isEmpty() && !bottom.isEmpty()) {
            ItemStack merged = mergeGrindstoneItems(top, bottom);
            if (merged != null) {
                grindstoneResult.setItem(0, merged);
                grindstoneXpReward = getGrindstoneEnchantValue(top) + getGrindstoneEnchantValue(bottom);
                return;
            }
            grindstoneResult.setItem(0, ItemStack.EMPTY);
            return;
        }
        ItemStack single = top.isEmpty() ? bottom : top;
        grindstoneResult.setItem(0, removeNonCurses(single));
        grindstoneXpReward = getGrindstoneEnchantValue(single);
    }

    private @Nullable ItemStack mergeGrindstoneItems(ItemStack top, ItemStack bottom) {
        if (top.getItem() != bottom.getItem() || !top.has(net.minecraft.core.component.DataComponents.MAX_DAMAGE)) return null;
        int topHealth = top.getMaxDamage() - top.getDamageValue();
        int botHealth = bottom.getMaxDamage() - bottom.getDamageValue();
        int merged    = topHealth + botHealth + top.getMaxDamage() / 20;
        ItemStack result = removeNonCurses(top);
        result.setDamageValue(Math.max(0, top.getMaxDamage() - merged));
        return result;
    }

    private ItemStack removeNonCurses(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        copy.remove(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        var enchants = stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (var e : enchants.entrySet()) {
            if (e.getKey().is(net.minecraft.tags.EnchantmentTags.CURSE)) copy.enchant(e.getKey(), e.getValue());
        }
        var stored = stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        if (!stored.isEmpty()) {
            var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            for (var e : stored.entrySet()) {
                if (e.getKey().is(net.minecraft.tags.EnchantmentTags.CURSE)) mutable.upgrade(e.getKey(), e.getValue());
            }
            net.minecraft.world.item.enchantment.ItemEnchantments result = mutable.toImmutable();
            if (!result.isEmpty()) copy.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, result);
        }
        return copy;
    }

    private int getGrindstoneEnchantValue(ItemStack stack) {
        int total = 0;
        for (var e : stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).entrySet())
            if (!e.getKey().is(net.minecraft.tags.EnchantmentTags.CURSE)) total += e.getValue();
        for (var e : stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).entrySet())
            if (!e.getKey().is(net.minecraft.tags.EnchantmentTags.CURSE)) total += e.getValue();
        return total;
    }

    // ── Smithing ─────────────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void updateSmithingResult() {
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack template = smithingInput.getItem(0);
        ItemStack base     = smithingInput.getItem(1);
        ItemStack addition = smithingInput.getItem(2);
        if (base.isEmpty()) { smithingResult.setItem(0, ItemStack.EMPTY); return; }
        SmithingRecipeInput input = new SmithingRecipeInput(template, base, addition);
        var opt = (Optional<RecipeHolder<SmithingRecipe>>) (Optional<?>)
                sp.level().getServer().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, sp.level());
        smithingResult.setItem(0, opt.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY));
    }

    // ── Brewing state ────────────────────────────────────────────────────────

    private void loadBrewingState() {
        if (!(sourceInventory instanceof EndlessInventory endInv)) return;
        BrewingState state = endInv.getBrewingState();
        brewingContainer.setItem(0, state.potion0().copy());
        brewingContainer.setItem(1, state.potion1().copy());
        brewingContainer.setItem(2, state.potion2().copy());
        brewingContainer.setItem(3, state.ingredient().copy());
        brewingContainer.setItem(4, state.fuel().copy());
        brewingTimeSlot.set(state.brewTime());
        brewingFuelSlot.set(state.fuelAmount());
        brewingSpeed = state.speedMultiplier();
    }

    private void saveBrewingState() {
        if (!(sourceInventory instanceof EndlessInventory endInv)) return;
        endInv.setBrewingState(new BrewingState(
                brewingContainer.getItem(3).copy(), brewingContainer.getItem(4).copy(),
                brewingContainer.getItem(0).copy(), brewingContainer.getItem(1).copy(),
                brewingContainer.getItem(2).copy(),
                brewingTimeSlot.get(), brewingFuelSlot.get(), brewingSpeed));
        endInv.setChanged();
    }

    private void tickBrewingStationMenu(ServerLevel level) {
        int fuel    = brewingFuelSlot.get();
        int brewTime = brewingTimeSlot.get();
        ItemStack fuelStack = brewingContainer.getItem(4);

        BrewingFuel brewingFuel = fuelStack.get(DataComponents.BREWING_FUEL);
        if (fuel <= 0 && brewingFuel != null) {
            LootContext ctx = StationProcessing.lootContext(level, Station.BREWING, player.blockPosition());
            fuel = StationProcessing.brewingFuelUses(ctx, brewingFuel);
            brewingSpeed = StationProcessing.brewingSpeed(ctx, brewingFuel);
            Item fuelItem = fuelStack.getItem();
            fuelStack.shrink(1);
            ItemStackTemplate remainder = fuelItem.getCraftingRemainder();
            if (remainder != null) {
                if (fuelStack.isEmpty()) brewingContainer.setItem(4, remainder.create());
                else player.getInventory().placeItemBackInInventory(remainder.create(), Prediction.SERVER_ONLY);
            }
        }

        boolean brewable = isBrewingBrewableMenu(level);

        if (brewTime > 0) {
            brewTime--;
            if (brewTime == 0 && brewable) {
                doBrewMenu(level);
            } else if (!brewable) {
                brewTime = 0;
            }
        } else if (fuel > 0 && brewable) {
            fuel--;
            brewTime = StationProcessing.brewTime(brewingSpeed);
        }

        brewingFuelSlot.set(fuel);
        brewingTimeSlot.set(brewTime);
    }

    private boolean isBrewingBrewableMenu(ServerLevel level) {
        return StationProcessing.isBrewable(level, brewingContainer.getItem(3),
                brewingContainer.getItem(0), brewingContainer.getItem(1), brewingContainer.getItem(2));
    }

    private void doBrewMenu(ServerLevel level) {
        ItemStack ingredient = brewingContainer.getItem(3);
        if (!ingredient.isEmpty()) {
            for (int i = 0; i < 3; i++) {
                ItemStack p = brewingContainer.getItem(i);
                if (!p.isEmpty()) brewingContainer.setItem(i, StationProcessing.brew(level, p, ingredient));
            }
            Item ingredientItem = ingredient.getItem();
            ingredient.shrink(1);
            ItemStackTemplate remainder = ingredientItem.getCraftingRemainder();
            if (remainder != null) {
                if (ingredient.isEmpty()) ingredient = remainder.create();
                else player.getInventory().placeItemBackInInventory(remainder.create(), Prediction.SERVER_ONLY);
            }
            brewingContainer.setItem(3, ingredient);
            if (player instanceof ServerPlayer sp) {
                ItemStack brewed = ItemStack.EMPTY;
                for (int i = 0; i < 3 && brewed.isEmpty(); i++) brewed = brewingContainer.getItem(i);
                StationNotifications.notify(sp, Station.BREWING, com.emma.endinv.api.StationEvent.Reason.DONE, brewed);
            }
        }
    }

    public boolean isBrewingActive() { return activeStation == Station.BREWING; }
    public float getBrewingProgress() { int t = brewingTimeSlot.get(); return t <= 0 ? 0f : t / (float) StationProcessing.BREW_TIME; }
    public float getBrewingFuelProgress() { int f = brewingFuelSlot.get(); return f <= 0 ? 0f : f / (float) StationProcessing.DEFAULT_FUEL_USES; }

    // ── Instant station return ────────────────────────────────────────────────

    private void returnInstantStationToPlayer(Station st) {
        if (player.level().isClientSide()) return;
        Inventory inv = player.getInventory();
        switch (st) {
            case STONECUTTER -> {
                returnContainerToPlayer(inv, stonecutterInput);
                stonecutterResult.clearContent();
                stonecutterLastInput = ItemStack.EMPTY;
            }
            case GRINDSTONE -> {
                returnContainerToPlayer(inv, grindstoneRepairSlots);
                grindstoneResult.clearContent();
                grindstoneXpReward = 0;
            }
            case ENCHANTING -> enchanting.returnToPlayer();
            case SMITHING -> {
                returnContainerToPlayer(inv, smithingInput);
                smithingResult.clearContent();
            }
        }
    }

    private void returnContainerToPlayer(Inventory inv, SimpleContainer container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.removeItemNoUpdate(i);
            if (!s.isEmpty()) inv.placeItemBackInInventory(s, Prediction.SERVER_ONLY);
        }
        container.setChanged();
    }

    private void loadCookingStates() {
        if (!(sourceInventory instanceof EndlessInventory endInv)) return;
        for (Station st : COOKING_STATIONS) {
            FurnaceState state = endInv.getCookingState(st);
            if (state.isEmpty()) continue;
            SimpleContainer cont = cookingContainers.get(st);
            cont.setItem(0, state.input().copy());
            cont.setItem(1, state.fuel().copy());
            cont.setItem(2, state.result().copy());
            DataSlot[] ds = cookingDataSlots.get(st);
            ds[0].set(state.litTime());
            ds[1].set(state.litDuration());
            ds[2].set(state.cookTime());
            ds[3].set(state.cookDuration());
            cookingSpeeds.put(st, state.speedMultiplier());
        }
    }

    private void saveCookingStates() {
        if (!(sourceInventory instanceof EndlessInventory endInv)) return;
        for (Station st : COOKING_STATIONS) {
            SimpleContainer cont = cookingContainers.get(st);
            DataSlot[] ds = cookingDataSlots.get(st);
            FurnaceState state = new FurnaceState(
                    cont.getItem(0).copy(),
                    cont.getItem(1).copy(),
                    cont.getItem(2).copy(),
                    ds[0].get(), ds[1].get(), ds[2].get(), ds[3].get(),
                    cookingSpeeds.getOrDefault(st, 1.0F));
            endInv.setCookingState(st, state);
        }
        endInv.setChanged();
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    public int getBaseRows() {
        return baseRows;
    }

    private void addStandardInventorySlots(Inventory playerInventory, int x, int y){
        for (int l = 0; l < 3; l++) {
            for (int j1 = 0; j1 < 9; j1++) {
                this.addSlot(new Slot(playerInventory, j1 + l * 9 + 9, x + j1 * 18, y + l * 18 ));
            }
        }

        for (int i1 = 0; i1 < 9; i1++) {
            this.addSlot(new Slot(playerInventory, i1, x + i1 * 18, y+58));
        }
    }

    //supposed to be the only method to change displaying page and index value; to sync.
    public void switchPageWithIndex(int index){
        if(index < 0 || index >= PageTypeRegistry.size()) {
            return;
        }
        PageType type = PageTypeRegistry.byIndex(index);
        if (type != null) {
            applySelectedPage(type);
        }
    }

    private void applySelectedPage(PageType type){
        this.displayingPageType = type;
        this.displayingPageId = type.registerName;
        this.displayingPageIndex = Math.max(0, PageTypeRegistry.getIndexOf(this.displayingPageId));
    }

    public int getItemSize(){
        return itemSize.get();
    }

    public void setItemSize(int i){
        this.itemSize.set(i);
    }

    public boolean enableInfinity(){
        return infinityMode.get() > 0;
    }

    public int getMaxStackSize(){
        return maxStackSize.get();
    }

    @Override
    public AbstractContainerMenu getMenu() {
        return this;
    }

    public SourceInventory getSourceInventory(){
        return this.sourceInventory;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public int rows(){
        return this.visibleRows;
    }

    @Override
    public int columns() {
        return 9;
    }

    /**Override {@link AbstractContainerMenu#clicked(int, int, ContainerInput, Player)}
     * Invoked when Client click in container screen/Server handle click packet.
     * for details see below.
     * @param slotId index
     * @param button ...0: left 1: right 2: middle ? Is there anyone who can explain?
     * @param clickType {@link ContainerInput}
     * @param player player performing menu click
     */
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        try {
            if(clickType==ContainerInput.QUICK_CRAFT){
                MenuClickHandler.handleQuickCraft(this,slotId,button,player);
            }else if(this.quickcraftStatus!=0){
                this.resetQuickCraft();
            }
            if (clickType == ContainerInput.PICKUP) {
                MenuClickHandler.handlePickup(this, slotId, button, player);
            } else if (clickType == ContainerInput.QUICK_MOVE) {
                MenuClickHandler.handleQuickMove(this, slotId, button, player);
            } else if (clickType == ContainerInput.SWAP) {
                MenuClickHandler.handleSwap(this, slotId, button, player);
            } else if (clickType == ContainerInput.THROW) {
                MenuClickHandler.handleThrow(this, slotId, button, player);
            } else if (clickType == ContainerInput.CLONE) {
                MenuClickHandler.handleClone(this, slotId, button, player);
            } else if (clickType == ContainerInput.PICKUP_ALL) {
                this.handlePickupAll(slotId, button, player);
            } else {
                return;
            }
            if(this.getSourceInventory() instanceof EndlessInventory endinv){
                this.setItemSize(endinv.getItemSize());
            }
        } catch (Exception exception) {
            CrashReport crashreport = CrashReport.forThrowable(exception, "Container click");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Click info");
            crashreportcategory.setDetail("Menu Type", "emma_endinv");
            crashreportcategory.setDetail("Menu Class", () -> this.getClass().getCanonicalName());
            crashreportcategory.setDetail("Slot Count", this.slots.size());
            crashreportcategory.setDetail("Slot", slotId);
            crashreportcategory.setDetail("Button", button);
            crashreportcategory.setDetail("Type", clickType);
            throw new ReportedException(crashreport);
        }
    }

    boolean tryItemClickBehaviourOverride(Player player, ClickAction action, Slot slot, ItemStack clickedItem, ItemStack carriedItem) {
        // Neo: Fire the ItemStackedOnOtherEvent, and return true if it was cancelled (meaning the event was handled). Returning true will trigger the container to stop processing further logic.
        if (ModInfo.platformContext.onItemStackedOn(clickedItem, carriedItem, slot, action, player, createCarriedSlotAccess())) {
            return true;
        }

        FeatureFlagSet featureflagset = player.level().enabledFeatures();
        //item combining in menu, bundle, etc
        return carriedItem.isItemEnabled(featureflagset) && carriedItem.overrideStackedOnOther(slot, action, player)
                || clickedItem.isItemEnabled(featureflagset)
                    && clickedItem.overrideOtherStackedOnMe(carriedItem, slot, action, player, this.createCarriedSlotAccess());
    }

    private SlotAccess createCarriedSlotAccess() {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return EndlessInventoryMenu.this.getCarried();
            }

            @Override
            public boolean set(ItemStack itemStack) {
                EndlessInventoryMenu.this.setCarried(itemStack);
                return true;
            }
        };
    }

    /**
     * Handles vanilla quick-move (shift-click) transfers, prioritising crafter/furnace slots before the player inventory
     * and falling back to the endless inventory page when needed.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getItem();
        if (slotStack.getItem() == Items.getTestEndInv()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slotStack.copy();
        boolean handled;

        if (index == RESULT_SLOT_INDEX) {
            handled = handleQuickMoveResult(slot, slotStack, original);
        } else if (isCookingResultSlot(index)) {
            handled = handleQuickMoveCookingResult(slot, slotStack, original);
        } else if (index == STONECUTTER_RESULT || index == GRINDSTONE_RESULT || index == SMITHING_RESULT) {
            handled = handleQuickMoveCookingResult(slot, slotStack, original);
        } else if (isCrafterSlot(index)) {
            handled = handleQuickMoveFromCrafterSlot(slot, slotStack);
        } else if (isCookingSlot(index)) {
            handled = this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_INV_END, false);
        } else if (index >= STONECUTTER_SLOT_START && index < BREWING_SLOT_END) {
            handled = this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_INV_END, false);
        } else if (isPlayerInventorySlot(index)) {
            handled = handleQuickMoveFromPlayerInventory(slotStack);
        } else {
            handled = this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_INV_END, false);
        }

        if (!handled) {
            return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (slotStack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, slotStack);
        if (index == RESULT_SLOT_INDEX) {
            player.drop(slotStack, false, Prediction.PREDICTED);
        }

        return original;

    }

    private boolean isPlayerInventorySlot(int index) {
        return index >= PLAYER_INV_START && index < PLAYER_INV_END;
    }

    private boolean isCrafterSlot(int index) {
        return index >= CRAFT_SLOT_START && index < CRAFT_SLOT_END;
    }

    private boolean isCookingSlot(int index) {
        return index >= COOKING_SLOT_START && index < COOKING_SLOT_END;
    }

    private boolean isCookingResultSlot(int index) {
        if (!isCookingSlot(index)) return false;
        return (index - COOKING_SLOT_START) % 3 == 0;
    }

    private boolean handleQuickMoveFromCrafterSlot(Slot slot, ItemStack stack) {
        boolean moved = this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false);
        if (moved) {
            craftMatrix.setChanged();
            updateCraftingResult();
        }
        return moved;
    }

    private boolean moveStackIntoCrafter(ItemStack stack) {
        int before = stack.getCount();
        boolean moved = this.moveItemStackTo(stack, CRAFT_SLOT_START, CRAFT_SLOT_END, false);
        if (!moved || stack.getCount() == before) {
            return false;
        }
        // Prevent ghost copies on the client by only flagging a move when the stack actually shrank.
        craftMatrix.setChanged();
        updateCraftingResult();
        return true;
    }

    private boolean moveStackIntoCookingInput(Station st, ItemStack stack) {
        int idx = cookingInputIdx(st);
        int before = stack.getCount();
        this.moveItemStackTo(stack, idx, idx + 1, false);
        return stack.getCount() < before;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        if (slot.container == this.craftResult) {
            return false;
        }
        return super.canTakeItemForPickAll(stack, slot);
    }

    private ItemStack quickMoveIntoPage(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remain = this.sourceInventory.addItem(stack);
        this.sourceInventory.setChanged();
        return remain;
    }

    private void handlePickupAll(int slotId, int button, Player player) {
        if (slotId < 0) {
            return;
        }
        Slot clickedSlot = this.slots.get(slotId);
        ItemStack carried = this.getCarried();
        if (carried.isEmpty()) {
            return;
        }

        if (!clickedSlot.hasItem() || !clickedSlot.mayPickup(player)) {
            int startIndex = button == 0 ? 0 : this.slots.size() - 1;
            int step = button == 0 ? 1 : -1;

            for (int pass = 0; pass < 2; pass++) {
                for (int index = startIndex; index >= 0 && index < this.slots.size() && carried.getCount() < carried.getMaxStackSize(); index += step) {
                    Slot scanningSlot = this.slots.get(index);
                    if (AbstractContainerMenu.canItemQuickReplace(scanningSlot, carried, true)
                            && scanningSlot.mayPickup(player)
                            && this.canTakeItemForPickAll(carried, scanningSlot)) {
                        ItemStack scanningItem = scanningSlot.getItem();
                        if (pass != 0 || scanningItem.getCount() != scanningItem.getMaxStackSize()) {
                            ItemStack taken = scanningSlot.safeTake(scanningItem.getCount(), carried.getMaxStackSize() - carried.getCount(), player);
                            carried.grow(taken.getCount());
                        }
                    }
                }

                if (carried.getCount() < carried.getMaxStackSize()) {
                    ItemStack extracted = tryExtractFromPage(carried, carried.getMaxStackSize() - carried.getCount());
                    carried.grow(extracted.getCount());
                    carried.setCount(Math.min(carried.getCount(), carried.getMaxStackSize()));
                }
            }
        }
    }

    public ItemStack tryExtractFromPage(ItemStack template, int count) {
        if (count <= 0 || template.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack request = template.copy();
        return this.sourceInventory.takeItem(request, count);
    }

    /**
     * Send stacks from the current page into the active station (when visible) and then the player inventory.
     */
    public ItemStack quickMoveFromPage(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }

        boolean stationAccepted = false;
        if (activeStation == Station.CRAFTING) {
            stationAccepted = moveStackIntoCrafter(stack);
        } else if (activeStation.isCooking()) {
            stationAccepted = moveStackIntoCookingInput(activeStation, stack);
            if (!stationAccepted && StationProcessing.isCookingFuel(stack)) {
                int fuelIdx = cookingFuelIdx(activeStation);
                stationAccepted = this.moveItemStackTo(stack, fuelIdx, fuelIdx + 1, false);
            }
        } else if (activeStation == Station.STONECUTTER) {
            stationAccepted = this.moveItemStackTo(stack, STONECUTTER_INPUT, STONECUTTER_INPUT + 1, false);
        } else if (activeStation == Station.GRINDSTONE) {
            stationAccepted = this.moveItemStackTo(stack, GRINDSTONE_SLOT_START, GRINDSTONE_RESULT, false);
        } else if (activeStation == Station.SMITHING && player instanceof ServerPlayer sp) {
            var ra = sp.level().recipeAccess();
            if (!stationAccepted && ra.propertySet(RecipePropertySet.SMITHING_TEMPLATE).test(stack))
                stationAccepted = this.moveItemStackTo(stack, SMITHING_TEMPLATE, SMITHING_TEMPLATE + 1, false);
            if (!stationAccepted && ra.propertySet(RecipePropertySet.SMITHING_BASE).test(stack))
                stationAccepted = this.moveItemStackTo(stack, SMITHING_BASE, SMITHING_BASE + 1, false);
            if (!stationAccepted && ra.propertySet(RecipePropertySet.SMITHING_ADDITION).test(stack))
                stationAccepted = this.moveItemStackTo(stack, SMITHING_ADDITION, SMITHING_ADDITION + 1, false);
        } else if (activeStation == Station.ENCHANTING) {
            stationAccepted = moveStackIntoEnchanting(stack);
        } else if (activeStation == Station.BREWING) {
            if (!stationAccepted && StationProcessing.isBrewingFuel(stack))
                stationAccepted = this.moveItemStackTo(stack, BREWING_FUEL_SLOT, BREWING_FUEL_SLOT + 1, false);
            if (!stationAccepted && player instanceof ServerPlayer sp && StationProcessing.isBrewingReagent(sp.level(), stack))
                stationAccepted = this.moveItemStackTo(stack, BREWING_INGREDIENT, BREWING_INGREDIENT + 1, false);
            if (!stationAccepted)
                stationAccepted = this.moveItemStackTo(stack, BREWING_POTION0, BREWING_POTION0 + 3, false);
        }

        if (!stack.isEmpty()) {
            boolean movedToMain = this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_START + PLAYER_INV_SLOT_COUNT, false);
            if (!movedToMain) {
                this.moveItemStackTo(stack, PLAYER_INV_START + PLAYER_INV_SLOT_COUNT, PLAYER_INV_END, false);
            }
        }
        return stationAccepted && stack.isEmpty() ? ItemStack.EMPTY : stack;
    }


    @Override
    public SortType sortType() {
        return sortType;
    }

    @Override
    public void setSortType(SortType sortType) {
        this.sortType = sortType;
    }

    @Override
    public boolean isSortReversed() {
        return reverseSort;
    }

    @Override
    public void switchSortReversed() {
        reverseSort=!reverseSort;
    }

    @Override
    public void setSortReversed(boolean reversed) {
        this.reverseSort = reversed;
    }

    @Override
    public String searching() {
        return searching;
    }

    @Override
    public void setSearching(String searching) {
        this.searching = searching;
    }

    /**
     * Send operation will be accomplished in {@link AbstractContainerMenu#broadcastChanges()}
     */
    @Override
    public PageData getPageData() {
        return new PageData(getDisplayingPageId(), baseRows, columns(), sortType(), isSortReversed(), searching());
    }

    @Override
    public String getDisplayingPageId() {
        return this.displayingPageId;
    }

    @Override
    public void switchPageWithId(String id) {
        PageType type = PageTypeRegistry.byId(id);
        if (type != null) {
            applySelectedPage(type);
        }
    }

    @Override
    public PageType getDisplayingPageType() {
        return this.displayingPageType;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        returnCraftingToPlayer();
        if (activeStation.isInstantStation()) {
            returnInstantStationToPlayer(activeStation);
        }
        saveCookingStates();
        saveBrewingState();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }


    private int insertStackIntoPage(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ItemStack attempt = stack.copy();
        ItemStack remainder = quickMoveIntoPage(attempt);
        int inserted = stack.getCount() - remainder.getCount();
        if (inserted > 0) {
            stack.shrink(inserted);
        }
        return inserted;
    }

    private boolean handleQuickMoveResult(Slot slot, ItemStack stack, ItemStack original) {
        boolean moved = this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, true)
                || this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false);
        if (!moved) {
            insertStackIntoPage(stack);
        }
        slot.onQuickCraft(stack, original);
        updateCraftingResult();
        return true;
    }

    private boolean handleQuickMoveCookingResult(Slot slot, ItemStack stack, ItemStack original) {
        boolean moved = this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, true)
                || this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false);
        if (!moved) {
            insertStackIntoPage(stack);
        }
        slot.onQuickCraft(stack, original);
        return true;
    }

    private boolean handleQuickMoveFromPlayerInventory(ItemStack stack) {
        if (activeStation == Station.CRAFTING) {
            return moveStackIntoCrafter(stack);
        }
        if (activeStation.isCooking()) {
            boolean moved = moveStackIntoCookingInput(activeStation, stack);
            if (!moved && StationProcessing.isCookingFuel(stack)) {
                int fuelIdx = cookingFuelIdx(activeStation);
                moved = this.moveItemStackTo(stack, fuelIdx, fuelIdx + 1, false);
            }
            return moved;
        }
        if (activeStation == Station.STONECUTTER) {
            return this.moveItemStackTo(stack, STONECUTTER_INPUT, STONECUTTER_INPUT + 1, false);
        }
        if (activeStation == Station.GRINDSTONE) {
            return this.moveItemStackTo(stack, GRINDSTONE_SLOT_START, GRINDSTONE_RESULT, false);
        }
        if (activeStation == Station.SMITHING) {
            if (!(player instanceof ServerPlayer sp)) return false;
            var ra = sp.level().recipeAccess();
            boolean moved = false;
            if (!moved && ra.propertySet(RecipePropertySet.SMITHING_TEMPLATE).test(stack))
                moved = this.moveItemStackTo(stack, SMITHING_TEMPLATE, SMITHING_TEMPLATE + 1, false);
            if (!moved && ra.propertySet(RecipePropertySet.SMITHING_BASE).test(stack))
                moved = this.moveItemStackTo(stack, SMITHING_BASE, SMITHING_BASE + 1, false);
            if (!moved && ra.propertySet(RecipePropertySet.SMITHING_ADDITION).test(stack))
                moved = this.moveItemStackTo(stack, SMITHING_ADDITION, SMITHING_ADDITION + 1, false);
            return moved;
        }
        if (activeStation == Station.ENCHANTING) {
            return moveStackIntoEnchanting(stack);
        }
        if (activeStation == Station.BREWING) {
            boolean moved = false;
            if (!moved && StationProcessing.isBrewingFuel(stack))
                moved = this.moveItemStackTo(stack, BREWING_FUEL_SLOT, BREWING_FUEL_SLOT + 1, false);
            if (!moved && player instanceof ServerPlayer sp && StationProcessing.isBrewingReagent(sp.level(), stack))
                moved = this.moveItemStackTo(stack, BREWING_INGREDIENT, BREWING_INGREDIENT + 1, false);
            if (!moved)
                moved = this.moveItemStackTo(stack, BREWING_POTION0, BREWING_POTION0 + 3, false);
            return moved;
        }
        return insertStackIntoPage(stack) > 0;
    }

    private void updateCraftingResult() {
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        var level = serverPlayer.level();
        ItemStack resultStack = ItemStack.EMPTY;
        int w = CRAFT_GRID_WIDTH;
        int h = CRAFT_GRID_HEIGHT;
        NonNullList<ItemStack> items = NonNullList.withSize(w * h, ItemStack.EMPTY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                items.set(idx, this.craftMatrix.getItem(idx));
            }
        }
        CraftingInput input = CraftingInput.of(w, h, items);
        Optional<RecipeHolder<CraftingRecipe>> optional = ((net.minecraft.server.level.ServerLevel)level).getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (optional.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = optional.get();
            CraftingRecipe recipe = holder.value();
            if (this.craftResult.setRecipeUsed(serverPlayer, holder)) {
                ItemStack assembled = recipe.assemble(input);
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    resultStack = assembled;
                }
            }
        }
        this.craftResult.setItem(RESULT_SLOT_INDEX, resultStack);
        this.setRemoteSlot(RESULT_SLOT_INDEX, resultStack);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(this.containerId, this.incrementStateId(), RESULT_SLOT_INDEX, resultStack));
    }

    // ── Inner slot classes ────────────────────────────────────────────────────

    private class CraftingGridSlot extends Slot {
        CraftingGridSlot(CraftingContainer matrix, int slot, int x, int y) {
            super(matrix, slot, x, y);
        }

        @Override
        public boolean isActive() {
            return activeStation == Station.CRAFTING;
        }
    }

    private class CraftingResultSlot extends ResultSlot {
        CraftingResultSlot(Player player, CraftingContainer matrix, ResultContainer result, int slotIndex, int x, int y) {
            super(player, matrix, result, slotIndex, x, y);
        }

        @Override
        public boolean isActive() {
            return activeStation == Station.CRAFTING;
        }
    }

    private class EICookingInputSlot extends Slot {
        private final Station station;

        EICookingInputSlot(SimpleContainer container, int slot, int x, int y, Station station) {
            super(container, slot, x, y);
            this.station = station;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return true;
        }

        @Override
        public boolean isActive() {
            return activeStation == station;
        }
    }

    private class EICookingFuelSlot extends Slot {
        private final Station station;

        EICookingFuelSlot(SimpleContainer container, int slot, int x, int y, Station station) {
            super(container, slot, x, y);
            this.station = station;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return StationProcessing.isCookingFuel(stack) || FurnaceFuelSlot.isBucket(stack);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return FurnaceFuelSlot.isBucket(stack) ? 1 : super.getMaxStackSize(stack);
        }

        @Override
        public boolean isActive() {
            return activeStation == station;
        }
    }

    private class EICookingResultSlot extends Slot {
        private final Station station;

        EICookingResultSlot(SimpleContainer container, int slot, int x, int y, Station station) {
            super(container, slot, x, y);
            this.station = station;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            super.onTake(player, carried);
            awardCookingXP(station, player);
        }

        @Override
        public boolean isActive() {
            return activeStation == station;
        }
    }

    // ── Inner slot classes for new stations ──────────────────────────────────

    private class EIStonecutterInputSlot extends Slot {
        EIStonecutterInputSlot(SimpleContainer c, int slot, int x, int y) { super(c, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return true; }
        @Override public boolean isActive() { return activeStation == Station.STONECUTTER; }
    }

    private class EIStonecutterResultSlot extends Slot {
        EIStonecutterResultSlot(SimpleContainer c, int slot, int x, int y) { super(c, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean isActive() { return activeStation == Station.STONECUTTER; }
        @Override
        public void onTake(Player player, ItemStack carried) {
            ItemStack input = stonecutterInput.getItem(0);
            if (!input.isEmpty()) { input.shrink(1); stonecutterInput.setChanged(); }
            super.onTake(player, carried);
        }
    }

    private class EIGrindstoneInputSlot extends Slot {
        EIGrindstoneInputSlot(SimpleContainer c, int slot, int x, int y) { super(c, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return true; }
        @Override public boolean isActive() { return activeStation == Station.GRINDSTONE; }
    }

    private class EIGrindstoneResultSlot extends Slot {
        EIGrindstoneResultSlot(SimpleContainer c, int slot, int x, int y) { super(c, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean isActive() { return activeStation == Station.GRINDSTONE; }
        @Override
        public void onTake(Player player, ItemStack carried) {
            if (player instanceof ServerPlayer sp) {
                int xp = grindstoneXpReward;
                if (xp > 0) ExperienceOrb.award(sp.level(), sp.position(), xp);
            }
            grindstoneRepairSlots.setItem(0, ItemStack.EMPTY);
            grindstoneRepairSlots.setItem(1, ItemStack.EMPTY);
            grindstoneRepairSlots.setChanged();
            grindstoneXpReward = 0;
            super.onTake(player, carried);
        }
    }

    private class EISmithingInputSlot extends Slot {
        private final Station st;
        EISmithingInputSlot(SimpleContainer c, int slot, int x, int y, Station st) { super(c, slot, x, y); this.st = st; }
        @Override public boolean mayPlace(ItemStack stack) { return true; }
        @Override public boolean isActive() { return activeStation == st; }
    }

    private class EISmithingResultSlot extends Slot {
        EISmithingResultSlot(SimpleContainer c, int slot, int x, int y) { super(c, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean isActive() { return activeStation == Station.SMITHING; }
        @Override
        public void onTake(Player player, ItemStack carried) {
            smithingInput.getItem(0).shrink(1);
            smithingInput.getItem(1).shrink(1);
            smithingInput.getItem(2).shrink(1);
            smithingInput.setChanged();
            super.onTake(player, carried);
        }
    }

    private class EIBrewingPotionSlot extends Slot {
        EIBrewingPotionSlot(SimpleContainer c, int slot, int x, int y) { super(c, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return true; }
        @Override public boolean isActive() { return activeStation == Station.BREWING; }
    }

    private class EIBrewingIngredientSlot extends Slot {
        EIBrewingIngredientSlot(SimpleContainer c, int slot, int x, int y) { super(c, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) {
            if (!(player instanceof ServerPlayer sp)) return true;
            return StationProcessing.isBrewingReagent(sp.level(), stack);
        }
        @Override public boolean isActive() { return activeStation == Station.BREWING; }
    }

    private class EIBrewingFuelSlot extends Slot {
        EIBrewingFuelSlot(SimpleContainer c, int slot, int x, int y) { super(c, slot, x, y); }
        @Override public boolean mayPlace(ItemStack stack) {
            return StationProcessing.isBrewingFuel(stack);
        }
        @Override public boolean isActive() { return activeStation == Station.BREWING; }
    }

    public interface ClientPageBinding {
        String pageId();

        PageType pageType();

        void onPageSelected();

        void scrollTo(float pos);

        ItemStack quickMoveIntoPage(ItemStack stack);

        void markPageChanged();

        ItemStack extractItemFromPage(ItemStack template, int maxCount);

        void refreshAfterMenuInteraction(SourceInventory source);
    }
}
