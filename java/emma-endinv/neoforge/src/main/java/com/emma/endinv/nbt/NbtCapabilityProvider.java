package com.emma.endinv.nbt;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NbtCapabilityProvider<T extends INBTSerializable<CompoundTag>>
        implements ICapabilitySerializable<CompoundTag> {

    private final T backend;
    private final Capability<T> capability;
    private final LazyOptional<T> optional;

    NbtCapabilityProvider(T backend, Capability<T> capability) {
        this.backend = backend;
        this.capability = capability;
        this.optional = LazyOptional.of(() -> backend);
    }

    @Override
    public <U> @NotNull LazyOptional<U> getCapability(
            @NotNull Capability<U> requested,
            @Nullable Direction direction
    ) {
        return requested == capability ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return backend.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        backend.deserializeNBT(tag);
    }

    void invalidate() {
        optional.invalidate();
    }
}
