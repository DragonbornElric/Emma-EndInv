package com.emma.endinv.client.gui;

import com.emma.endinv.ModInfo;
import com.emma.endinv.client.gui.recipebook.EndInvCookingRecipeBookComponent;
import com.emma.endinv.client.gui.recipebook.EndInvCraftingRecipeBookComponent;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.menu.Station;
import com.emma.endinv.mixin.AbstractRecipeBookScreenAccessor;
import com.emma.endinv.network.payloads.toServer.SetActiveStationPayload;
import com.emma.endinv.util.NotNullWhenInitialized;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public class EndlessInventoryScreen extends AbstractRecipeBookScreen<EndlessInventoryMenu> {
    private static final Identifier CRAFTING_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/crafting_table.png");
    private static final Identifier FURNACE_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final Identifier SMOKER_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/smoker.png");
    private static final Identifier BLAST_FURNACE_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/blast_furnace.png");
    private static final Identifier STONECUTTER_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");
    private static final Identifier GRINDSTONE_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/grindstone.png");
    private static final Identifier SMITHING_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/smithing.png");
    private static final Identifier BREWING_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/brewing_stand.png");
    private static final Identifier LIT_PROGRESS_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final Identifier BURN_PROGRESS_SPRITE = Identifier.withDefaultNamespace("container/furnace/burn_progress");
    private static final Identifier BREW_FUEL_SPRITE = Identifier.withDefaultNamespace("container/brewing_stand/fuel_length");
    private static final Identifier BREW_PROGRESS_SPRITE = Identifier.withDefaultNamespace("container/brewing_stand/brew_progress");
    private static final Identifier STONECUTTER_SCROLLER_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller");
    private static final Identifier STONECUTTER_SCROLLER_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
    private static final Identifier STONECUTTER_RECIPE_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe");
    private static final Identifier STONECUTTER_RECIPE_SELECTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");
    private static final Identifier STONECUTTER_RECIPE_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");

    @NotNullWhenInitialized
    private ScreenFramework frameWork;
    private float stonecutterScrollOffs;
    private boolean stonecutterScrolling;
    private int stonecutterStartIndex;
    private boolean stonecutterDisplayRecipes;
    private boolean stonecutterLastHadInput;
    private final EndInvCraftingRecipeBookComponent craftingRecipeBookComponent;
    private final EnumMap<Station, EndInvCookingRecipeBookComponent> cookingComponents = new EnumMap<>(Station.class);
    private Station activeStation = Station.NONE;
    @Nullable private StationIconButton craftingButton;
    @Nullable private StationIconButton furnaceButton;
    @Nullable private StationIconButton smokerButton;
    @Nullable private StationIconButton blastFurnaceButton;
    @Nullable private StationIconButton stonecutterButton;
    @Nullable private StationIconButton grindstoneButton;
    @Nullable private StationIconButton smithingButton;
    @Nullable private StationIconButton brewingButton;
    @Nullable private StorageTrackerButton storageButton;

    // Static capture so we can store the super() argument in a final field
    @Nullable private static EndInvCraftingRecipeBookComponent pendingCraftingComp;

    public EndlessInventoryScreen(EndlessInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, pendingCraftingComp = new EndInvCraftingRecipeBookComponent(menu), playerInventory, title);
        this.craftingRecipeBookComponent = pendingCraftingComp;
        pendingCraftingComp = null;
        this.imageWidth = 176;
        this.imageHeight = 114 + menu.getBaseRows() * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected ScreenPosition getRecipeBookButtonPosition() {
        int centeredLeft = (this.width - this.imageWidth) / 2;
        int centeredTop = (this.height - this.imageHeight) / 2;
        return new ScreenPosition(centeredLeft, centeredTop - 22);
    }

    @Override
    protected void onRecipeBookButtonClick() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        updateStationButtonPositions();
    }

    @Override
    public void init() {
        // Ensure ARBS always initialises and adds the crafting component, regardless of previous state
        ((AbstractRecipeBookScreenAccessor) this).setRecipeBookComponent(craftingRecipeBookComponent);
        super.init();
        this.inventoryLabelY = this.imageHeight - 94;
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        var existing = ScreenFramework.getInstance();
        if (existing != null) existing.onClose();
        this.frameWork = new ScreenFramework(this);
        frameWork.addWidgetToScreen(this::addRenderableWidget);

        for (Station st : EndlessInventoryMenu.COOKING_STATIONS) {
            cookingComponents.computeIfAbsent(st, s -> EndInvCookingRecipeBookComponent.forStation(menu, s));
        }

        craftingButton = new StationIconButton(0, 0, new ItemStack(Items.CRAFTING_TABLE), Station.CRAFTING,
                Component.translatable("emma_endinv.button.crafting_table"));
        furnaceButton = new StationIconButton(0, 0, new ItemStack(Items.FURNACE), Station.FURNACE,
                Component.translatable("emma_endinv.button.furnace"));
        smokerButton = new StationIconButton(0, 0, new ItemStack(Items.SMOKER), Station.SMOKER,
                Component.translatable("emma_endinv.button.smoker"));
        blastFurnaceButton = new StationIconButton(0, 0, new ItemStack(Items.BLAST_FURNACE), Station.BLAST_FURNACE,
                Component.translatable("emma_endinv.button.blast_furnace"));
        stonecutterButton = new StationIconButton(0, 0, new ItemStack(Items.STONECUTTER), Station.STONECUTTER,
                Component.translatable("emma_endinv.button.stonecutter"));
        grindstoneButton = new StationIconButton(0, 0, new ItemStack(Items.GRINDSTONE), Station.GRINDSTONE,
                Component.translatable("emma_endinv.button.grindstone"));
        smithingButton = new StationIconButton(0, 0, new ItemStack(Items.SMITHING_TABLE), Station.SMITHING,
                Component.translatable("emma_endinv.button.smithing_table"));
        brewingButton = new StationIconButton(0, 0, new ItemStack(Items.BREWING_STAND), Station.BREWING,
                Component.translatable("emma_endinv.button.brewing_stand"));
        addRenderableWidget(craftingButton);
        addRenderableWidget(furnaceButton);
        addRenderableWidget(smokerButton);
        addRenderableWidget(blastFurnaceButton);
        addRenderableWidget(stonecutterButton);
        addRenderableWidget(grindstoneButton);
        addRenderableWidget(smithingButton);
        addRenderableWidget(brewingButton);
        storageButton = new StorageTrackerButton(0, 0);
        addRenderableWidget(storageButton);
        updateStationButtonPositions();

        // Restore active station from menu (handles screen resize)
        activeStation = Station.NONE;
        Station savedStation = menu.getActiveStation();
        if (savedStation != Station.NONE) {
            applyStationSwap(Station.NONE, savedStation);
            activeStation = savedStation;
        }

        if (this.frameWork != null) {
            this.frameWork.resizePageRows(menu.getVisibleRows());
        }
    }

    private void applyStationSwap(Station from, Station to) {
        RecipeBookComponent<?> oldComp = getRecipeCompForStation(from);
        RecipeBookComponent<?> newComp = getRecipeCompForStation(to);
        if (oldComp == newComp) return;

        if (oldComp.isVisible()) oldComp.toggleVisibility();
        removeWidget(oldComp);
        newComp.init(this.width, this.height, this.minecraft, this.width < 379);
        ((AbstractRecipeBookScreenAccessor) this).setRecipeBookComponent(newComp);
        addWidget(newComp);
    }

    private RecipeBookComponent<?> getRecipeCompForStation(Station station) {
        if (station.isCooking()) return cookingComponents.get(station);
        return craftingRecipeBookComponent;
    }

    private void setActiveStation(Station clicked) {
        Station newStation = (clicked == activeStation) ? Station.NONE : clicked;
        if (newStation == activeStation) return;

        Station old = activeStation;
        activeStation = newStation;
        menu.setActiveStation(newStation);
        ModInfo.getPacketDistributor().sendToServer(new SetActiveStationPayload(newStation));

        applyStationSwap(old, newStation);

        int previousTop = this.topPos;
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        updateStationButtonPositions();
        if (frameWork != null) {
            frameWork.resizePageRows(menu.getVisibleRows());
            frameWork.move(0, this.topPos - previousTop);
        }
    }

    private void updateStationButtonPositions() {
        if (craftingButton == null || furnaceButton == null || smokerButton == null || blastFurnaceButton == null
                || stonecutterButton == null || grindstoneButton == null || smithingButton == null || brewingButton == null) return;
        int rightEdge = this.leftPos + this.imageWidth - 8;
        // Row 1 (y = topPos - 22): crafting, furnace, smoker, blast furnace
        int row1Y = this.topPos - 22;
        blastFurnaceButton.setX(rightEdge - 22);       blastFurnaceButton.setY(row1Y);
        smokerButton.setX(rightEdge - 22 - 24);        smokerButton.setY(row1Y);
        furnaceButton.setX(rightEdge - 22 - 48);       furnaceButton.setY(row1Y);
        craftingButton.setX(rightEdge - 22 - 72);      craftingButton.setY(row1Y);
        // Row 2 (y = topPos - 44): stonecutter, grindstone, smithing, brewing
        int row2Y = this.topPos - 44;
        brewingButton.setX(rightEdge - 22);            brewingButton.setY(row2Y);
        smithingButton.setX(rightEdge - 22 - 24);      smithingButton.setY(row2Y);
        grindstoneButton.setX(rightEdge - 22 - 48);    grindstoneButton.setY(row2Y);
        stonecutterButton.setX(rightEdge - 22 - 72);   stonecutterButton.setY(row2Y);
        // Storage tracker sits at the left end of row 2, above the recipe book button.
        if (storageButton != null) { storageButton.setX(this.leftPos); storageButton.setY(row2Y); }
    }

    private void drawStationBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int craftYScreen = this.topPos + 18 * menu.getVisibleRows() + 18;
        Station st = menu.getActiveStation();
        if (st == Station.CRAFTING) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_TEXTURE, this.leftPos, craftYScreen, 0, 12, 176, 58, 256, 256);
        } else if (st.isCooking()) {
            Identifier texture = switch (st) {
                case SMOKER -> SMOKER_TEXTURE;
                case BLAST_FURNACE -> BLAST_FURNACE_TEXTURE;
                default -> FURNACE_TEXTURE;
            };
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, this.leftPos, craftYScreen, 0, 12, 176, 62, 256, 256);
            if (menu.isFurnaceLit()) {
                int litHeight = Mth.ceil(menu.getLitProgress() * 13.0f) + 1;
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        LIT_PROGRESS_SPRITE, 14, 14, 0, 14 - litHeight,
                        this.leftPos + 56, craftYScreen + 24 + 14 - litHeight, 14, litHeight);
            }
            int arrowWidth = Mth.ceil(menu.getBurnProgress() * 24.0f);
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    BURN_PROGRESS_SPRITE, 24, 16, 0, 0,
                    this.leftPos + 79, craftYScreen + 22, arrowWidth, 16);
        } else if (st == Station.STONECUTTER) {
            boolean hasInput = menu.hasStonecutterInput();
            if (hasInput != stonecutterLastHadInput) {
                stonecutterLastHadInput = hasInput;
                stonecutterDisplayRecipes = hasInput;
                stonecutterScrollOffs = 0;
                stonecutterStartIndex = 0;
            }
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, STONECUTTER_TEXTURE, this.leftPos, craftYScreen, 0, 0, 176, 68, 256, 256);
            if (stonecutterDisplayRecipes) {
                int scrollY = craftYScreen + 15 + (int)(41.0f * stonecutterScrollOffs);
                Identifier scrollSprite = isStonecutterScrollBarActive() ? STONECUTTER_SCROLLER_SPRITE : STONECUTTER_SCROLLER_DISABLED_SPRITE;
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, scrollSprite, this.leftPos + 119, scrollY, 12, 15);
                var recipes = menu.getStonecutterRecipes();
                int endIndex = Math.min(stonecutterStartIndex + 12, recipes.size());
                int baseX = this.leftPos + 52;
                int baseY = craftYScreen + 14;
                ContextMap context = SlotDisplayContext.fromLevel(this.minecraft.level);
                for (int i = stonecutterStartIndex; i < endIndex; i++) {
                    int posIndex = i - stonecutterStartIndex;
                    int col = posIndex % 4;
                    int row = posIndex / 4;
                    int posX = baseX + col * 16;
                    int posY = baseY + row * 18 + 2;
                    Identifier sprite;
                    if (i == menu.getSelectedStonecutterRecipe()) {
                        sprite = STONECUTTER_RECIPE_SELECTED_SPRITE;
                    } else if (mouseX >= posX && mouseY >= posY - 1 && mouseX < posX + 16 && mouseY < posY - 1 + 18) {
                        sprite = STONECUTTER_RECIPE_HIGHLIGHTED_SPRITE;
                    } else {
                        sprite = STONECUTTER_RECIPE_SPRITE;
                    }
                    guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, posX, posY - 1, 16, 18);
                    guiGraphics.item(recipes.get(i).recipe().optionDisplay().resolveForFirstStack(context), posX, posY);
                }
            }
        } else if (st == Station.GRINDSTONE) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GRINDSTONE_TEXTURE, this.leftPos, craftYScreen, 0, 0, 176, 58, 256, 256);
        } else if (st == Station.SMITHING) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, SMITHING_TEXTURE, this.leftPos, craftYScreen, 0, 0, 176, 62, 256, 256);
        } else if (st == Station.BREWING) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BREWING_TEXTURE, this.leftPos, craftYScreen, 0, 0, 176, 62, 256, 256);
            int fuelLength = Mth.clamp(Mth.ceil(menu.getBrewingFuelProgress() * 18.0f), 0, 18);
            if (fuelLength > 0) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, BREW_FUEL_SPRITE, 18, 4, 0, 0,
                        this.leftPos + 60, craftYScreen + 44, fuelLength, 4);
            }
            int brewLength = Mth.ceil(28.0f * (1.0f - menu.getBrewingProgress()));
            if (brewLength > 0) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, BREW_PROGRESS_SPRITE, 9, 28, 0, 0,
                        this.leftPos + 97, craftYScreen + 16, 9, brewLength);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        frameWork.renderBg(guiGraphics, mouseX, mouseY, partialTick);
        drawStationBackground(guiGraphics, mouseX, mouseY);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        frameWork.render(guiGraphics, mouseX, mouseY, partialTick);
        // The pages are drawn after the screen, so draw the stack held on the cursor again on top of them.
        guiGraphics.nextStratum();
        extractCarriedItem(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (menu.getActiveStation() == Station.STONECUTTER && stonecutterDisplayRecipes) {
            int craftYScreen = this.topPos + 18 * menu.getVisibleRows() + 18;
            int baseX = this.leftPos + 52;
            int baseY = craftYScreen + 14;
            var recipes = menu.getStonecutterRecipes();
            int endIndex = Math.min(stonecutterStartIndex + 12, recipes.size());
            for (int i = stonecutterStartIndex; i < endIndex; i++) {
                int posIndex = i - stonecutterStartIndex;
                int posX = baseX + (posIndex % 4) * 16;
                int posY = baseY + (posIndex / 4) * 18 + 2;
                if (mouseX >= posX && mouseX < posX + 16 && mouseY >= posY && mouseY < posY + 18) {
                    ContextMap context = SlotDisplayContext.fromLevel(this.minecraft.level);
                    SlotDisplay display = recipes.get(i).recipe().optionDisplay();
                    graphics.setTooltipForNextFrame(this.font, display.resolveForFirstStack(context), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean pre) {
        if (menu.getActiveStation() == Station.STONECUTTER && stonecutterDisplayRecipes) {
            int craftYScreen = this.topPos + 18 * menu.getVisibleRows() + 18;
            int baseX = this.leftPos + 52;
            int baseY = craftYScreen + 14;
            var recipes = menu.getStonecutterRecipes();
            int endIndex = Math.min(stonecutterStartIndex + 12, recipes.size());
            for (int i = stonecutterStartIndex; i < endIndex; i++) {
                int posIndex = i - stonecutterStartIndex;
                double relX = event.x() - (baseX + (posIndex % 4) * 16);
                double relY = event.y() - (baseY + (posIndex / 4) * 18);
                if (relX >= 0 && relY >= 0 && relX < 16 && relY < 18 && menu.clickMenuButton(this.minecraft.player, i)) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                    this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    return true;
                }
            }
            int scrollX = this.leftPos + 119;
            int scrollY = craftYScreen + 9;
            if (event.x() >= scrollX && event.x() < scrollX + 12 && event.y() >= scrollY && event.y() < scrollY + 54) {
                stonecutterScrolling = true;
            }
        }
        for (GuiEventListener guieventlistener : this.children()) {
            if (guieventlistener.mouseClicked(event, pre)) {
                this.setFocused(guieventlistener);
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                    this.setDragging(true);
                }
                return true;
            }
        }
        return frameWork.mouseClicked(event, pre) || super.mouseClicked(event, pre);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        if (stonecutterScrolling && isStonecutterScrollBarActive()) {
            int craftYScreen = this.topPos + 18 * menu.getVisibleRows() + 18;
            int yscr = craftYScreen + 14;
            stonecutterScrollOffs = ((float)event.y() - yscr - 7.5f) / 39.0f;
            stonecutterScrollOffs = Mth.clamp(stonecutterScrollOffs, 0.0f, 1.0f);
            stonecutterStartIndex = (int)(stonecutterScrollOffs * getStonecutterOffscreenRows() + 0.5f) * 4;
            return true;
        }
        return frameWork.mouseDragged(event, x, y) || super.mouseDragged(event, x, y);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent p_446114_) {
        stonecutterScrolling = false;
        return frameWork.mouseReleased(p_446114_) || super.mouseReleased(p_446114_);
    }

    @Override
    public boolean mouseScrolled(double p_364830_, double p_360707_, double p_364436_, double p_364417_) {
        if (menu.getActiveStation() == Station.STONECUTTER && isStonecutterScrollBarActive()) {
            int offscreenRows = getStonecutterOffscreenRows();
            stonecutterScrollOffs = Mth.clamp(stonecutterScrollOffs - (float)p_364417_ / offscreenRows, 0.0f, 1.0f);
            stonecutterStartIndex = (int)(stonecutterScrollOffs * offscreenRows + 0.5f) * 4;
            return true;
        }
        return super.mouseScrolled(p_364830_, p_360707_, p_364436_, p_364417_) || frameWork.mouseScrolled(p_364830_, p_360707_, p_364436_, p_364417_);
    }

    @Override
    public boolean keyPressed(KeyEvent p_445387_) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(p_445387_)) {
            this.onClose();
            return true;
        }
        return frameWork.keyPressed(p_445387_) || super.keyPressed(p_445387_);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return frameWork.charTyped(event) || super.charTyped(event);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput type) {
        super.slotClicked(slot, slotId, mouseButton, type);
        this.menu.broadcastChanges();
    }

    @Override
    public void onClose() {
        super.onClose();
        frameWork.onClose();
    }

    public com.emma.endinv.menu.page.pageManager.PageMetaDataManager getPageManager() {
        return menu;
    }

    public net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> getScreen() {
        return this;
    }

    public ScreenFramework getFrameWork() {
        return frameWork;
    }

    public int getGuiLeft() {
        return leftPos;
    }

    public int getGuiTop() {
        return topPos;
    }

    public int getXSize() {
        return imageWidth;
    }

    public int getYSize() {
        return imageHeight;
    }

    private boolean isStonecutterScrollBarActive() {
        return stonecutterDisplayRecipes && menu.getStonecutterRecipes().size() > 12;
    }

    private int getStonecutterOffscreenRows() {
        return (menu.getStonecutterRecipes().size() + 3) / 4 - 3;
    }

    private class StationIconButton extends AbstractButton {
        private final ItemStack icon;
        private final Station station;

        StationIconButton(int x, int y, ItemStack icon, Station station, Component tooltip) {
            super(x, y, 22, 22, CommonComponents.EMPTY);
            this.icon = icon;
            this.station = station;
            this.setTooltip(Tooltip.create(tooltip));
            this.setOverrideRenderHighlightedSprite(() -> EndlessInventoryScreen.this.activeStation == this.station);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            EndlessInventoryScreen.this.setActiveStation(this.station);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(graphics);
            graphics.item(icon, this.getX() + 3, this.getY() + 3, 0);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    /** Opens the {@link StorageTrackerScreen} over this screen; closing it returns here. */
    private class StorageTrackerButton extends AbstractButton {
        private final ItemStack icon = new ItemStack(Items.CHEST);

        StorageTrackerButton(int x, int y) {
            super(x, y, 22, 22, CommonComponents.EMPTY);
            this.setTooltip(Tooltip.create(Component.translatable("endinv.storage.open.tip")));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            EndlessInventoryScreen.this.minecraft.setScreen(new StorageTrackerScreen(EndlessInventoryScreen.this));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(graphics);
            graphics.item(icon, this.getX() + 3, this.getY() + 3, 0);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
