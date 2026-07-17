package com.emma.endinv.client.gui.page.slotView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;

public interface SlotView extends Renderable {

    void renderSlotHighlightBack(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    void renderSlotHighlightFront(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);
}
