package com.emma.endinv.nbt;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public interface IEndInvUuid {

    @Nullable
    UUID getUuid();

    void setUuid(@Nullable UUID uuid);
}
