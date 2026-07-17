package com.emma.endinv.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class FabricNbtStorage {

    private static final String ROOT_KEY = "endinv_data";
    private static final String UUID_KEY = "endinv_uuid";

    private FabricNbtStorage() {
    }

    public static UUID getUuid(Player player) {
        CompoundTag root = root(player);
        return root.hasUUID(UUID_KEY) ? root.getUUID(UUID_KEY) : null;
    }

    public static void setUuid(Player player, UUID uuid) {
        root(player).putUUID(UUID_KEY, uuid);
    }

    public static CompoundTag getCompound(Player player, String key) {
        return root(player).getCompound(key);
    }

    public static void setCompound(Player player, String key, CompoundTag value) {
        root(player).put(key, value.copy());
    }

    public static boolean hasCompound(Player player, String key) {
        return root(player).contains(key, Tag.TAG_COMPOUND);
    }

    private static CompoundTag root(Player player) {
        CompoundTag persistent = ((EndInvPersistentDataHolder) player).endinv$getPersistentData();
        if (!persistent.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT_KEY, new CompoundTag());
        }
        return persistent.getCompound(ROOT_KEY);
    }
}
