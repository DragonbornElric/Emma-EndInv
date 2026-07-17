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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.level.LevelEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public final class NeoForgeEvents {

    private static final Set<UUID> PENDING_SYNC = new HashSet<>();

    private NeoForgeEvents() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(NeoForgeEvents::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeEvents::onLevelLoad);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeEvents::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeEvents::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(NeoForgeEvents::onPlayerClone);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        EndInvCommand.register(event.getDispatcher());
        ConfigCommand.register(event.getDispatcher());
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EndlessInventoryData.init(level);
            markPlayersForSync(level.getServer());
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            flushSync(event.getServer());
        }
    }

    private static void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_SYNC.add(player.getUUID());
        }
    }

    private static void onPlayerLogout(PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PENDING_SYNC.remove(player.getUUID());
        ServerLevelEndInv.PAGE_META_DATA_MANAGER.remove(player);
        ServerLevelEndInv.TEMP_ENDINV_REG.remove(player);

        if (ServerLevelEndInv.levelEndInvData == null) return;
        UUID uuid = player.getUUID();
        for (EndlessInventory endInv : ServerLevelEndInv.levelEndInvData.levelEndInvs) {
            endInv.viewerIds.remove(uuid);
        }
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)
                || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }

        /*
         * NeoForge attachments can opt into copy-on-death. Forge 47 capabilities
         * cannot, so copy both values for every clone (death and End return).
         */
        oldPlayer.reviveCaps();
        try {
            UUID uuid = ModRegistries.NbtAttachments.getEndInvUUID().getWith(oldPlayer);
            if (uuid != null) {
                ModRegistries.NbtAttachments.getEndInvUUID().setTo(newPlayer, uuid);
            }

            SyncedConfig config =
                    ModRegistries.NbtAttachments.getSyncedConfig().getWith(oldPlayer);
            if (config != null) {
                ModRegistries.NbtAttachments.getSyncedConfig().setTo(newPlayer, config);
                ModInfo.getPacketDistributor().sendToPlayer(newPlayer, config);
            }
        } finally {
            oldPlayer.invalidateCaps();
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

        if (ServerLevelEndInv.levelEndInvData == null) return;

        Set<Object> openSourceInventories = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof EndlessInventoryMenu menu) {
                openSourceInventories.add(menu.getSourceInventory());
            }
        }

        for (EndlessInventory endInv : ServerLevelEndInv.levelEndInvData.levelEndInvs) {
            endInv.broadcastChanges(server);
            if (openSourceInventories.contains(endInv)) continue;

            UUID ownerUuid = endInv.getOwnerUUID();
            if (ownerUuid == null) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
            if (owner == null) continue;

            for (Station station :
                    new Station[]{Station.FURNACE, Station.SMOKER, Station.BLAST_FURNACE}) {
                endInv.tickCookingBackground(owner.serverLevel(), station);
            }
            endInv.tickBrewingBackground(owner.serverLevel());
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

        var menuConfig = ServerConfigs.SPECIFIED_ATTACHABILITY.get();
        boolean defaultAttach = ServerConfigs.DEFAULT_ATTACH.get();
        ModInfo.getPacketDistributor().sendToPlayer(
                player,
                new com.emma.endinv.network.payloads.toClient.MenuAttachabilityPayload(
                        defaultAttach,
                        menuConfig.isInventoryAttachable(),
                        menuConfig.getConfigs()
                )
        );

        ServerLevelEndInv.getEndInvForPlayer(player).ifPresent(endInv -> {
            ModInfo.getPacketDistributor()
                    .sendToPlayer(player, new EndInvContent(endInv.getItemMap()));
            ModInfo.getPacketDistributor()
                    .sendToPlayer(player, EndInvMetadata.getWith(endInv));
        });
    }
}
