package com.emma.endinv.mixin.fabric;

import com.emma.endinv.event.DeathLootCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every spawnAtLocation overload ends here; hands death drops to {@link DeathLootCapture}. */
@Mixin(Entity.class)
public abstract class EntitySpawnAtLocationMixin {

    @Inject(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN"))
    private void endinv$captureDeathDrop(ServerLevel level, ItemStack stack, Vec3 offset, CallbackInfoReturnable<ItemEntity> cir) {
        DeathLootCapture.offer((Entity) (Object) this, cir.getReturnValue());
    }
}
