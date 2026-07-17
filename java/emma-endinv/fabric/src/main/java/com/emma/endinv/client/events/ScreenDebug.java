package com.emma.endinv.client.events;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ScreenDebug {

    private static final int OVERLAY_COLOR = 0xAA000000;
    private static final Component HIDE_MESSAGE = Component.literal("[F4]Menu screen rendering stopped!");

    private static boolean hideMenu;

    private ScreenDebug() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                renderOverlay(s, graphics);
                com.emma.endinv.client.ScreenDebug.debugInfo(s, graphics, mouseX, mouseY);
            });

            ScreenKeyboardEvents.afterKeyPress(screen).register((s, keyCode, scanCode, modifiers) -> {
                com.emma.endinv.client.ScreenDebug.click(keyCode, s);
                if (keyCode == InputConstants.KEY_F4) {
                    hideMenu = !hideMenu;
                }
            });
        });
    }

    private static void renderOverlay(Screen screen, GuiGraphics graphics) {
        if (!hideMenu) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        graphics.fill(0, 0, screen.width, screen.height, OVERLAY_COLOR);
        graphics.drawString(minecraft.font, HIDE_MESSAGE, 0, 0, 0xFFFFAA00);
    }
}
