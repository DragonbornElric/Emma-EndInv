package com.emma.endinv.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class EndInvUuidCapability
        implements IEndInvUuid, INBTSerializable<CompoundTag> {

    private static final String UUID_KEY = "EndInvUuid";

    @Nullable
    private UUID uuid;

    @Override
    @Nullable
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public void setUuid(@Nullable UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (uuid != null) {
            tag.putUUID(UUID_KEY, uuid);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        uuid = tag.hasUUID(UUID_KEY) ? tag.getUUID(UUID_KEY) : null;
    }
}
