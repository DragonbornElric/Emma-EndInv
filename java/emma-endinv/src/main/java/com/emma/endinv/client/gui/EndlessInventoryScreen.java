package com.emma.endinv.client.gui;

import com.emma.endinv.ModInfo;
import com.emma.endinv.client.gui.recipebook.EndInvCraftingRecipeBookComponent;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.network.payloads.toServer.ToggleCraftingPayload;
import com.emma.endinv.util.NotNullWhenInitialized;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

public class EndlessInventoryScreen extends AbstractRecipeBookScreen<EndlessInventoryMenu> {
    private static final Identifier CRAFTING_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/crafting_table.png");
    @NotNullWhenInitialized
    private ScreenFramework frameWork;
    @Nullable
    private CycleButton<Boolean> craftingToggleButton;
    private boolean craftingVisible;

    public EndlessInventoryScreen(EndlessInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, new EndInvCraftingRecipeBookComponent(menu), playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 114 + menu.getBaseRows() * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected ScreenPosition getRecipeBookButtonPosition() {
        // Use fixed centered position — leftPos may be shifted by ARBS when this is called
        int centeredLeft = (this.width - this.imageWidth) / 2;
        int centeredTop = (this.height - this.imageHeight) / 2;
        return new ScreenPosition(centeredLeft, centeredTop - 22);
    }

    @Override
    protected void onRecipeBookButtonClick() {
        // Cancel ARBS's leftPos shift — the ScreenFramework can't follow a horizontal shift,
        // so we keep the container centered and let the recipe book panel overlap if needed.
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        updateCraftingToggleButtonPosition();
    }

    @Override
    public void init() {
        super.init();  // ARBS: initialises recipe book component, sets leftPos, adds book button
        craftingVisible = menu.isCraftingVisible();
        this.inventoryLabelY = this.imageHeight - 94;
        // Keep container centered regardless of recipe book state
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        var existing = ScreenFramework.getInstance();
        if (existing != null) {
            existing.onClose();
        }
        this.frameWork = new ScreenFramework(this);

        frameWork.addWidgetToScreen(this::addRenderableWidget);
        addCraftingToggleButton();
        if (this.craftingToggleButton != null) {
            this.craftingToggleButton.setValue(craftingVisible);
        }
        if (this.frameWork != null) {
            this.frameWork.resizePageRows(menu.getVisibleRows());
        }
    }

    private void addCraftingToggleButton() {
        int width = 95;
        this.craftingToggleButton = CycleButton.onOffBuilder(false)
                .create(0, 0, width, 20,
                        Component.translatable("endless_inventory.button.crafting_table"),
                        (it, on) -> {
                            toggleCrafting();
                            if (it.getValue() != craftingVisible) it.setValue(craftingVisible);
                        });
        updateCraftingToggleButtonPosition();
        addRenderableWidget(this.craftingToggleButton);
    }

    private void updateCraftingToggleButtonPosition() {
        if (this.craftingToggleButton == null) {
            return;
        }
        int w = this.craftingToggleButton.getWidth();
        int x = this.leftPos + this.imageWidth - w - 8;
        int y = this.topPos - 20;
        this.craftingToggleButton.setX(x);
        this.craftingToggleButton.setY(y);
    }

    private void toggleCrafting() {
        craftingVisible = !craftingVisible;
        menu.setCraftingVisible(craftingVisible);
        ModInfo.getPacketDistributor().sendToServer(new ToggleCraftingPayload(craftingVisible));
        int previousTop = this.topPos;
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        updateCraftingToggleButtonPosition();
        if (frameWork != null) {
            frameWork.resizePageRows(menu.getVisibleRows());
            frameWork.move(0, this.topPos - previousTop);
        }
    }

    private void drawCraftingBackground(GuiGraphicsExtractor guiGraphics) {
        int craftX = this.leftPos;
        int craftY = this.topPos + 18 * menu.getVisibleRows() + 18;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_TEXTURE, craftX, craftY, 0, 12, 176, 58, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        frameWork.renderBg(guiGraphics, mouseX, mouseY, partialTick);
        if (menu.isCraftingVisible()) {
            drawCraftingBackground(guiGraphics);
        }
        // Delegates to ARBS which handles: extractContents (slots/labels), recipe book panel,
        // carried item, snapback, and tooltips.
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        frameWork.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean pre) {
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
        return frameWork.mouseDragged(event, x, y) || super.mouseDragged(event, x, y);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent p_446114_) {
        return frameWork.mouseReleased(p_446114_) || super.mouseReleased(p_446114_);
    }

    @Override
    public boolean mouseScrolled(double p_364830_, double p_360707_, double p_364436_, double p_364417_) {
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
        // Framework gets first chance (search box etc.); recipe book search box gets it via super (ARBS)
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
}
