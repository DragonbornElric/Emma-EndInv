package com.emma.endinv.client.gui.bg;

import com.emma.endinv.client.gui.ScreenFramework;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface SFBgRenderer {

    ScreenFramework getScreenFrameWork();

    void renderBg(@NotNull GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY);

    IRectangleParam pageSwitchBarParam();

    default Optional<PageBgRender> getDefaultPageBgRenderer(){
        return Optional.empty();
    }

    @FunctionalInterface
    interface PageBgRender {
        void renderBg(@NotNull GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY);
    }
}
