package com.emma.endinv.client.events;

import com.emma.endinv.client.gui.AttachingScreen;
import com.emma.endinv.client.gui.IScreenEvent;
import com.emma.endinv.client.gui.bg.IRectangleParam;
import com.emma.endinv.client.option.ClientConfigs;
import com.emma.endinv.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class ScreenAttachment {

    @Nullable
    public static AttachingScreen<?> attachment;

    /** The ⚙ toggle button added to every attachable container screen. */
    @Nullable
    public static Button configToggleButton;

    /** Tracks the parent container's leftPos to detect recipe book toggles */
    public static int lastContainerLeft = -1;

    private ScreenAttachment() {}

    public static boolean handleMouseDrag(AbstractContainerScreen<?> screen, double mouseX, double mouseY,
                                          int button, double deltaX, double deltaY) {
        AttachingScreen<?> current = attachment;
        if (current == null || current.screen != screen || !isAttachmentActive(current)) {
            return false;
        }
        boolean[] canceled = {false};
        current.mouseDragged(new IScreenEvent() {
            @Override public double getMouseX() { return mouseX; }
            @Override public double getMouseY() { return mouseY; }
            @Override public int getMouseButton() { return button; }
            @Override public double getDragX() { return deltaX; }
            @Override public double getDragY() { return deltaY; }
            @Override public void setCanceled(boolean flag) { canceled[0] = flag; }
        });
        return canceled[0];
    }

    public static void onRenderAfterBackground(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AttachingScreen<?> current = attachment;
        if (current == null || current.getScreen() != screen || !isAttachmentActive(current)) {
            return;
        }
        current.renderPre(new IScreenEvent() {
            @Override public double getMouseX() { return mouseX; }
            @Override public double getMouseY() { return mouseY; }
            @Override public float getPartialTick() { return partialTick; }
            @Override public GuiGraphics getGuiGraphics() { return graphics; }
        });
    }

    public static void onRenderPost(AbstractContainerScreen<?> screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AttachingScreen<?> current = attachment;
        if (current == null || current.getScreen() != screen || !isAttachmentActive(current)) {
            return;
        }
        current.render(new IScreenEvent() {
            @Override public double getMouseX() { return mouseX; }
            @Override public double getMouseY() { return mouseY; }
            @Override public float getPartialTick() { return partialTick; }
            @Override public GuiGraphics getGuiGraphics() { return graphics; }
        });
    }

    public static boolean beforeCharTyped(GuiEventListener guiEventListener, char codePoint, int modifiers) {
        AttachingScreen<?> current = attachment;
        if (current == null) return false;
        if (!(guiEventListener instanceof Screen screen) || current.screen != screen) return false;
        if (!isAttachmentActive(current)) return false;
        boolean[] canceled = {false};
        current.charTyped(new IScreenEvent() {
            @Override public char getCodePoint() { return codePoint; }
            @Override public int getModifiers() { return modifiers; }
            @Override public void setCanceled(boolean flag) { canceled[0] = flag; }
        });
        return canceled[0];
    }

    public static boolean allowMouseClick(AttachingScreen<?> expected, double mouseX, double mouseY, int button) {
        if (attachment != expected || !isAttachmentActive(expected)) return true;
        boolean[] canceled = {false};
        expected.mouseClicked(new IScreenEvent() {
            @Override public double getMouseX() { return mouseX; }
            @Override public double getMouseY() { return mouseY; }
            @Override public int getMouseButton() { return button; }
            @Override public void setCanceled(boolean flag) { canceled[0] = flag; }
        });
        return !canceled[0];
    }

    public static boolean allowMouseRelease(AttachingScreen<?> expected, double mouseX, double mouseY, int button) {
        if (attachment != expected || !isAttachmentActive(expected)) return true;
        boolean[] canceled = {false};
        expected.mouseReleased(new IScreenEvent() {
            @Override public double getMouseX() { return mouseX; }
            @Override public double getMouseY() { return mouseY; }
            @Override public int getMouseButton() { return button; }
            @Override public void setCanceled(boolean flag) { canceled[0] = flag; }
        });
        return !canceled[0];
    }

    public static boolean allowMouseScroll(AttachingScreen<?> expected, double mouseX, double mouseY, double horizontal, double vertical) {
        if (attachment != expected || !isAttachmentActive(expected)) return true;
        boolean[] canceled = {false};
        expected.mouseScrolled(new IScreenEvent() {
            @Override public double getMouseX() { return mouseX; }
            @Override public double getMouseY() { return mouseY; }
            @Override public double getScrollDeltaY() { return vertical; }
            @Override public double getScrollDeltaX() { return horizontal; }
            @Override public void setCanceled(boolean canceled1) { canceled[0] = canceled1; }
        });
        return !canceled[0];
    }

    public static boolean allowKeyPress(AttachingScreen<?> expected, int keyCode, int scanCode, int modifiers) {
        if (attachment != expected || !isAttachmentActive(expected)) return true;
        boolean[] canceled = {false};
        expected.keyPressed(new IScreenEvent() {
            @Override public int getKeyCode() { return keyCode; }
            @Override public int getScanCode() { return scanCode; }
            @Override public int getModifiers() { return modifiers; }
            @Override public void setCanceled(boolean flag) { canceled[0] = flag; }
        });
        return !canceled[0];
    }

    public static void openAttachment(AbstractContainerScreen<?> container, IScreenEvent addWidget) {
        attachment = new AttachingScreen<>(container);
        attachment.init(addWidget);
    }

    public static void closeAttachment() {
        if (attachment != null) {
            attachment.closed(new IScreenEvent() {});
            attachment = null;
        }
        configToggleButton = null;
    }

    public static boolean isAttachmentActive(@Nullable AttachingScreen<?> expected) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof AbstractContainerScreen<?> c)) {
            attachment = null;
            return false;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            attachment = null;
            return false;
        }
        if (expected == null || expected.screen != screen) return false;
        if (!AttachingScreen.isAttachable(c)) {
            attachment = null;
            return false;
        }
        return true;
    }

    public static IRectangleParam configButtonParam(AbstractContainerScreen<?> container) {
        return ClientConfigs.ATTACHED_MENU_CONFIG.get().adjust(container).configButtonA();
    }

    public static int getContainerLeft(AbstractContainerScreen<?> container) {
        return ((AbstractContainerScreenAccessor) container).endinv$getLeftPos();
    }
}
