package com.emma.endinv.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int endinv$getLeftPos();

    @Accessor("topPos")
    int endinv$getTopPos();

    @Accessor("imageWidth")
    int endinv$getImageWidth();

    @Accessor("imageHeight")
    int endinv$getImageHeight();

    @Accessor("hoveredSlot")
    Slot endinv$getHoveredSlot();

    @Invoker("extractTooltip")
    void endinv$invokeExtractTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY);
}
