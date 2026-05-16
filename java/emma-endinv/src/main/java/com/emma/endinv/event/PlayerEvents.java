package com.emma.endinv.event;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ModInfo;
import com.emma.endinv.menu.Station;
import com.emma.endinv.ModRegistries;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toClient.EndInvContent;
import com.emma.endinv.network.payloads.toClient.EndInvMetadata;
import com.emma.endinv.options.ServerConfigs;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public final class PlayerEvents {

    private static final Set<UUID> PENDING_SYNC = new HashSet<>();

    private PlayerEvents() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlayerEvents::flushSync);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> scheduleSync(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeViewer(handler.player));
        ServerPlayerEvents.COPY_FROM.register(PlayerEvents::copyFromOldPlayer);
    }

    public static void markPlayersForSync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Ensure attachments are loaded before scheduling initial sync
            scheduleSync(player);
        }
    }

    private static void flushSync(MinecraftServer server) {
        Iterator<UUID> iterator = PENDING_SYNC.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                sendInitialData(player);
                iterator.remove();
            }
        }
        if (ServerLevelEndInv.levelEndInvData != null) {
            // Collect inventories whose menu is currently open so we skip background-ticking them
            // (the open menu's broadcastChanges() already ticks the furnace each server tick).
            java.util.Set<Object> openSourceInventories = new java.util.HashSet<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.containerMenu instanceof EndlessInventoryMenu eim) {
                    openSourceInventories.add(eim.getSourceInventory());
                }
            }
            for (EndlessInventory endInv : ServerLevelEndInv.levelEndInvData.levelEndInvs) {
                endInv.broadcastChanges(server);
                if (!openSourceInventories.contains(endInv)) {
                    UUID ownerUuid = endInv.getOwnerUUID();
                    if (ownerUuid != null) {
                        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUuid);
                        if (ownerPlayer != null) {
                            for (Station st : new Station[]{Station.FURNACE, Station.SMOKER, Station.BLAST_FURNACE}) {
                                endInv.tickCookingBackground(ownerPlayer.level(), st);
                            }
                            endInv.tickBrewingBackground(ownerPlayer.level());
                        }
                    }
                }
            }
        }
    }

    private static void removeViewer(ServerPlayer player) {
        if (ServerLevelEndInv.levelEndInvData == null) return;
        UUID uuid = player.getUUID();
        for (EndlessInventory endInv : ServerLevelEndInv.levelEndInvData.levelEndInvs) {
            endInv.viewerIds.remove(uuid);
        }
    }

    private static void scheduleSync(ServerPlayer player) {
        PENDING_SYNC.add(player.getUUID());
    }

    private static void copyFromOldPlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (alive) {
            return;
        }

        var uuidAttachment = ModRegistries.NbtAttachments.getEndInvUUID();
        UUID uuid = uuidAttachment.getWith(oldPlayer);
        if(uuid!=null) uuidAttachment.setTo(newPlayer, uuid);

        SyncedConfig config = ModRegistries.NbtAttachments.getSyncedConfig().getWith(oldPlayer);
        if (config != null) {
            ModRegistries.NbtAttachments.getSyncedConfig().setTo(newPlayer, config);
            ModInfo.getPacketDistributor().sendToPlayer(newPlayer, config);
        }
        scheduleSync(newPlayer);
    }

    private static void sendInitialData(ServerPlayer player) {
        // Safety: load persisted attachments if default
        var configAttachment = ModRegistries.NbtAttachments.getSyncedConfig();
        SyncedConfig syncedConfig = configAttachment.computeIfAbsent(player);

        // Force autopick to match server config — prevents stale per-player data from disabling it
        boolean serverAutoPick = ServerConfigs.ENABLE_AUTOPICK.get();
        if (syncedConfig.autoPicking() != serverAutoPick) {
            syncedConfig = new SyncedConfig(syncedConfig.attaching(), serverAutoPick);
            configAttachment.setTo(player, syncedConfig);
        }

        ModInfo.getPacketDistributor().sendToPlayer(player, syncedConfig);

        // Broadcast effective menu attachability on join
        var menuCfg = com.emma.endinv.options.ServerConfigs.SPECIFIED_ATTACHABILITY.get();
        boolean defaultAttach = com.emma.endinv.options.ServerConfigs.DEFAULT_ATTACH.get();
        ModInfo.getPacketDistributor().sendToPlayer(player,
                new com.emma.endinv.network.payloads.toClient.MenuAttachabilityPayload(
                        defaultAttach,
                        menuCfg.isInventoryAttachable(),
                        menuCfg.getConfigs()
                ));

        ServerLevelEndInv.getEndInvForPlayer(player).ifPresent(endInv -> {
            ModInfo.getPacketDistributor().sendToPlayer(player, new EndInvContent(endInv.getItemMap()));
            ModInfo.getPacketDistributor().sendToPlayer(player, EndInvMetadata.getWith(endInv));
        });
    }
}
