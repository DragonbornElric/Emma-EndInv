package com.emma.endinv.mixin;

import com.emma.endinv.menu.StationUnlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DropStationsOnDeath: a dying player drops the station blocks and bookshelves put into their
 * EndInv, next to their inventory (Fabric and NeoForge; the Folia plugin uses PlayerDeathEvent).
 */
@Mixin(Player.class)
public abstract class PlayerDeathStationDropMixin {

    @Inject(method = "dropEquipment", at = @At("TAIL"))
    private void endinv$dropStations(ServerLevel level, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) return;
        for (ItemStack stack : StationUnlocks.takeDeathDrops(player)) {
            player.drop(stack, true, false);
        }
    }
}
