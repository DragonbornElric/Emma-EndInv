package com.emma.endinv.folia.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Thread-safety helpers for Folia's regionized scheduler. */
public final class RegionScheduling {

    private RegionScheduling() {}

    /** Run a task on the entity's owning region thread. Safe to call from any thread. */
    public static void runOnEntity(Plugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, sch -> task.run(), null);
    }

    /** Run a task on the global region (e.g. spawn chunks, non-world-specific ops). */
    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, sch -> task.run());
    }

    /** Schedule a repeating task on the global region. Returns a cancellable handle via runnable. */
    public static io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduleGlobalRepeating(
            Plugin plugin, long delayTicks, long periodTicks, Runnable task) {
        return Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, sch -> task.run(), delayTicks, periodTicks);
    }
}
