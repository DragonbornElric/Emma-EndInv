package com.emma.endinv;

import com.emma.endinv.client.IContainerScreenHelper;
import com.emma.endinv.client.IInputHandler;
import com.emma.endinv.client.KeyMappings;
import com.emma.endinv.client.events.NeoForgeClientEvents;
import com.emma.endinv.client.events.ScreenCharTypedEvents;
import com.emma.endinv.client.option.ClientConfigs;
import com.emma.endinv.mixin.AbstractContainerScreenAccessor;
import com.emma.endinv.options.config.json.JsonConfigurationHandler;
import com.emma.endinv.platform.ILoaderProvider;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import static com.emma.endinv.client.KeyMappings.*;

public class EndInvNeoForgeClient extends AbstractClientModInitializer {

    private static JsonConfigurationHandler CLIENT_CONFIGS;

    public EndInvNeoForgeClient(IEventBus modBus) {
        super();
        AbstractClientModInitializer.ENDINV_CLIENT = this;

        modBus.addListener(this::onRegisterKeyMappings);
        modBus.addListener((FMLClientSetupEvent e) -> onClientSetup());

        NeoForgeClientEvents.register(modBus);

        // Register the char-typed event bridge (loader-neutral dispatcher)
        ScreenCharTypedEvents.register((listener, event) -> {
            if (!(listener instanceof net.minecraft.client.gui.screens.Screen screen)) return false;
            return false; // NeoForge intercepts via ScreenEvent.CharacterTyped — handled in NeoForgeClientEvents
        });
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KEY_MAPPING_MAP.get(OPEN_MENU));
        event.register(KEY_MAPPING_MAP.get(QUICK_MOVE));
        event.register(KEY_MAPPING_MAP.get(STAR_ITEM_ALTER));
    }

    private void onClientSetup() {
        CLIENT_CONFIGS = new JsonConfigurationHandler(
                ILoaderProvider.get().getConfigDir().resolve("endless_inventory-client.json"),
                ClientConfigs.getConfigs()
        );
        CLIENT_CONFIGS.load();

        // Register menu → screen mapping (vanilla call, loader-neutral)
        com.emma.endinv.client.events.MenuScreenReg.register();
    }

    @Override
    protected void regKeyParam(KeyMappings.KeyParam key) {
        KeyMapping mapping = new KeyMapping(key.key(), key.type(), key.keyCode(), key.category());
        KEY_MAPPING_MAP.put(key, mapping);
    }

    @Override
    protected IInputHandler getInputHandler() {
        return new IInputHandler() {
            @Override
            public boolean isActiveAndMatches(KeyParam keyParam, InputWithModifiers input) {
                AbstractClientModInitializer modClient = AbstractClientModInitializer.ENDINV_CLIENT;
                if (modClient == null) throw new IllegalStateException("Client mod not initialized");
                if (!keyParam.condition().isActive()) return false;
                if (!keyParam.modifier().matchesModifier(input)) return false;
                if (input instanceof MouseButtonEvent buttonEvent
                        && keyParam.keyCode() == buttonEvent.button()
                        && keyParam.modifier().matchesModifier(input)) return true;
                var reg = modClient.KEY_MAPPING_MAP.get(keyParam);
                return switch (input) {
                    case KeyEvent keyEvent -> reg.matches(keyEvent);
                    case MouseButtonEvent buttonEvent -> reg.matchesMouse(buttonEvent);
                    default -> false;
                };
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
}
