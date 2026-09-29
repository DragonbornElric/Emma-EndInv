package com.emma.endinv.event;

import com.emma.endinv.autopick.AutoPickHelper;
import com.emma.endinv.autopick.events.ILivingDropsEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fabric's stand-in for the Folia {@code EntityDeathEvent} hook: while a mob killed by a player
 * drops its death loot, collect the item entities it spawns, then hand them to
 * {@link AutoPickHelper#onLivingDrops} (which moves them into the killer's EndInv and removes the
 * absorbed entities). XP goes the same way through {@link MobDeathRedirect}, which
 * {@code ExperienceOrbAwardMixin} reads when {@code dropExperience} calls {@code ExperienceOrb.award}.
 * Server thread only.
 */
public final class DeathLootCapture {

    private static @Nullable Entity dying;
    private static @Nullable DamageSource source;
    private static final List<ItemEntity> DROPS = new ArrayList<>();

    private DeathLootCapture() {
    }

    public static void begin(Entity entity, DamageSource damageSource, ServerPlayer killer) {
        dying = entity;
        source = damageSource;
        DROPS.clear();
        MobDeathRedirect.set(killer);
    }

    /** Entity.spawnAtLocation return: keep the item if the dying mob spawned it. */
    public static void offer(Entity spawner, @Nullable ItemEntity item) {
        if (item != null && spawner == dying) {
            DROPS.add(item);
        }
    }

    public static void finish(Entity entity) {
        if (entity != dying) return;
        DamageSource src = source;
        List<ItemEntity> drops = new ArrayList<>(DROPS);
        dying = null;
        source = null;
        DROPS.clear();
        MobDeathRedirect.clear();
        if (drops.isEmpty()) return;
        AutoPickHelper.onLivingDrops(new ILivingDropsEvent() {
            @Override
            public DamageSource getSource() {
                return src;
            }

            @Override
            public Collection<ItemEntity> getDrops() {
                return drops;
            }

            @Override
            public void setCanceled(boolean canceled) {
                // The drops already spawned; onLivingDrops discards every entity it fully absorbed.
            }
        });
    }
}
