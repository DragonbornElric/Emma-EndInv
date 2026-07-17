package com.emma.endinv.client.events;

import com.emma.endinv.AbstractClientModInitializer;
import com.emma.endinv.ModInfo;
import com.emma.endinv.client.KeyMappings;
import com.emma.endinv.client.ScreenDebug;
import com.emma.endinv.client.event.AutoPickTipper;
import com.emma.endinv.client.gui.AttachingScreen;
import com.emma.endinv.client.gui.EndlessInventoryScreen;
import com.emma.endinv.client.gui.IScreenEvent;
import com.emma.endinv.mixin.ScreenAccessor;
import com.emma.endinv.network.payloads.toServer.OpenEndInvPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class NeoForgeClientEvents {

    private NeoForgeClientEvents() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(NeoForgeClientEvents::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeClientEvents::onRenderGui);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeClientEvents::onScreenInit);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeClientEvents::onScreenClose);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeClientEvents::onMouseClick);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeClientEvents::onMouseRelease);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeClientEvents::onMouseScroll);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeClientEvents::onKeyPress);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeClientEvents::onScreenRender);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        KeyMapping openMenu = AbstractClientModInitializer.ENDINV_CLIENT != null
                ? AbstractClientModInitializer.ENDINV_CLIENT.KEY_MAPPING_MAP
                        .get(KeyMappings.OPEN_MENU)
                : null;
        if (openMenu == null) return;

        while (openMenu.consumeClick()) {
            ModInfo.getPacketDistributor().sendToServer(new OpenEndInvPayload());
        }
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        AutoPickTipper.onRenderGui(event.getGuiGraphics());
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)) return;
        if (container instanceof EndlessInventoryScreen) return;

        ScreenAttachment.closeAttachment();
        ScreenAttachment.lastContainerLeft =
                ScreenAttachment.getContainerLeft(container);

        Button configButton = AttachingScreen.configButton(
                container,
                ScreenAttachment.configButtonParam(container),
                () -> {
                    if (ScreenAttachment.attachment == null) {
                        ModInfo.getPacketDistributor()
                                .sendToServer(new OpenEndInvPayload());
                        ScreenAttachment.openAttachment(container, new IScreenEvent() {
                            @Override
                            public void addListener(AbstractWidget widget) {
                                event.addListener(widget);
                            }
                        });
                    }
                },
                ScreenAttachment::closeAttachment
        );
        event.addListener(configButton);
        ScreenAttachment.configToggleButton = configButton;

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
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)) {
            return;
        }

        AttachingScreen<?> current = ScreenAttachment.attachment;
        if (current == null || !ScreenAttachment.isAttachmentActive(current)) {
            return;
        }

        int currentLeft = ScreenAttachment.getContainerLeft(container);
        if (currentLeft != ScreenAttachment.lastContainerLeft) {
            ScreenAttachment.lastContainerLeft = currentLeft;
            current.closed(new IScreenEvent() {});
            ScreenAttachment.openAttachment(container, new IScreenEvent() {
                @Override
                public void addListener(AbstractWidget widget) {
                    ((ScreenAccessor) container)
                            .endinv$invokeAddRenderableWidget(widget);
                }
            });
        }

        ScreenAttachment.onRenderAfterBackground(
                container,
                event.getGuiGraphics(),
                event.getMouseX(),
                event.getMouseY(),
                event.getPartialTick()
        );
        ScreenAttachment.onRenderPost(
                container,
                event.getGuiGraphics(),
                event.getMouseX(),
                event.getMouseY(),
                event.getPartialTick()
        );
        ScreenDebug.debugInfo(
                event.getScreen(),
                event.getGuiGraphics(),
                event.getMouseX(),
                event.getMouseY()
        );
    }

    private static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!ScreenAttachment.allowMouseClick(
                ScreenAttachment.attachment,
                event.getMouseX(),
                event.getMouseY(),
                event.getButton())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!ScreenAttachment.allowMouseRelease(
                ScreenAttachment.attachment,
                event.getMouseX(),
                event.getMouseY(),
                event.getButton())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!ScreenAttachment.allowMouseScroll(
                ScreenAttachment.attachment,
                event.getMouseX(),
                event.getMouseY(),
                0.0,
                event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    private static void onKeyPress(ScreenEvent.KeyPressed.Pre event) {
        if (!ScreenAttachment.allowKeyPress(
                ScreenAttachment.attachment,
                event.getKeyCode(),
                event.getScanCode(),
                event.getModifiers())) {
            event.setCanceled(true);
        }
    }
}
