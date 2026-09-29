package com.emma.endinv.mixin.fabric;

import com.emma.endinv.autopick.AutoPickHelper;
import com.emma.endinv.event.DeathLootCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mob killed by a player: its loot-table, custom and equipment drops and its XP go to the killer's
 * EndInv, as the Folia plugin does from EntityDeathEvent. A dying player keeps vanilla drops.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathLootMixin {

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void endinv$beginDeathLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player) return;
        if (source.getEntity() instanceof ServerPlayer killer && AutoPickHelper.isEnabled(killer)) {
            DeathLootCapture.begin(self, source, killer);
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
    private void endinv$finishDeathLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        DeathLootCapture.finish((LivingEntity) (Object) this);
    }
}
