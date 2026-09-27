package com.emma.endinv.folia;

import com.emma.endinv.storage.StorageIndex;
import com.emma.endinv.storage.StorageTag;
import com.emma.endinv.storage.StorageTracker;
import com.emma.endinv.storage.TrackedContainer;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Folia glue for the storage tracker: the tagging interaction, per-region polling, and the Storage Tag recipe
 * (the Fabric/NeoForge jars ship it as a data pack recipe, which a plugin cannot).
 */
public final class FoliaStorageTracker implements Listener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Plugin plugin;

    public FoliaStorageTracker(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs after protection plugins (HIGHEST, ignoring cancelled events), so a player cannot read a chest through a
     * claim they could not open. Fires on the player's region thread, which owns the clicked block.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) return;
        ServerPlayer player = ((CraftPlayer) event.getPlayer()).getHandle();
        InteractionHand hand = event.getHand() == EquipmentSlot.OFF_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Block block = event.getClickedBlock();
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        if (!StorageTracker.shouldIntercept(player, hand, player.level(), pos)) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        StorageTracker.onUseBlock(player, hand, pos);
    }

    /**
     * Global-region tick: group tracked containers by chunk and refresh each loaded chunk's containers on the region
     * thread that owns it. Unloaded chunks are skipped; their contents cannot change until they load again.
     */
    public void poll(MinecraftServer server) {
        if (!StorageIndex.isLoaded()) return;
        Map<ServerLevel, Map<Long, List<TrackedContainer>>> byChunk = new HashMap<>();
        for (TrackedContainer container : StorageIndex.all()) {
            ServerLevel level = server.getLevel(container.dimension());
            if (level == null) continue;
            long chunk = ((long) (container.pos().getX() >> 4) << 32) | ((container.pos().getZ() >> 4) & 0xFFFFFFFFL);
            byChunk.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(chunk, c -> new ArrayList<>()).add(container);
        }
        byChunk.forEach((level, chunks) -> {
            World world = level.getWorld();
            chunks.forEach((chunk, containers) -> {
                int cx = (int) (chunk >> 32);
                int cz = (int) chunk.longValue();
                if (!world.isChunkLoaded(cx, cz)) return;
                Bukkit.getRegionScheduler().execute(plugin, world, cx, cz, () -> {
                    for (TrackedContainer container : containers) {
                        // Re-read the entry: it may have changed or been removed since this task was queued.
                        TrackedContainer current = StorageIndex.get(container.key());
                        if (current != null) StorageTracker.refresh(level, current);
                    }
                });
            });
        });
    }

    public static void registerRecipe(Plugin plugin) {
        try {
            ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, "storage_tag"),
                    CraftItemStack.asBukkitCopy(StorageTag.create(1)));
            recipe.addIngredient(Material.PAPER);
            recipe.addIngredient(Material.CHEST);
            Bukkit.addRecipe(recipe);
        } catch (Exception e) {
            LOGGER.warn("Could not register the Storage Tag recipe; /storage tag still works", e);
        }
    }
}
