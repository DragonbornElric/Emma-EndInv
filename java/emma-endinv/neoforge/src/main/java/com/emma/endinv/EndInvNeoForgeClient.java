package com.emma.endinv;

import com.emma.endinv.client.IContainerScreenHelper;
import com.emma.endinv.client.IInputHandler;
import com.emma.endinv.client.KeyMappings;
import com.emma.endinv.client.events.MenuScreenReg;
import com.emma.endinv.client.events.NeoForgeClientEvents;
import com.emma.endinv.client.events.ScreenAttachment;
import com.emma.endinv.client.events.ScreenCharTypedEvents;
import com.emma.endinv.client.option.ClientConfigs;
import com.emma.endinv.mixin.AbstractContainerScreenAccessor;
import com.emma.endinv.network.NeoForgeNetworking;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.options.config.json.JsonConfigurationHandler;
import com.emma.endinv.platform.ILoaderProvider;
import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import static com.emma.endinv.client.KeyMappings.OPEN_MENU;
import static com.emma.endinv.client.KeyMappings.QUICK_MOVE;
import static com.emma.endinv.client.KeyMappings.STAR_ITEM;
import static com.emma.endinv.client.KeyMappings.STAR_ITEM_ALTER;

public class EndInvNeoForgeClient extends AbstractClientModInitializer {

    private static JsonConfigurationHandler clientConfigs;

    public EndInvNeoForgeClient(IEventBus modBus) {
        super();
        AbstractClientModInitializer.ENDINV_CLIENT = this;

        modBus.addListener(this::onRegisterKeyMappings);
        modBus.addListener(this::onClientSetup);
        NeoForgeClientEvents.register();

        // The common mixin owns character dispatch on all loaders.
        ScreenCharTypedEvents.register(ScreenAttachment::beforeCharTyped);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KEY_MAPPING_MAP.get(OPEN_MENU));
        event.register(KEY_MAPPING_MAP.get(QUICK_MOVE));

        /*
         * JEI reserves A in its recipe UI. Keep the public STAR_ITEM semantic
         * key while transparently binding it to the conflict-free F13 mapping.
         */
        KeyMapping starMapping;
        if (ModList.get().isLoaded("jei")) {
            starMapping = KEY_MAPPING_MAP.get(STAR_ITEM_ALTER);
            KEY_MAPPING_MAP.put(STAR_ITEM, starMapping);
        } else {
            starMapping = KEY_MAPPING_MAP.get(STAR_ITEM);
        }
        event.register(starMapping);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            clientConfigs = new JsonConfigurationHandler(
                    ILoaderProvider.get().getConfigDir()
                            .resolve("endless_inventory-client.json"),
                    ClientConfigs.getConfigs()
            );
            clientConfigs.load();
            MenuScreenReg.register();
        });
    }

    @Override
    protected void regKeyParam(KeyMappings.KeyParam key) {
        KeyConflictContext conflictContext =
                key.condition() == KeyMappings.ActiveCondition.GUI
                        ? KeyConflictContext.GUI
                        : KeyConflictContext.IN_GAME;
        KeyModifier modifier =
                key.modifier() == KeyMappings.Modifier.CTRL
                        ? KeyModifier.CONTROL
                        : KeyModifier.NONE;
        KeyMapping mapping = new KeyMapping(
                key.key(),
                conflictContext,
                modifier,
                key.type(),
                key.keyCode(),
                key.category()
        );
        KEY_MAPPING_MAP.put(key, mapping);
    }

    @Override
    protected IInputHandler getInputHandler() {
        return new IInputHandler() {
            @Override
            public boolean isActiveAndMatches(
                    KeyMappings.KeyParam keyParam,
                    InputConstants.Key input
            ) {
                KeyMapping mapping = KEY_MAPPING_MAP.get(keyParam);
                return mapping != null && mapping.isActiveAndMatches(input);
            }
        };
    }

    @Override
    protected IContainerScreenHelper getScreenHelper() {
        return new IContainerScreenHelper() {
            @Override
            public int getGuiLeft(AbstractContainerScreen<?> screen) {
                return ((AbstractContainerScreenAccessor) screen).endinv$getLeftPos();
            }

            @Override
            public int getGuiTop(AbstractContainerScreen<?> screen) {
                return ((AbstractContainerScreenAccessor) screen).endinv$getTopPos();
            }

            @Override
            public int getGuiXSize(AbstractContainerScreen<?> screen) {
                return ((AbstractContainerScreenAccessor) screen).endinv$getImageWidth();
            }

            @Override
            public int getGuiYSize(AbstractContainerScreen<?> screen) {
                return ((AbstractContainerScreenAccessor) screen).endinv$getImageHeight();
            }
        };
    }

    public static void handlePayload(ModPacketPayload payload) {
        payload.handle(() -> Minecraft.getInstance().player);
    }

    public static void sendToServer(ModPacketPayload payload) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null) return;
        if (!NeoForgeNetworking.canSend(listener.getConnection(), payload.payloadId())) {
            return;
        }

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            payload.write(buffer);
            listener.send(
                    new ServerboundCustomPayloadPacket(payload.payloadId(), buffer));
        } catch (RuntimeException exception) {
            buffer.release();
            throw exception;
        }
    }
}
