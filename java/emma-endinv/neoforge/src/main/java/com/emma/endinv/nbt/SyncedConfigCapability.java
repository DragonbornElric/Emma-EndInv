package com.emma.endinv.nbt;

import com.emma.endinv.network.payloads.SyncedConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class SyncedConfigCapability
        implements ISyncedConfig, INBTSerializable<CompoundTag> {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private SyncedConfig syncedConfig;

    @Override
    @Nullable
    public SyncedConfig getSyncedConfig() {
        return syncedConfig;
    }

    @Override
    public void setSyncedConfig(@Nullable SyncedConfig syncedConfig) {
        this.syncedConfig = syncedConfig;
    }

    @Override
    public CompoundTag serializeNBT() {
        SyncedConfig value = syncedConfig != null ? syncedConfig : SyncedConfig.DEFAULT;
        return (CompoundTag) SyncedConfig.CODEC
                .encodeStart(NbtOps.INSTANCE, value)
                .getOrThrow(false,
                        error -> LOGGER.error("Failed to encode EndInv settings: {}", error));
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        SyncedConfig.CODEC.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(
                        error -> LOGGER.error("Failed to decode EndInv settings: {}", error))
                .ifPresent(value -> syncedConfig = value);
    }
}
