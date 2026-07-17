package com.emma.endinv.folia;

import com.emma.endinv.ModRegistries;
import com.emma.endinv.ModInfo;
import com.emma.endinv.NbtAttachment;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.network.payloads.SyncedConfig;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Initialises {@link ModRegistries.Menus} and {@link ModRegistries.NbtAttachments}
 * for the Folia loader. Kept separate from common's ModRegistries to avoid polluting
 * it with reflection hacks that are Paper/Folia-specific.
 */
public final class FoliaMenuRegistry {

    private FoliaMenuRegistry() {}

    public static void init() {
        initMenuType();
        initAttachments();
    }

    // ── MenuType registration ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void initMenuType() {
        // BuiltInRegistries.MENU is frozen when Paper plugins enable.
        // Temporarily unfreeze, register, re-freeze.
        var registry = (net.minecraft.core.MappedRegistry<?>) BuiltInRegistries.MENU;
        try {
            java.lang.reflect.Field frozenField = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen");
            frozenField.setAccessible(true);
            frozenField.setBoolean(registry, false);

            MenuType<EndlessInventoryMenu> type = Registry.register(
                    BuiltInRegistries.MENU,
                    Identifier.fromNamespaceAndPath(ModInfo.MOD_ID, "endinv_menu"),
                    createMenuType()
            );
            ModRegistries.Menus.endinvMenuType = () -> type;

            frozenField.setBoolean(registry, true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to register EndInv MenuType", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.inventory.AbstractContainerMenu> MenuType<T> createMenuType() {
        try {
            Constructor<?>[] ctors = MenuType.class.getDeclaredConstructors();
            Constructor<?> ctor = ctors[0];
            ctor.setAccessible(true);

            Class<?> supplierIface = null;
            for (Class<?> c : MenuType.class.getDeclaredClasses()) {
                if (c.isInterface()) { supplierIface = c; break; }
            }
            if (supplierIface == null) throw new IllegalStateException("MenuType.MenuSupplier not found");

            Object supplier = Proxy.newProxyInstance(
                    MenuType.class.getClassLoader(),
                    new Class<?>[]{supplierIface},
                    (proxy, method, args) -> { throw new UnsupportedOperationException("Server-side MenuType factory"); }
            );
            return (MenuType<T>) ctor.newInstance(supplier, FeatureFlagSet.of());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MenuType for EndInv", e);
        }
    }

    // ── NbtAttachment registration (in-memory, server-side) ─────────────────

    private static void initAttachments() {
        ModRegistries.NbtAttachments.endInvUUID = new ServerMapAttachment<>(UUID::randomUUID);
        ModRegistries.NbtAttachments.syncedConfig = new ServerMapAttachment<>(() -> SyncedConfig.DEFAULT);
    }

    private static final class ServerMapAttachment<T> implements NbtAttachment<T> {
        private final Map<UUID, T> map = new HashMap<>();
        private final Supplier<T> defaultFactory;

        ServerMapAttachment(Supplier<T> defaultFactory) { this.defaultFactory = defaultFactory; }

        @Override public @Nullable T getWith(Player player) { return map.get(player.getUUID()); }
        @Override public void setTo(Player player, T t) { map.put(player.getUUID(), t); }
        @Override public T computeIfAbsent(Player player) {
            return map.computeIfAbsent(player.getUUID(), k -> defaultFactory.get());
        }
    }
}
