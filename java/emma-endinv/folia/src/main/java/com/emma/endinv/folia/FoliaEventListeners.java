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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
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

import java.util.UUID;

public class FoliaEventListeners implements Listener {

    private final Plugin plugin;

    public FoliaEventListeners(Plugin plugin) { this.plugin = plugin; }

    // ── AutoPick ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        AutoPickHelper.onBlockBreak(new FoliaBlockBreakAdapter(event));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        FoliaLivingDropsAdapter dropsAdapter = new FoliaLivingDropsAdapter(event);
        AutoPickHelper.onLivingDrops(dropsAdapter);
        dropsAdapter.syncBackToEvent();
        if (event.getDroppedExp() > 0) {
            AutoPickHelper.onExpDrops(new FoliaExpDropsAdapter(event, killer));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        AutoPickHelper.onPickupItem(new FoliaPickupItemAdapter(event));
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
        if (ServerLevelEndInv.levelEndInvData == null) return;
        UUID uuid = player.getUUID();
        for (EndlessInventory endInv : ServerLevelEndInv.levelEndInvData.levelEndInvs) {
            endInv.viewerIds.remove(uuid);
        }
    }

    /** Called on the global region tick — ticks background cooking/brewing for players not in their menu. */
    public static void tickBackgroundCooking(MinecraftServer server) {
        if (ServerLevelEndInv.levelEndInvData == null) return;
        java.util.Set<Object> openSrcInvs = new java.util.HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.containerMenu instanceof EndlessInventoryMenu eim) {
                openSrcInvs.add(eim.getSourceInventory());
            }
        }
        for (EndlessInventory endInv : ServerLevelEndInv.levelEndInvData.levelEndInvs) {
            endInv.broadcastChanges(server);
            if (!openSrcInvs.contains(endInv)) {
                UUID ownerUuid = endInv.getOwnerUUID();
                if (ownerUuid == null) continue;
                ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
                if (owner == null) continue;
                for (Station st : new Station[]{Station.FURNACE, Station.SMOKER, Station.BLAST_FURNACE}) {
                    endInv.tickCookingBackground(owner.level(), st);
                }
                endInv.tickBrewingBackground(owner.level());
            }
        }
    }
}
