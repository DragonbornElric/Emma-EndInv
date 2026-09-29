package com.emma.endinv;

import com.emma.endinv.event.NeoForgeEvents;
import com.emma.endinv.network.NeoForgeNetworking;
import com.emma.endinv.network.IPacketDistributor;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.options.ServerConfigs;
import com.emma.endinv.options.config.json.JsonConfigurationHandler;
import com.emma.endinv.platform.ILoaderProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.emma.endinv.menu.EndlessInventoryMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.UUID;
import java.util.function.Supplier;

@Mod(ModInfo.MOD_ID)
public class EndInvNeoForge extends AbstractModInitializer {

    public static Supplier<AttachmentType<UUID>> ENDINV_UUID;
    public static Supplier<AttachmentType<SyncedConfig>> SYNCED_CONFIG;

    private final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, ModInfo.MOD_ID);
    private final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, ModInfo.MOD_ID);
    private final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ModInfo.LEGACY_ID); // saved in player data: legacy id kept

    public EndInvNeoForge(IEventBus eventBus) {
        ITEMS.register(eventBus);
        MENUS.register(eventBus);
        ATTACHMENTS.register(eventBus);
        // Stacks saved under the upstream id (endless_inventory:<id>) load as these items.
        for (String id : new String[]{"endinv_accessor", "screen_debugger"}) {
            BuiltInRegistries.ITEM.addAlias(Identifier.fromNamespaceAndPath(ModInfo.LEGACY_ID, id), Identifier.fromNamespaceAndPath(ModInfo.MOD_ID, id));
        }

        // Register attachment types via DeferredRegister
        ENDINV_UUID = ATTACHMENTS.register("endinv_uuid", () ->
                AttachmentType.<UUID>builder(UUID::randomUUID)
                        .serialize(net.minecraft.core.UUIDUtil.CODEC.fieldOf("id"))
                        .copyOnDeath()
                        .build()
        );
        SYNCED_CONFIG = ATTACHMENTS.register("endinv_settings", () ->
                AttachmentType.<SyncedConfig>builder(() -> SyncedConfig.DEFAULT)
                        .serialize(SyncedConfig.CODEC.fieldOf("config"))
                        .copyOnDeath()
                        .build()
        );

        NeoForgeNetworking.register(eventBus);
        NeoForgeEvents.register(eventBus);

        if (FMLEnvironment.getDist().isClient()) {
            new EndInvNeoForgeClient(eventBus);
        }

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
        return new NeoForgePacketDistributor();
    }

    @Override
    protected void loadServerConfig() {
        new JsonConfigurationHandler(
                com.emma.endinv.options.config.ConfigFiles.resolve(ILoaderProvider.get().getConfigDir(), "server.json"),
                ServerConfigs.getConfigs()
        ).load();
    }

    @Override
    protected RegistryCallback<Item> itemReg() {
        return new RegistryCallback<>() {
            @Override
            public <R extends Item> Supplier<R> register(String id, Supplier<R> supplier) {
                var holder = ITEMS.register(id, supplier);
                return holder;
            }
        };
    }

    @Override
    protected RegistryCallback<MenuType<?>> menuReg() {
        return new RegistryCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <R extends MenuType<?>> Supplier<R> register(String id, Supplier<R> supplier) {
                var holder = (net.neoforged.neoforge.registries.DeferredHolder<MenuType<?>, R>)
                        MENUS.register(id, supplier);
                return holder;
            }
        };
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected Supplier<MenuType<EndlessInventoryMenu>> createEndInvMenuType() {
        return () -> {
            net.minecraft.world.inventory.MenuType raw = new net.minecraft.world.inventory.MenuType(
                    (id, inventory) -> {
                        try {
                            var cls = Class.forName("com.emma.endinv.menu.EndlessInventoryMenu");
                            var m = cls.getMethod("createClient", int.class, net.minecraft.world.entity.player.Inventory.class);
                            return (net.minecraft.world.inventory.AbstractContainerMenu) m.invoke(null, id, inventory);
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
            @Override
            public UUID getWith(Player player) {
                if (!player.hasData(ENDINV_UUID.get())) return null;
                return player.getData(ENDINV_UUID.get());
            }

            @Override
            public void setTo(Player player, UUID uuid) {
                player.setData(ENDINV_UUID.get(), uuid);
            }

            @Override
            public UUID computeIfAbsent(Player player) {
                return player.getData(ENDINV_UUID.get());
            }
        };
    }

    @Override
    protected NbtAttachment<SyncedConfig> createSyncedConfig(String name) {
        return new NbtAttachment<>() {
            @Override
            public SyncedConfig getWith(Player player) {
                if (!player.hasData(SYNCED_CONFIG.get())) return null;
                return player.getData(SYNCED_CONFIG.get());
            }

            @Override
            public void setTo(Player player, SyncedConfig syncedConfig) {
                player.setData(SYNCED_CONFIG.get(), syncedConfig);
            }

            @Override
            public SyncedConfig computeIfAbsent(Player player) {
                return player.getData(SYNCED_CONFIG.get());
            }
        };
    }
}
