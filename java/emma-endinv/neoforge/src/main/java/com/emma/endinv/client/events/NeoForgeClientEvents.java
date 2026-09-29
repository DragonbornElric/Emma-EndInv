package com.emma.endinv.client.events;

import com.emma.endinv.ModInfo;
import com.emma.endinv.AbstractClientModInitializer;
import com.emma.endinv.client.KeyMappings;
import net.minecraft.client.KeyMapping;
import com.emma.endinv.client.ScreenDebug;
import com.emma.endinv.client.event.AutoPickTipper;
import com.emma.endinv.client.gui.AttachingScreen;
import com.emma.endinv.client.gui.EndlessInventoryScreen;
import com.emma.endinv.client.gui.IScreenEvent;
import com.emma.endinv.mixin.ScreenAccessor;
import com.emma.endinv.network.payloads.toServer.OpenEndInvPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class NeoForgeClientEvents {

    private static boolean charTypedRegistered;

    private NeoForgeClientEvents() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::onRenderGuiOverlay);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::onScreenInit);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::onScreenClose);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::onMouseClick);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::onMouseRelease);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::onMouseDrag);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::onKeyPress);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEvents::onScreenRender);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        KeyMapping openMenu = AbstractClientModInitializer.ENDINV_CLIENT != null
                ? AbstractClientModInitializer.ENDINV_CLIENT.KEY_MAPPING_MAP.get(KeyMappings.OPEN_MENU)
                : null;
        if (openMenu != null) {
            while (openMenu.consumeClick()) {
                // Same as Fabric: open a new EndInv screen (the no-arg payload only attaches to the open menu).
                com.emma.endinv.client.ClientModInfo.sendOpenMenu();
            }
        }
    }

    private static void onRenderGuiOverlay(RenderGuiLayerEvent.Post event) {
        AutoPickTipper.onRenderGui(event.getGuiGraphics());
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)) return;
        if (container instanceof EndlessInventoryScreen) return;

        if (!charTypedRegistered) {
            ScreenCharTypedEvents.register(ScreenAttachment::beforeCharTyped);
            charTypedRegistered = true;
        }

        ScreenAttachment.closeAttachment();
        ScreenAttachment.lastContainerLeft = ScreenAttachment.getContainerLeft(container);

        Button cfgBtn = AttachingScreen.configButton(
                container,
                ScreenAttachment.configButtonParam(container),
                () -> {
                    if (ScreenAttachment.attachment == null) {
                        // Same as Fabric: open a new EndInv screen (the no-arg payload only attaches to the open menu).
                com.emma.endinv.client.ClientModInfo.sendOpenMenu();
                        ScreenAttachment.openAttachment(container, new IScreenEvent() {
                            @Override
                            public void addListener(AbstractWidget widget) {
                                event.addListener(widget);
                            }
                        });
                    }
                },
                () -> ScreenAttachment.closeAttachment()
        );
        event.addListener(cfgBtn);
        ScreenAttachment.configToggleButton = cfgBtn;

        if (Minecraft.getInstance().player == null) {
            ScreenAttachment.attachment = null;
            return;
        }

        if (AttachingScreen.isAttachable(container)) {
            ModInfo.getPacketDistributor().sendToServer(new OpenEndInvPayload());
            ScreenAttachment.openAttachment(container, new IScreenEvent() {
                @Override
                public void addListener(AbstractWidget widget) {
                    event.addListener(widget);
                }
            });
        }
    }

    private static void onScreenClose(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            ScreenAttachment.closeAttachment();
        }
    }

    private static void onScreenRender(ScreenEvent.Render.Post event) {
        AttachingScreen<?> current = ScreenAttachment.attachment;
        if (current == null || !(event.getScreen() instanceof AbstractContainerScreen<?> container)) return;
        if (!ScreenAttachment.isAttachmentActive(current)) return;

        int currentLeft = ScreenAttachment.getContainerLeft(container);
        if (currentLeft != ScreenAttachment.lastContainerLeft) {
            ScreenAttachment.lastContainerLeft = currentLeft;
            current.closed(new IScreenEvent() {});
            ScreenAttachment.openAttachment(container, new IScreenEvent() {
                @Override
                public void addListener(AbstractWidget widget) {
                    ((ScreenAccessor) container).endinv$invokeAddRenderableWidget(widget);
                }
            });
            current = ScreenAttachment.attachment;
            if (current == null) return;
        }

        final AttachingScreen<?> toRender = current;
        toRender.renderPre(new IScreenEvent() {
            @Override public double getMouseX() { return event.getMouseX(); }
            @Override public double getMouseY() { return event.getMouseY(); }
            @Override public float getPartialTick() { return event.getPartialTick(); }
            @Override public net.minecraft.client.gui.GuiGraphicsExtractor getGuiGraphicsExtractor() { return event.getGuiGraphics(); }
        });
        toRender.render(new IScreenEvent() {
            @Override public double getMouseX() { return event.getMouseX(); }
            @Override public double getMouseY() { return event.getMouseY(); }
            @Override public float getPartialTick() { return event.getPartialTick(); }
            @Override public net.minecraft.client.gui.GuiGraphicsExtractor getGuiGraphicsExtractor() { return event.getGuiGraphics(); }
        });
        ScreenDebug.debugInfo(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    private static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!ScreenAttachment.allowMouseClick(ScreenAttachment.attachment, event.getMouseButtonEvent())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!ScreenAttachment.allowMouseRelease(ScreenAttachment.attachment, event.getMouseButtonEvent())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!ScreenAttachment.allowMouseScroll(ScreenAttachment.attachment,
                event.getMouseX(), event.getMouseY(),
                event.getScrollDeltaX(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)) return;
        if (ScreenAttachment.handleMouseDrag(container, event.getMouseButtonEvent(),
                event.getDragX(), event.getDragY())) {
            event.setCanceled(true);
        }
    }

    private static void onKeyPress(ScreenEvent.KeyPressed.Pre event) {
        if (!ScreenAttachment.allowKeyPress(ScreenAttachment.attachment, event.getKeyEvent())) {
            event.setCanceled(true);
        }
    }
}
