package com.emma.endinv;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.IPlatform;
import com.emma.endinv.NbtAttachment;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.nbt.FabricNbtStorage;
import com.emma.endinv.network.IPacketDistributor;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.options.ServerConfigs;
import com.emma.endinv.options.config.json.JsonConfigurationHandler;
import com.emma.endinv.event.FabricEvents;
import com.emma.endinv.network.FabricServerNetworking;
import com.emma.endinv.platform.ILoaderProvider;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public class ModInit extends AbstractModInitializer implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricServerNetworking.init();
        FabricEvents.init();
        super.init();
    }

    @Override
    protected IPlatform loadOtherPlatformSpecific() {
        return new IPlatform() {
            @Override
            public boolean onItemStackedOn(ItemStack clickedItem, ItemStack carriedItem, Slot slot, ClickAction action, Player player, SlotAccess access) {
                var features = player.level().enabledFeatures();
                return (carriedItem.isItemEnabled(features) && carriedItem.overrideStackedOnOther(slot, action, player))
                        || (clickedItem.isItemEnabled(features) && clickedItem.overrideOtherStackedOnMe(carriedItem, slot, action, player, access));
            }

            @Override
            public boolean isModLoaded(String modid) {
                return ILoaderProvider.get().isModLoaded(modid);
            }
        };
    }

    @Override
    protected IPacketDistributor loadPacketDistributor() {
        return new FabricPacketDistributor();
    }

    @Override
    protected void loadServerConfig() {
        new JsonConfigurationHandler(ILoaderProvider.get().getConfigDir().resolve("endless_inventory-server.json"), ServerConfigs.getConfigs())
                .load();
    }

    @Override
    protected RegistryCallback<Item> itemReg() {
        return new RegistryCallback<>() {
            @Override
            public <R extends Item> Supplier<R> register(String id, Supplier<R> supplier) {
                ResourceLocation location = withModLocation(id);
                R item = supplier.get();
                R registered = net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, location, item);
                return () -> registered;
            }
        };
    }

    @Override
    protected RegistryCallback<MenuType<?>> menuReg() {
        return new RegistryCallback<>() {
            @Override
            public <R extends MenuType<?>> Supplier<R> register(String id, Supplier<R> supplier) {
                ResourceLocation location = withModLocation(id);
                R type = supplier.get();
                R registered = net.minecraft.core.Registry.register(BuiltInRegistries.MENU, location, type);
                return () -> registered;
            }
        };
    }

    @Override @SuppressWarnings("unchecked")
    protected Supplier<MenuType<EndlessInventoryMenu>> createEndInvMenuType() {
        return () -> {
            @SuppressWarnings({"rawtypes", "unchecked"})
            net.minecraft.world.inventory.MenuType raw = new net.minecraft.world.inventory.MenuType(
                    (net.minecraft.world.inventory.MenuType.MenuSupplier) (id, inventory) -> {
                        try {
                            Class<?> cls = Class.forName("com.emma.endinv.menu.EndlessInventoryMenu");
                            java.lang.reflect.Method m = cls.getMethod("createClient", int.class, net.minecraft.world.entity.player.Inventory.class);
                            Object menu = m.invoke(null, id, inventory);
                            return (net.minecraft.world.inventory.AbstractContainerMenu) menu;
                        } catch (Throwable t) {
                            throw new RuntimeException(t);
                        }
                    },
                    FeatureFlags.DEFAULT_FLAGS
            );
            return (MenuType<EndlessInventoryMenu>) raw;
        };
    }

    @Override
    protected NbtAttachment<UUID> createEndInvUUID(String name) {
        return new NbtAttachment<>() {
            @Override@Nullable
            public UUID getWith(Player player) {
                return FabricNbtStorage.getUuid(player);
            }

            @Override
            public void setTo(Player player, UUID uuid) {
                FabricNbtStorage.setUuid(player, uuid);
            }

            @Override
            public UUID computeIfAbsent(Player player) {
                UUID uuid = FabricNbtStorage.getUuid(player);
                if (uuid == null) {
                    uuid = UUID.randomUUID();
                    FabricNbtStorage.setUuid(player, uuid);
                }
                return uuid;
            }
        };
    }

    @Override
    protected NbtAttachment<SyncedConfig> createSyncedConfig(String name) {
        return new NbtAttachment<>() {
            @Override@Nullable
            public SyncedConfig getWith(Player player) {
                if (!FabricNbtStorage.hasCompound(player, name)) {
                    return null;
                }
                CompoundTag compound = FabricNbtStorage.getCompound(player, name).copy();
                return SyncedConfig.CODEC.parse(NbtOps.INSTANCE, compound)
                        .resultOrPartial(ModInit::logCodecError)
                        .orElse(null);
            }

            @Override
            public void setTo(Player player, SyncedConfig syncedConfig) {
                SyncedConfig.CODEC.encodeStart(NbtOps.INSTANCE, syncedConfig)
                        .resultOrPartial(ModInit::logCodecError)
                        .ifPresent(tag -> {
                            if (tag instanceof CompoundTag compound) {
                                FabricNbtStorage.setCompound(player, name, compound);
                            }
                        });
            }

            @Override
            public SyncedConfig computeIfAbsent(Player player) {
                SyncedConfig config = getWith(player);
                if (config == null) {
                    config = SyncedConfig.DEFAULT;
                    setTo(player, config);
                }
                return config;
            }
        };
    }

    private static void logCodecError(String message) {
        org.slf4j.LoggerFactory.getLogger(ModInit.class)
                .warn("Failed to process synced config: {}", message);
    }
}
