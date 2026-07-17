package com.emma.endinv.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class TooltipRenderer {
    private TooltipRenderer() {
    }

    public static void renderText(GuiGraphics guiGraphics, Font font, List<Component> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        guiGraphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    public static void renderItem(
            GuiGraphics guiGraphics,
            Font font,
            List<Component> lines,
            Optional<TooltipComponent> optionalImage,
            ItemStack stack,
            int mouseX,
            int mouseY
    ) {
        if (lines.isEmpty() && optionalImage.isEmpty()) {
            return;
        }
        guiGraphics.renderTooltip(font, lines, optionalImage, mouseX, mouseY);
    }
}
