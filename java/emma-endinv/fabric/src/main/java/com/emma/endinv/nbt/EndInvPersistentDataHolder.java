package com.emma.endinv.nbt;

import net.minecraft.nbt.CompoundTag;

/**
 * Fabric 1.20.1 has no data-attachment API, so player-owned EndInv metadata is
 * persisted in the vanilla player save through this mixin-backed holder.
 */
public interface EndInvPersistentDataHolder {

    CompoundTag endinv$getPersistentData();
}
