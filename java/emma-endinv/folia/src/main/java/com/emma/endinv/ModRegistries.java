package com.emma.endinv;

import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.network.payloads.SyncedConfig;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;

import java.util.UUID;
import java.util.function.Supplier;

public final class ModRegistries {

    private ModRegistries() {}

    public static class Items {

        public static Supplier<Item> testEndInv;
        public static Supplier<Item> screenDebugger;

        public static Item getTestEndInv() {
            return testEndInv != null ? testEndInv.get() : null;
        }

        public static Item getScreenDebugger() {
            return screenDebugger != null ? screenDebugger.get() : null;
        }
    }

    public static class Menus {

        public static Supplier<MenuType<EndlessInventoryMenu>> endinvMenuType;

        public static MenuType<EndlessInventoryMenu> getEndInvMenuType() {
            return endinvMenuType.get();
        }
    }

    public static class NbtAttachments {

        public static NbtAttachment<UUID> endInvUUID;
        public static NbtAttachment<SyncedConfig> syncedConfig;

        public static NbtAttachment<UUID> getEndInvUUID() {
            return endInvUUID;
        }

        public static NbtAttachment<SyncedConfig> getSyncedConfig() {
            return syncedConfig;
        }
    }
}
