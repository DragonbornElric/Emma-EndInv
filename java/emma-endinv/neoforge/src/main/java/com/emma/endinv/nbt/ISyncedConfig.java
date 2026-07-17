package com.emma.endinv.nbt;

import com.emma.endinv.network.payloads.SyncedConfig;
import org.jetbrains.annotations.Nullable;

public interface ISyncedConfig {

    @Nullable
    SyncedConfig getSyncedConfig();

    void setSyncedConfig(@Nullable SyncedConfig syncedConfig);
}
