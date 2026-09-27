package com.emma.endinv.event;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ModInfo;
import com.emma.endinv.ModRegistries;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.commands.ConfigCommand;
import com.emma.endinv.commands.EndInvCommand;
import com.emma.endinv.data.EndlessInventoryData;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.menu.Station;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toClient.EndInvContent;
import com.emma.endinv.network.payloads.toClient.EndInvMetadata;
import com.emma.endinv.options.ServerConfigs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public final class NeoForgeEvents {

    private static final Set<UUID> PENDING_SYNC = new HashSet<>();

    private NeoForgeEvents() {}

    public static void register(IEventBus modBus) {
        // Game events go on the NeoForge global bus, not the mod bus
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onPlayerClone);
        NeoForgeStorageEvents.register();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        EndInvCommand.register(event.getDispatcher());
        ConfigCommand.register(event.getDispatcher());
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EndlessInventoryData.init(level);
            if (level.getServer() != null) {
                markPlayersForSync(level.getServer());
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        flushSync(event.getServer());
    }

    private static void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PENDING_SYNC.add(sp.getUUID());
        }
    }

    private static void onPlayerLogout(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ServerLevelEndInv.levelEndInvData == null) return;
            UUID uuid = player.getUUID();
            for (EndlessInventory endInv : ServerLevelEndInv.levelEndInvData.levelEndInvs) {
                endInv.viewerIds.remove(uuid);
            }
        }
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) return;
        ServerPlayer oldPlayer = (ServerPlayer) event.getOriginal();
        ServerPlayer newPlayer = (ServerPlayer) event.getEntity();

        var uuidAttachment = ModRegistries.NbtAttachments.getEndInvUUID();
        UUID uuid = uuidAttachment.getWith(oldPlayer);
        if (uuid != null) uuidAttachment.setTo(newPlayer, uuid);

        SyncedConfig config = ModRegistries.NbtAttachments.getSyncedConfig().getWith(oldPlayer);
        if (config != null) {
            ModRegistries.NbtAttachments.getSyncedConfig().setTo(newPlayer, config);
            ModInfo.getPacketDistributor().sendToPlayer(newPlayer, config);
        }
        PENDING_SYNC.add(newPlayer.getUUID());
    }

    private static void markPlayersForSync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PENDING_SYNC.add(player.getUUID());
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
            Set<Object> openSourceInventories = new HashSet<>();
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
