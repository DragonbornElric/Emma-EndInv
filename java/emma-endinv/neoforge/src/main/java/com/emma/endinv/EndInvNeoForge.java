package com.emma.endinv;

import com.emma.endinv.event.NeoForgeEvents;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.nbt.ForgeCapabilities;
import com.emma.endinv.nbt.IEndInvUuid;
import com.emma.endinv.nbt.ISyncedConfig;
import com.emma.endinv.network.IPacketDistributor;
import com.emma.endinv.network.NeoForgeNetworking;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.options.ServerConfigs;
import com.emma.endinv.options.config.json.JsonConfigurationHandler;
import com.emma.endinv.platform.ILoaderProvider;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Historical NeoForge 1.20.1 lives on the Forge 47 API/package boundary.
 * The class name remains NeoForge to keep the multiloader artifact naming
 * stable, while all loader imports intentionally use {@code net.minecraftforge}.
 */
@Mod(ModInfo.MOD_ID)
public class EndInvNeoForge extends AbstractModInitializer {

    private final DeferredRegister<Item> items =
            DeferredRegister.create(ForgeRegistries.ITEMS, ModInfo.MOD_ID);
    private final DeferredRegister<MenuType<?>> menus =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ModInfo.MOD_ID);

    public EndInvNeoForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        items.register(modBus);
        menus.register(modBus);
        ForgeCapabilities.register(modBus);
        NeoForgeNetworking.register();
        NeoForgeEvents.register();

        if (FMLEnvironment.dist.isClient()) {
            new EndInvNeoForgeClient(modBus);
        }

        super.init();
    }

    @Override
    protected IPlatform loadOtherPlatformSpecific() {
        return new IPlatform() {
            @Override
            public boolean onItemStackedOn(
                    ItemStack clickedItem,
                    ItemStack carriedItem,
                    Slot slot,
                    ClickAction action,
                    Player player,
                    SlotAccess access
            ) {
                if (ForgeHooks.onItemStackedOn(
                        carriedItem, clickedItem, slot, action, player, access)) {
                    return true;
                }

                var features = player.level().enabledFeatures();
                return (carriedItem.isItemEnabled(features)
                            && carriedItem.overrideStackedOnOther(slot, action, player))
                        || (clickedItem.isItemEnabled(features)
                            && clickedItem.overrideOtherStackedOnMe(
                                    carriedItem, slot, action, player, access));
            }

            @Override
            public boolean isModLoaded(String modid) {
                return ILoaderProvider.get().isModLoaded(modid);
            }
        };
    }

    @Override
    protected IPacketDistributor loadPacketDistributor() {
        return new NeoForgePacketDistributor();
    }

    @Override
    protected void loadServerConfig() {
        new JsonConfigurationHandler(
                ILoaderProvider.get().getConfigDir().resolve("endless_inventory-server.json"),
                ServerConfigs.getConfigs()
        ).load();
    }

    @Override
    protected RegistryCallback<Item> itemReg() {
        return new RegistryCallback<>() {
            @Override
            public <R extends Item> Supplier<R> register(String id, Supplier<R> supplier) {
                return items.register(id, supplier);
            }
        };
    }

    @Override
    protected RegistryCallback<MenuType<?>> menuReg() {
        return new RegistryCallback<>() {
            @Override
            public <R extends MenuType<?>> Supplier<R> register(String id, Supplier<R> supplier) {
                RegistryObject<R> holder = menus.register(id, supplier);
                return holder;
            }
        };
    }

    @Override
    protected Supplier<MenuType<EndlessInventoryMenu>> createEndInvMenuType() {
        return () -> new MenuType<>(EndlessInventoryMenu::createClient, FeatureFlags.DEFAULT_FLAGS);
    }

    @Override
    protected NbtAttachment<UUID> createEndInvUUID(String name) {
        return new NbtAttachment<>() {
            @Override
            @Nullable
            public UUID getWith(Player player) {
                return player.getCapability(ForgeCapabilities.END_INV_UUID)
                        .resolve()
                        .map(IEndInvUuid::getUuid)
                        .orElse(null);
            }

            @Override
            public void setTo(Player player, UUID uuid) {
                player.getCapability(ForgeCapabilities.END_INV_UUID)
                        .ifPresent(capability -> capability.setUuid(uuid));
            }

            @Override
            public UUID computeIfAbsent(Player player) {
                IEndInvUuid capability = player.getCapability(ForgeCapabilities.END_INV_UUID)
                        .orElseThrow(() -> new IllegalStateException(
                                "EndInv UUID capability missing from player " + player.getUUID()));
                UUID uuid = capability.getUuid();
                if (uuid == null || ModInfo.DEFAULT_UUID.equals(uuid)) {
                    uuid = UUID.randomUUID();
                    capability.setUuid(uuid);
                }
                return uuid;
            }
        };
    }

    @Override
    protected NbtAttachment<SyncedConfig> createSyncedConfig(String name) {
        return new NbtAttachment<>() {
            @Override
            @Nullable
            public SyncedConfig getWith(Player player) {
                return player.getCapability(ForgeCapabilities.END_INV_CONFIG)
                        .resolve()
                        .map(ISyncedConfig::getSyncedConfig)
                        .orElse(null);
            }

            @Override
            public void setTo(Player player, SyncedConfig syncedConfig) {
                player.getCapability(ForgeCapabilities.END_INV_CONFIG)
                        .ifPresent(capability -> capability.setSyncedConfig(syncedConfig));
            }

            @Override
            public SyncedConfig computeIfAbsent(Player player) {
                ISyncedConfig capability = player.getCapability(ForgeCapabilities.END_INV_CONFIG)
                        .orElseThrow(() -> new IllegalStateException(
                                "EndInv settings capability missing from player " + player.getUUID()));
                SyncedConfig config = capability.getSyncedConfig();
                if (config == null) {
                    config = SyncedConfig.DEFAULT;
                    capability.setSyncedConfig(config);
                }
                return config;
            }
        };
    }
}
