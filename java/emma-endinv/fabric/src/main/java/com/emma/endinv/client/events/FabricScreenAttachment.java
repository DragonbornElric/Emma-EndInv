package com.emma.endinv.client.events;

import com.emma.endinv.ModInfo;
import com.emma.endinv.client.gui.AttachingScreen;
import com.emma.endinv.client.gui.EndlessInventoryScreen;
import com.emma.endinv.client.gui.IScreenEvent;
import com.emma.endinv.mixin.ScreenAccessor;
import com.emma.endinv.network.payloads.toServer.OpenEndInvPayload;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class FabricScreenAttachment {

    private static boolean registered;

    private FabricScreenAttachment() {}

    public static void register() {
        if (registered) return;
        registered = true;

        ScreenCharTypedEvents.register(ScreenAttachment::beforeCharTyped);

        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                ScreenAttachment.configButtonParam((AbstractContainerScreen<?>) screen);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || screen instanceof EndlessInventoryScreen) {
                return;
            }

            Button cfgBtn = AttachingScreen.configButton(
                    screen,
                    ScreenAttachment.configButtonParam(container),
                    () -> {
                        if (ScreenAttachment.attachment == null) {
                            ModInfo.getPacketDistributor().sendToServer(new OpenEndInvPayload());
                            ScreenAttachment.openAttachment(container, new IScreenEvent() {
                                @Override
                                public void addListener(AbstractWidget widget) {
                                    ((ScreenAccessor) screen).endinv$invokeAddRenderableWidget(widget);
                                }
                            });
                        }
                    },
                    () -> ScreenAttachment.closeAttachment()
            );
            ((ScreenAccessor) screen).endinv$invokeAddRenderableWidget(cfgBtn);
            ScreenAttachment.configToggleButton = cfgBtn;

            if (client.player == null) {
                ScreenAttachment.attachment = null;
                return;
            }

            if (AttachingScreen.isAttachable(container)) {
                if (ScreenAttachment.attachment == null) {
                    ModInfo.getPacketDistributor().sendToServer(new OpenEndInvPayload());
                    ScreenAttachment.openAttachment(container, new IScreenEvent() {
                        @Override
                        public void addListener(AbstractWidget widget) {
                            ((ScreenAccessor) screen).endinv$invokeAddRenderableWidget(widget);
                        }
                    });
                }
            }

            ScreenEvents.remove(screen).register(s -> {
                ScreenAttachment.closeAttachment();
            });

            ScreenAttachment.lastContainerLeft = ScreenAttachment.getContainerLeft(container);

            ScreenEvents.beforeRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                AttachingScreen<?> current = ScreenAttachment.attachment;
                if (current == null || current.getScreen() != s || !ScreenAttachment.isAttachmentActive(current)) {
                    return;
                }
                int currentLeft = ScreenAttachment.getContainerLeft(container);
                if (currentLeft != ScreenAttachment.lastContainerLeft) {
                    ScreenAttachment.lastContainerLeft = currentLeft;
                    current.closed(new IScreenEvent() {});
                    ScreenAttachment.openAttachment(container, new IScreenEvent() {
                        @Override
                        public void addListener(AbstractWidget widget) {
                            ((ScreenAccessor) s).endinv$invokeAddRenderableWidget(widget);
                        }
                    });
                    current = ScreenAttachment.attachment;
                }
                if (current == null) return;
                final AttachingScreen<?> toRender = current;
                toRender.renderPre(new IScreenEvent() {
                    @Override public double getMouseX() { return mouseX; }
                    @Override public double getMouseY() { return mouseY; }
                    @Override public float getPartialTick() { return delta; }
                    @Override public net.minecraft.client.gui.GuiGraphics getGuiGraphics() { return graphics; }
                });
            });

            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                AttachingScreen<?> current = ScreenAttachment.attachment;
                if (current == null || current.getScreen() != s || !ScreenAttachment.isAttachmentActive(current)) {
                    return;
                }
                current.render(new IScreenEvent() {
                    @Override public double getMouseX() { return mouseX; }
                    @Override public double getMouseY() { return mouseY; }
                    @Override public float getPartialTick() { return delta; }
                    @Override public net.minecraft.client.gui.GuiGraphics getGuiGraphics() { return graphics; }
                });
            });

            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) ->
                    ScreenAttachment.allowMouseClick(ScreenAttachment.attachment, mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, mouseX, mouseY, button) ->
                    ScreenAttachment.allowMouseRelease(ScreenAttachment.attachment, mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) ->
                    ScreenAttachment.allowMouseScroll(ScreenAttachment.attachment, mouseX, mouseY, horizontal, vertical));
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyCode, scanCode, modifiers) ->
                    ScreenAttachment.allowKeyPress(ScreenAttachment.attachment, keyCode, scanCode, modifiers));
        });
    }
}
