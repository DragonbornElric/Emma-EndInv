package com.emma.endinv.folia;

import com.emma.endinv.ModRegistries;
import com.emma.endinv.ModInfo;
import com.emma.endinv.NbtAttachment;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.network.payloads.SyncedConfig;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.UUID;

/**
 * Initialises {@link ModRegistries.Menus} and {@link ModRegistries.NbtAttachments}
 * for the Folia loader. Kept separate from common's ModRegistries to avoid polluting
 * it with reflection hacks that are Paper/Folia-specific.
 */
public final class FoliaMenuRegistry {

    private FoliaMenuRegistry() {}

    public static void init(JavaPlugin plugin) {
        initMenuType();
        initAttachments(plugin);
    }

    // ── MenuType registration ────────────────────────────────────────────────

    private static void initMenuType() {
        ResourceLocation id = new ResourceLocation(ModInfo.MOD_ID, "endinv_menu");
        if (BuiltInRegistries.MENU.containsKey(id)) {
            @SuppressWarnings("unchecked")
            MenuType<EndlessInventoryMenu> existing =
                    (MenuType<EndlessInventoryMenu>) BuiltInRegistries.MENU.get(id);
            ModRegistries.Menus.endinvMenuType = () -> existing;
            return;
        }

        // BuiltInRegistries.MENU is frozen when Paper plugins enable.
        // Temporarily unfreeze and always restore the prior state, including
        // when registration fails.
        var registry = (net.minecraft.core.MappedRegistry<?>) BuiltInRegistries.MENU;
        Field frozenField;
        boolean wasFrozen;
        try {
            frozenField = findFrozenField();
            frozenField.setAccessible(true);
            wasFrozen = frozenField.getBoolean(registry);
            frozenField.setBoolean(registry, false);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to unfreeze the menu registry", e);
        }

        try {
            MenuType<EndlessInventoryMenu> type = Registry.register(
                    BuiltInRegistries.MENU,
                    id,
                    createMenuType()
            );
            ModRegistries.Menus.endinvMenuType = () -> type;
        } finally {
            try {
                frozenField.setBoolean(registry, wasFrozen);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to restore the menu registry freeze state", e);
            }
        }
    }

    /**
     * Paper's production reobfuscation remaps the MappedRegistry class reference
     * but cannot remap a field name embedded in a reflection string. Minecraft
     * 1.20.1 has exactly one non-static boolean field on MappedRegistry (the
     * registry's frozen flag), so resolve it by shape instead of by mapped name.
     */
    private static Field findFrozenField() {
        Field[] candidates = Arrays.stream(net.minecraft.core.MappedRegistry.class.getDeclaredFields())
                .filter(field -> field.getType() == boolean.class)
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toArray(Field[]::new);
        if (candidates.length != 1) {
            throw new IllegalStateException(
                    "Expected one MappedRegistry instance boolean field, found "
                            + candidates.length
            );
        }
        return candidates[0];
    }

    /**
     * Paper's 1.20.1 Mojang-mapped MenuType constructor and MenuSupplier are
     * private even though mod-loader mappings expose the equivalent API.
     * Resolve the constructor by shape rather than relying on declaration
     * order or on an arbitrary nested interface.
     */
    @SuppressWarnings("unchecked")
    private static MenuType<EndlessInventoryMenu> createMenuType() {
        try {
            Constructor<?> ctor = Arrays.stream(MenuType.class.getDeclaredConstructors())
                    .filter(candidate -> candidate.getParameterCount() == 2)
                    .filter(candidate ->
                            candidate.getParameterTypes()[1].isAssignableFrom(
                                    FeatureFlags.DEFAULT_FLAGS.getClass()
                            ))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException("Compatible MenuType constructor not found"));
            ctor.setAccessible(true);

            Class<?> supplierInterface = ctor.getParameterTypes()[0];
            if (!supplierInterface.isInterface()) {
                throw new IllegalStateException(
                        "MenuType supplier parameter is not an interface: "
                                + supplierInterface.getName()
                );
            }

            Object supplier = Proxy.newProxyInstance(
                    MenuType.class.getClassLoader(),
                    new Class<?>[]{supplierInterface},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "EmmaEndInvMenuSupplier";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> null;
                            };
                        }
                        return new EndlessInventoryMenu(
                                (int) args[0],
                                (net.minecraft.world.entity.player.Inventory) args[1],
                                null
                        );
                    }
            );
            return (MenuType<EndlessInventoryMenu>) ctor.newInstance(
                    supplier,
                    FeatureFlags.DEFAULT_FLAGS
            );
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create the EndInv MenuType", e);
        }
    }

    // ── Persistent player attachments ────────────────────────────────────────

    private static void initAttachments(JavaPlugin plugin) {
        ModRegistries.NbtAttachments.endInvUUID =
                new PersistentUuidAttachment(new NamespacedKey(plugin, "endinv_uuid"));
        ModRegistries.NbtAttachments.syncedConfig =
                new PersistentConfigAttachment(new NamespacedKey(plugin, "endinv_settings"));
    }

    private static PersistentDataContainer data(Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            throw new IllegalArgumentException("Folia EndInv attachments require a server player");
        }
        return serverPlayer.getBukkitEntity().getPersistentDataContainer();
    }

    private static final class PersistentUuidAttachment implements NbtAttachment<UUID> {
        private final NamespacedKey key;

        private PersistentUuidAttachment(NamespacedKey key) {
            this.key = key;
        }

        @Override
        public @Nullable UUID getWith(Player player) {
            String stored = data(player).get(key, PersistentDataType.STRING);
            if (stored == null) {
                return null;
            }
            try {
                return UUID.fromString(stored);
            } catch (IllegalArgumentException invalidUuid) {
                data(player).remove(key);
                return null;
            }
        }

        @Override
        public void setTo(Player player, UUID uuid) {
            if (uuid == null) {
                data(player).remove(key);
            } else {
                data(player).set(key, PersistentDataType.STRING, uuid.toString());
            }
        }

        @Override
        public UUID computeIfAbsent(Player player) {
            UUID existing = getWith(player);
            if (existing != null && !ModInfo.DEFAULT_UUID.equals(existing)) {
                return existing;
            }
            UUID created = UUID.randomUUID();
            setTo(player, created);
            return created;
        }
    }

    private static final class PersistentConfigAttachment implements NbtAttachment<SyncedConfig> {
        private final NamespacedKey key;

        private PersistentConfigAttachment(NamespacedKey key) {
            this.key = key;
        }

        @Override
        public @Nullable SyncedConfig getWith(Player player) {
            byte[] stored = data(player).get(key, PersistentDataType.BYTE_ARRAY);
            if (stored == null || stored.length < 2) {
                return null;
            }
            return new SyncedConfig(stored[0] != 0, stored[1] != 0);
        }

        @Override
        public void setTo(Player player, SyncedConfig config) {
            if (config == null) {
                data(player).remove(key);
            } else {
                data(player).set(
                        key,
                        PersistentDataType.BYTE_ARRAY,
                        new byte[] {
                                (byte) (config.attaching() ? 1 : 0),
                                (byte) (config.autoPicking() ? 1 : 0)
                        }
                );
            }
        }

        @Override
        public SyncedConfig computeIfAbsent(Player player) {
            SyncedConfig existing = getWith(player);
            if (existing != null) {
                return existing;
            }
            setTo(player, SyncedConfig.DEFAULT);
            return SyncedConfig.DEFAULT;
        }
    }
}
