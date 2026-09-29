package com.emma.endinv.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TooltipRenderer {
    private TooltipRenderer() {
    }

    public static void renderText(GuiGraphicsExtractor guiGraphics, Font font, List<Component> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        guiGraphics.tooltip(font, toClientComponents(lines, Optional.empty()), mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null, false);
    }

    public static void renderItem(
            GuiGraphicsExtractor guiGraphics,
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
        guiGraphics.tooltip(
                font,
                toClientComponents(lines, optionalImage),
                mouseX,
                mouseY,
                DefaultTooltipPositioner.INSTANCE,
                stack.get(DataComponents.TOOLTIP_STYLE),
                true
        );
    }

    private static List<ClientTooltipComponent> toClientComponents(List<Component> lines, Optional<TooltipComponent> optionalImage) {
        List<ClientTooltipComponent> components = new ArrayList<>(lines.size() + (optionalImage.isPresent() ? 1 : 0));
        for (Component line : lines) {
            components.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        optionalImage.ifPresent(image -> components.add(components.isEmpty() ? 0 : 1, ClientTooltipComponent.create(image)));
        return components;
    }
}