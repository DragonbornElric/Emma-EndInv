package com.emma.endinv.mixin;

import com.emma.endinv.nbt.EndInvPersistentDataHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerPersistentDataMixin implements EndInvPersistentDataHolder {

    @Unique
    private CompoundTag endinv$persistentData = new CompoundTag();

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void endinv$save(CompoundTag tag, CallbackInfo ci) {
        if (!endinv$persistentData.isEmpty()) {
            tag.put("EndInvPersistent", endinv$persistentData.copy());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void endinv$read(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("EndInvPersistent", Tag.TAG_COMPOUND)) {
            endinv$persistentData = tag.getCompound("EndInvPersistent").copy();
        } else {
            endinv$persistentData = new CompoundTag();
        }
    }

    @Override
    public CompoundTag endinv$getPersistentData() {
        return endinv$persistentData;
    }
}
