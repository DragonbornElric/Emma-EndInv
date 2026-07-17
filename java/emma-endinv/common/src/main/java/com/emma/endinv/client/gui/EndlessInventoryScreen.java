package com.emma.endinv.client.gui;

import com.emma.endinv.ModInfo;
import com.emma.endinv.client.gui.recipebook.EndInvCookingRecipeBookComponent;
import com.emma.endinv.client.gui.recipebook.EndInvCraftingRecipeBookComponent;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.menu.Station;
import com.emma.endinv.network.payloads.toServer.SetActiveStationPayload;
import com.emma.endinv.util.NotNullWhenInitialized;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;

/**
 * EndInv's full inventory and workstation screen, adapted to the primitive
 * input and recipe-book APIs used by Minecraft 1.20.1.
 */
public class EndlessInventoryScreen extends AbstractContainerScreen<EndlessInventoryMenu>
        implements RecipeUpdateListener {
    private static final ResourceLocation CRAFTING_TEXTURE =
            new ResourceLocation("textures/gui/container/crafting_table.png");
    private static final ResourceLocation FURNACE_TEXTURE =
            new ResourceLocation("textures/gui/container/furnace.png");
    private static final ResourceLocation SMOKER_TEXTURE =
            new ResourceLocation("textures/gui/container/smoker.png");
    private static final ResourceLocation BLAST_FURNACE_TEXTURE =
            new ResourceLocation("textures/gui/container/blast_furnace.png");
    private static final ResourceLocation STONECUTTER_TEXTURE =
            new ResourceLocation("textures/gui/container/stonecutter.png");
    private static final ResourceLocation GRINDSTONE_TEXTURE =
            new ResourceLocation("textures/gui/container/grindstone.png");
    private static final ResourceLocation SMITHING_TEXTURE =
            new ResourceLocation("textures/gui/container/smithing.png");
    private static final ResourceLocation BREWING_TEXTURE =
            new ResourceLocation("textures/gui/container/brewing_stand.png");
    private static final ResourceLocation RECIPE_BUTTON_TEXTURE =
            new ResourceLocation("textures/gui/recipe_button.png");

    @NotNullWhenInitialized
    private ScreenFramework frameWork;
    private final EndInvCraftingRecipeBookComponent craftingRecipeBookComponent;
    private final EnumMap<Station, EndInvCookingRecipeBookComponent> cookingComponents =
            new EnumMap<>(Station.class);
    private RecipeBookComponent recipeBookComponent;
    private boolean widthTooNarrow;
    private float stonecutterScrollOffs;
    private boolean stonecutterScrolling;
    private int stonecutterStartIndex;
    private boolean stonecutterDisplayRecipes;
    private boolean stonecutterLastHadInput;
    private Station activeStation = Station.NONE;
    @Nullable private ImageButton recipeBookButton;
    @Nullable private StationIconButton craftingButton;
    @Nullable private StationIconButton furnaceButton;
    @Nullable private StationIconButton smokerButton;
    @Nullable private StationIconButton blastFurnaceButton;
    @Nullable private StationIconButton stonecutterButton;
    @Nullable private StationIconButton grindstoneButton;
    @Nullable private StationIconButton smithingButton;
    @Nullable private StationIconButton brewingButton;

    public EndlessInventoryScreen(EndlessInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.craftingRecipeBookComponent = new EndInvCraftingRecipeBookComponent(menu);
        this.recipeBookComponent = craftingRecipeBookComponent;
        this.imageWidth = 176;
        this.imageHeight = 114 + menu.getBaseRows() * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.widthTooNarrow = this.width < 379;
        this.inventoryLabelY = this.imageHeight - 94;
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        for (Station station : EndlessInventoryMenu.COOKING_STATIONS) {
            cookingComponents.computeIfAbsent(
                    station, key -> EndInvCookingRecipeBookComponent.forStation(menu, key));
        }
        this.activeStation = menu.getActiveStation();
        this.recipeBookComponent = getRecipeCompForStation(activeStation);
        this.recipeBookComponent.init(
                this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
        this.addWidget(this.recipeBookComponent);

        this.recipeBookButton = new ImageButton(
                this.leftPos, this.topPos - 22, 20, 18,
                0, 0, 19, RECIPE_BUTTON_TEXTURE,
                button -> {
                    recipeBookComponent.toggleVisibility();
                    updateStationButtonPositions();
                });
        this.addRenderableWidget(recipeBookButton);

        ScreenFramework existing = ScreenFramework.getInstance();
        if (existing != null) existing.onClose();
        this.frameWork = new ScreenFramework(this);
        this.frameWork.addWidgetToScreen(this::addRenderableWidget);

        craftingButton = stationButton(Items.CRAFTING_TABLE, Station.CRAFTING,
                "endless_inventory.button.crafting_table");
        furnaceButton = stationButton(Items.FURNACE, Station.FURNACE,
                "endless_inventory.button.furnace");
        smokerButton = stationButton(Items.SMOKER, Station.SMOKER,
                "endless_inventory.button.smoker");
        blastFurnaceButton = stationButton(Items.BLAST_FURNACE, Station.BLAST_FURNACE,
                "endless_inventory.button.blast_furnace");
        stonecutterButton = stationButton(Items.STONECUTTER, Station.STONECUTTER,
                "endless_inventory.button.stonecutter");
        grindstoneButton = stationButton(Items.GRINDSTONE, Station.GRINDSTONE,
                "endless_inventory.button.grindstone");
        smithingButton = stationButton(Items.SMITHING_TABLE, Station.SMITHING,
                "endless_inventory.button.smithing_table");
        brewingButton = stationButton(Items.BREWING_STAND, Station.BREWING,
                "endless_inventory.button.brewing_stand");
        updateStationButtonPositions();
        this.frameWork.resizePageRows(menu.getVisibleRows());
    }

    private StationIconButton stationButton(net.minecraft.world.item.Item item, Station station, String key) {
        StationIconButton button = new StationIconButton(
                0, 0, new ItemStack(item), station, Component.translatable(key));
        this.addRenderableWidget(button);
        return button;
    }

    private void applyStationSwap(Station from, Station to) {
        RecipeBookComponent oldComponent = getRecipeCompForStation(from);
        RecipeBookComponent newComponent = getRecipeCompForStation(to);
        if (oldComponent == newComponent) return;

        boolean wasVisible = oldComponent.isVisible();
        this.removeWidget(oldComponent);
        newComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
        if (wasVisible && !newComponent.isVisible()) {
            newComponent.toggleVisibility();
        }
        this.recipeBookComponent = newComponent;
        this.addWidget(newComponent);
    }

    private RecipeBookComponent getRecipeCompForStation(Station station) {
        if (station.isCooking()) return cookingComponents.get(station);
        return craftingRecipeBookComponent;
    }

    private void setActiveStation(Station clicked) {
        Station newStation = clicked == activeStation ? Station.NONE : clicked;
        if (newStation == activeStation) return;

        Station oldStation = activeStation;
        activeStation = newStation;
        menu.setActiveStation(newStation);
        ModInfo.getPacketDistributor().sendToServer(new SetActiveStationPayload(newStation));
        applyStationSwap(oldStation, newStation);

        int previousTop = this.topPos;
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        updateStationButtonPositions();
        frameWork.resizePageRows(menu.getVisibleRows());
        frameWork.move(0, this.topPos - previousTop);
    }

    private void updateStationButtonPositions() {
        if (recipeBookButton != null) {
            recipeBookButton.setPosition(this.leftPos, this.topPos - 22);
        }
        if (craftingButton == null || furnaceButton == null || smokerButton == null
                || blastFurnaceButton == null || stonecutterButton == null
                || grindstoneButton == null || smithingButton == null || brewingButton == null) {
            return;
        }
        int rightEdge = this.leftPos + this.imageWidth - 8;
        int row1Y = this.topPos - 22;
        blastFurnaceButton.setPosition(rightEdge - 22, row1Y);
        smokerButton.setPosition(rightEdge - 46, row1Y);
        furnaceButton.setPosition(rightEdge - 70, row1Y);
        craftingButton.setPosition(rightEdge - 94, row1Y);
        int row2Y = this.topPos - 44;
        brewingButton.setPosition(rightEdge - 22, row2Y);
        smithingButton.setPosition(rightEdge - 46, row2Y);
        grindstoneButton.setPosition(rightEdge - 70, row2Y);
        stonecutterButton.setPosition(rightEdge - 94, row2Y);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        recipeBookComponent.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        if (recipeBookComponent.isVisible() && widthTooNarrow) {
            this.renderBg(graphics, partialTick, mouseX, mouseY);
            recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
        } else {
            recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
            super.render(graphics, mouseX, mouseY, partialTick);
            recipeBookComponent.renderGhostRecipe(
                    graphics, this.leftPos, this.topPos, true, partialTick);
            frameWork.render(graphics, mouseX, mouseY, partialTick);
        }
        this.renderTooltip(graphics, mouseX, mouseY);
        recipeBookComponent.renderTooltip(
                graphics, this.leftPos, this.topPos, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        frameWork.renderBg(graphics, mouseX, mouseY, partialTick);
        drawStationBackground(graphics, mouseX, mouseY);
    }

    private void drawStationBackground(GuiGraphics graphics, int mouseX, int mouseY) {
        int stationY = this.topPos + 18 * menu.getVisibleRows() + 18;
        Station station = menu.getActiveStation();
        if (station == Station.CRAFTING) {
            graphics.blit(CRAFTING_TEXTURE, this.leftPos, stationY, 0, 12, 176, 58);
        } else if (station.isCooking()) {
            ResourceLocation texture = station == Station.SMOKER
                    ? SMOKER_TEXTURE
                    : station == Station.BLAST_FURNACE ? BLAST_FURNACE_TEXTURE : FURNACE_TEXTURE;
            graphics.blit(texture, this.leftPos, stationY, 0, 12, 176, 62);
            if (menu.isFurnaceLit()) {
                int litHeight = Mth.ceil(menu.getLitProgress() * 13.0F) + 1;
                graphics.blit(texture,
                        this.leftPos + 56, stationY + 36 + 12 - litHeight,
                        176, 12 - litHeight, 14, litHeight);
            }
            int arrowWidth = Mth.ceil(menu.getBurnProgress() * 24.0F);
            graphics.blit(texture, this.leftPos + 79, stationY + 34,
                    176, 14, arrowWidth + 1, 16);
        } else if (station == Station.STONECUTTER) {
            renderStonecutter(graphics, mouseX, mouseY, stationY);
        } else if (station == Station.GRINDSTONE) {
            graphics.blit(GRINDSTONE_TEXTURE, this.leftPos, stationY, 0, 0, 176, 58);
        } else if (station == Station.SMITHING) {
            graphics.blit(SMITHING_TEXTURE, this.leftPos, stationY, 0, 0, 176, 62);
        } else if (station == Station.BREWING) {
            graphics.blit(BREWING_TEXTURE, this.leftPos, stationY, 0, 0, 176, 62);
            int fuelLength = Mth.clamp(Mth.ceil(menu.getBrewingFuelProgress() * 18.0F), 0, 18);
            if (fuelLength > 0) {
                graphics.blit(BREWING_TEXTURE, this.leftPos + 60, stationY + 44,
                        176, 29, fuelLength, 4);
            }
            int brewLength = Mth.ceil(28.0F * (1.0F - menu.getBrewingProgress()));
            if (brewLength > 0) {
                graphics.blit(BREWING_TEXTURE, this.leftPos + 97, stationY + 16,
                        176, 0, 9, brewLength);
            }
        }
    }

    private void renderStonecutter(GuiGraphics graphics, int mouseX, int mouseY, int stationY) {
        boolean hasInput = menu.hasStonecutterInput();
        if (hasInput != stonecutterLastHadInput) {
            stonecutterLastHadInput = hasInput;
            stonecutterDisplayRecipes = hasInput;
            stonecutterScrollOffs = 0.0F;
            stonecutterStartIndex = 0;
        }
        graphics.blit(STONECUTTER_TEXTURE, this.leftPos, stationY, 0, 0, 176, 68);
        if (!stonecutterDisplayRecipes) return;

        int scrollY = stationY + 15 + (int) (41.0F * stonecutterScrollOffs);
        graphics.blit(STONECUTTER_TEXTURE, this.leftPos + 119, scrollY,
                176, isStonecutterScrollBarActive() ? 0 : 12, 12, 15);
        List<StonecutterRecipe> recipes = menu.getStonecutterRecipes();
        int endIndex = Math.min(stonecutterStartIndex + 12, recipes.size());
        int baseX = this.leftPos + 52;
        int baseY = stationY + 14;
        for (int index = stonecutterStartIndex; index < endIndex; index++) {
            int relative = index - stonecutterStartIndex;
            int x = baseX + relative % 4 * 16;
            int y = baseY + relative / 4 * 18 + 2;
            int sourceY = 68;
            if (index == menu.getSelectedStonecutterRecipe()) {
                sourceY += 18;
            } else if (mouseX >= x && mouseY >= y - 1 && mouseX < x + 16 && mouseY < y + 17) {
                sourceY += 36;
            }
            graphics.blit(STONECUTTER_TEXTURE, x, y - 1, 0, sourceY, 16, 18);
            graphics.renderItem(recipes.get(index).getResultItem(minecraft.level.registryAccess()), x, y);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (menu.getActiveStation() != Station.STONECUTTER || !stonecutterDisplayRecipes) return;
        int stationY = this.topPos + 18 * menu.getVisibleRows() + 18;
        int baseX = this.leftPos + 52;
        int baseY = stationY + 14;
        List<StonecutterRecipe> recipes = menu.getStonecutterRecipes();
        int endIndex = Math.min(stonecutterStartIndex + 12, recipes.size());
        for (int index = stonecutterStartIndex; index < endIndex; index++) {
            int relative = index - stonecutterStartIndex;
            int x = baseX + relative % 4 * 16;
            int y = baseY + relative / 4 * 18 + 2;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 18) {
                graphics.renderTooltip(this.font,
                        recipes.get(index).getResultItem(minecraft.level.registryAccess()),
                        mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (recipeBookComponent.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(recipeBookComponent);
            return true;
        }
        if (widthTooNarrow && recipeBookComponent.isVisible()) return true;

        if (menu.getActiveStation() == Station.STONECUTTER && stonecutterDisplayRecipes) {
            int stationY = this.topPos + 18 * menu.getVisibleRows() + 18;
            int baseX = this.leftPos + 52;
            int baseY = stationY + 14;
            int endIndex = Math.min(
                    stonecutterStartIndex + 12, menu.getStonecutterRecipes().size());
            for (int index = stonecutterStartIndex; index < endIndex; index++) {
                int relative = index - stonecutterStartIndex;
                double relativeX = mouseX - (baseX + relative % 4 * 16);
                double relativeY = mouseY - (baseY + relative / 4 * 18);
                if (relativeX >= 0 && relativeY >= 0 && relativeX < 16 && relativeY < 18
                        && menu.clickMenuButton(this.minecraft.player, index)) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                            SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                    return true;
                }
            }
            int scrollX = this.leftPos + 119;
            int scrollY = stationY + 9;
            if (mouseX >= scrollX && mouseX < scrollX + 12
                    && mouseY >= scrollY && mouseY < scrollY + 54) {
                stonecutterScrolling = true;
            }
        }
        if (frameWork.mouseClicked(mouseX, mouseY, button, true)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (stonecutterScrolling && isStonecutterScrollBarActive()) {
            int stationY = this.topPos + 18 * menu.getVisibleRows() + 18;
            stonecutterScrollOffs = ((float) mouseY - (stationY + 14) - 7.5F) / 39.0F;
            stonecutterScrollOffs = Mth.clamp(stonecutterScrollOffs, 0.0F, 1.0F);
            stonecutterStartIndex =
                    (int) (stonecutterScrollOffs * getStonecutterOffscreenRows() + 0.5F) * 4;
            return true;
        }
        return frameWork.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        stonecutterScrolling = false;
        return frameWork.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (menu.getActiveStation() == Station.STONECUTTER && isStonecutterScrollBarActive()) {
            int rows = getStonecutterOffscreenRows();
            stonecutterScrollOffs = Mth.clamp(
                    stonecutterScrollOffs - (float) delta / rows, 0.0F, 1.0F);
            stonecutterStartIndex = (int) (stonecutterScrollOffs * rows + 0.5F) * 4;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta)
                || frameWork.mouseScrolled(mouseX, mouseY, 0.0D, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return frameWork.keyPressed(keyCode, scanCode, modifiers)
                || recipeBookComponent.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return frameWork.charTyped(codePoint, modifiers)
                || recipeBookComponent.charTyped(codePoint, modifiers)
                || super.charTyped(codePoint, modifiers);
    }

    @Override
    protected boolean isHovering(
            int x, int y, int width, int height, double mouseX, double mouseY) {
        return (!widthTooNarrow || !recipeBookComponent.isVisible())
                && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    @Override
    protected boolean hasClickedOutside(
            double mouseX, double mouseY, int left, int top, int button) {
        boolean outside = mouseX < left || mouseY < top
                || mouseX >= left + imageWidth || mouseY >= top + imageHeight;
        return recipeBookComponent.hasClickedOutside(
                mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight, button) && outside;
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        super.slotClicked(slot, slotId, mouseButton, type);
        recipeBookComponent.slotClicked(slot);
        this.menu.broadcastChanges();
    }

    @Override
    public void recipesUpdated() {
        recipeBookComponent.recipesUpdated();
    }

    @Override
    public RecipeBookComponent getRecipeBookComponent() {
        return recipeBookComponent;
    }

    @Override
    public void onClose() {
        super.onClose();
        if (frameWork != null) frameWork.onClose();
    }

    public com.emma.endinv.menu.page.pageManager.PageMetaDataManager getPageManager() {
        return menu;
    }

    public AbstractContainerScreen<?> getScreen() {
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
        }

        @Override
        public void onPress() {
            setActiveStation(this.station);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (activeStation == this.station) {
                graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
            }
            graphics.renderItem(icon, getX() + 3, getY() + 3);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
