package com.emma.endinv.folia;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ModInfo;
import com.emma.endinv.ModRegistries;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.autopick.AutoPickHelper;
import com.emma.endinv.data.EndlessInventoryData;
import com.emma.endinv.folia.adapter.*;
import com.emma.endinv.folia.scheduler.RegionScheduling;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.menu.Station;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toClient.EndInvContent;
import com.emma.endinv.network.payloads.toClient.EndInvMetadata;
import com.emma.endinv.network.payloads.toClient.MenuAttachabilityPayload;
import com.emma.endinv.options.ServerConfigs;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FoliaEventListeners implements Listener {

    private static final Set<UUID> BACKGROUND_TICKS_QUEUED =
            ConcurrentHashMap.newKeySet();

    private final Plugin plugin;

    public FoliaEventListeners(Plugin plugin) { this.plugin = plugin; }

    // ── AutoPick ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        FoliaBlockBreakAdapter adapter = new FoliaBlockBreakAdapter(event);
        if (!AutoPickHelper.isEnabled(adapter.getPlayer())
                || ServerLevelEndInv.levelEndInvData == null) {
            return;
        }

        // AutoPickHelper computes the vanilla drops itself and explicitly
        // spawns any remainder that EndInv could not absorb. Suppress Bukkit's
        // second copy of those same drops in both the full and partial paths.
        boolean previouslyDroppingItems = event.isDropItems();
        event.setDropItems(false);
        try {
            AutoPickHelper.onBlockBreak(adapter);
        } catch (RuntimeException exception) {
            event.setDropItems(previouslyDroppingItems);
            throw exception;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        // A projectile may kill an entity after its shooter has moved into a
        // different Folia region. In that case this death callback must not
        // mutate the remote player's inventory or PDC from the victim's thread.
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(killer)) return;
        FoliaLivingDropsAdapter dropsAdapter = new FoliaLivingDropsAdapter(event);
        AutoPickHelper.onLivingDrops(dropsAdapter);
        dropsAdapter.syncBackToEvent();
        if (event.getDroppedExp() > 0) {
            AutoPickHelper.onExpDrops(new FoliaExpDropsAdapter(event, killer));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player bukkitPlayer)) return;
        ServerPlayer player = ((CraftPlayer) bukkitPlayer).getHandle();
        if (!AutoPickHelper.isEnabled(player)) return;

        EndlessInventory endInv =
                ServerLevelEndInv.getEndInvForPlayer(player).orElse(null);
        if (endInv == null) return;

        net.minecraft.world.entity.item.ItemEntity itemEntity =
                (net.minecraft.world.entity.item.ItemEntity)
                        ((org.bukkit.craftbukkit.v1_20_R1.entity.CraftItem)
                                event.getItem()).getHandle();
        net.minecraft.world.item.ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        int originalCount = stack.getCount();
        ItemKey key = ItemKey.asKey(stack);
        int beforeCount = storedCount(endInv, key);
        boolean infiniteAtCapacity =
                endInv.isInfinityMode()
                        && beforeCount >= endInv.getMaxItemStackSize();

        AutoPickHelper.onPickupItem(new FoliaPickupItemAdapter(event));

        // The shared 1.20 pickup hook's partial-remainder branch leaves the
        // absorbed count in the entity stack. Reconcile from EndInv's actual
        // count delta so Bukkit only performs its normal pickup for the true
        // remainder. Infinity-at-capacity intentionally absorbs without a
        // count delta.
        if (stack.getCount() == originalCount) return;
        int absorbed = infiniteAtCapacity
                ? originalCount
                : Math.max(0, storedCount(endInv, key) - beforeCount);
        int remainder = Math.max(0, originalCount - absorbed);
        stack.setCount(remainder);
        if (remainder == 0) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    // ── Player lifecycle ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        ServerPlayer nmsPlayer = ((CraftPlayer) event.getPlayer()).getHandle();
        scheduleSync(nmsPlayer);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        ServerPlayer nmsPlayer = ((CraftPlayer) event.getPlayer()).getHandle();
        scheduleSync(nmsPlayer);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeViewer(((CraftPlayer) event.getPlayer()).getHandle());
    }

    // ── World load ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (!(event.getWorld() instanceof CraftWorld craftWorld)) return;
        ServerLevel level = craftWorld.getHandle();
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        EndlessInventoryData.init(level);
        MinecraftServer server = level.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            scheduleSync(p);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void scheduleSync(ServerPlayer player) {
        RegionScheduling.runOnEntity(plugin, player.getBukkitEntity(), () -> sendInitialData(player));
    }

    private static void sendInitialData(ServerPlayer player) {
        var configAttachment = ModRegistries.NbtAttachments.getSyncedConfig();
        SyncedConfig syncedConfig = configAttachment.computeIfAbsent(player);

        boolean serverAutoPick = ServerConfigs.ENABLE_AUTOPICK.get();
        if (syncedConfig.autoPicking() != serverAutoPick) {
            syncedConfig = new SyncedConfig(syncedConfig.attaching(), serverAutoPick);
            configAttachment.setTo(player, syncedConfig);
        }

        ModInfo.getPacketDistributor().sendToPlayer(player, syncedConfig);

        var menuCfg = ServerConfigs.SPECIFIED_ATTACHABILITY.get();
        boolean defaultAttach = ServerConfigs.DEFAULT_ATTACH.get();
        ModInfo.getPacketDistributor().sendToPlayer(player,
                new MenuAttachabilityPayload(defaultAttach, menuCfg.isInventoryAttachable(), menuCfg.getConfigs()));

        ServerLevelEndInv.getEndInvForPlayer(player).ifPresent(endInv -> {
            ModInfo.getPacketDistributor().sendToPlayer(player, new EndInvContent(endInv.getItemMap()));
            ModInfo.getPacketDistributor().sendToPlayer(player, EndInvMetadata.getWith(endInv));
        });
    }

    private static void removeViewer(ServerPlayer player) {
        ServerLevelEndInv.PAGE_META_DATA_MANAGER.remove(player);
        ServerLevelEndInv.TEMP_ENDINV_REG.remove(player);
        if (ServerLevelEndInv.levelEndInvData == null) return;
        UUID uuid = player.getUUID();
        for (EndlessInventory endInv : ServerLevelEndInv.levelEndInvData.levelEndInvs) {
            endInv.viewerIds.remove(uuid);
        }
    }

    /**
     * Called on the global scheduler. Packet broadcasts are safe here, while
     * every access to a player's menu/level and every station tick is moved to
     * that player's owning region thread.
     */
    public static void scheduleBackgroundCooking(Plugin plugin, MinecraftServer server) {
        if (ServerLevelEndInv.levelEndInvData == null) return;
        for (EndlessInventory endInv :
                java.util.List.copyOf(ServerLevelEndInv.levelEndInvData.levelEndInvs)) {
            UUID ownerUuid = endInv.getOwnerUUID();
            if (ownerUuid == null) {
                endInv.broadcastChanges(server);
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
            if (owner == null) {
                endInv.broadcastChanges(server);
                continue;
            }
            UUID inventoryId = endInv.getUuid();
            if (!BACKGROUND_TICKS_QUEUED.add(inventoryId)) continue;
            owner.getBukkitEntity().getScheduler().run(
                    plugin,
                    scheduledTask -> {
                        try {
                            endInv.broadcastChanges(server);
                            if (owner.containerMenu instanceof EndlessInventoryMenu openMenu
                                    && openMenu.getSourceInventory() == endInv) {
                                return;
                            }
                            for (Station st : new Station[]{
                                    Station.FURNACE,
                                    Station.SMOKER,
                                    Station.BLAST_FURNACE
                            }) {
                                endInv.tickCookingBackground(owner.serverLevel(), st);
                            }
                            endInv.tickBrewingBackground(owner.serverLevel());
                        } finally {
                            BACKGROUND_TICKS_QUEUED.remove(inventoryId);
                        }
                    },
                    () -> BACKGROUND_TICKS_QUEUED.remove(inventoryId)
            );
        }
    }

    static void clearRuntimeState() {
        BACKGROUND_TICKS_QUEUED.clear();
    }

    private static int storedCount(EndlessInventory endInv, ItemKey key) {
        ItemState state = endInv.getItemMap().get(key);
        return state == null ? 0 : state.count();
    }
}
