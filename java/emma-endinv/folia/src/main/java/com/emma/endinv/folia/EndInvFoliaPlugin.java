package com.emma.endinv.folia;

import com.emma.endinv.ModInfo;
import com.emma.endinv.ModRegistries;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.data.EndlessInventoryData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;

public final class EndInvFoliaPlugin extends JavaPlugin {

    private static EndInvFoliaPlugin instance;
    private FoliaIncomingPayloadBridge payloadBridge;
    private FoliaEventListeners eventListeners;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask tickTask;

    public static EndInvFoliaPlugin get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        MinecraftServer mcServer = ((CraftServer) getServer()).getServer();

        // 1. Bootstrap ILoaderProvider so ILoaderProvider.get().isClient() returns false
        FoliaLoaderProvider.setConfigDir(getDataFolder().toPath());

        // 2. Register MenuType + NbtAttachments (must happen before any player events)
        FoliaMenuRegistry.init(this);

        // 3. Load config.yml → wire ServerConfigs
        FoliaConfigLoader.load(this);

        // 4. Register item stubs (no-op — items are client/dev-only on Folia)
        ModRegistries.Items.testEndInv = () -> null;
        ModRegistries.Items.screenDebugger = () -> null;

        // 5. Wire IPlatform + IPacketDistributor
        ModInfo.platformContext = new FoliaPlatform();
        ModInfo.setPacketDistributor(new FoliaPacketDistributor(mcServer));

        // 6. Register Brigadier commands
        FoliaCommands.register(this);

        // 7. Register plugin-message bridge (C2S payloads → handle on player region thread)
        payloadBridge = new FoliaIncomingPayloadBridge(mcServer, this);
        payloadBridge.register();

        // 8. Register Bukkit event listeners
        eventListeners = new FoliaEventListeners(this);
        getServer().getPluginManager().registerEvents(eventListeners, this);
        getServer().getPluginManager().registerEvents(new com.emma.endinv.folia.debug.CraftingDebugListener(this), this);
        getServer().getPluginManager().registerEvents(new FoliaRecipeBookPlacementListener(this), this);

        // 9. Periodic tick for broadcastChanges + background cooking (global region, 1 tick period)
        tickTask = getServer().getGlobalRegionScheduler()
                .runAtFixedRate(
                        this,
                        sch -> FoliaEventListeners.scheduleBackgroundCooking(this, mcServer),
                        1L,
                        1L
                );

        // 10. Handle /reload: worlds may already be loaded before onEnable.
        // Do not assume Bukkit's first world is the overworld.
        ServerLevel overworld = findOverworld();
        if (overworld != null) {
            EndlessInventoryData.init(overworld);
            for (net.minecraft.server.level.ServerPlayer player : mcServer.getPlayerList().getPlayers()) {
                eventListeners.scheduleSync(player);
            }
        }

        getLogger().info("EmmaEndInv (Folia) enabled");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        if (payloadBridge != null) { payloadBridge.unregister(); payloadBridge = null; }

        // Force-flush EndInv data — Pelican Panel may kill the process before
        // the next normal world save completes.
        ServerLevel overworld = findOverworld();
        if (overworld != null) {
            overworld.getDataStorage().save();
            getLogger().info("EndInv data flushed to disk");
        }
        ServerLevelEndInv.PAGE_META_DATA_MANAGER.clear();
        ServerLevelEndInv.TEMP_ENDINV_REG.clear();
        ServerLevelEndInv.levelEndInvData = null;
        FoliaEventListeners.clearRuntimeState();
        eventListeners = null;
        getLogger().info("EmmaEndInv (Folia) disabled");
        instance = null;
    }

    private ServerLevel findOverworld() {
        for (org.bukkit.World world : getServer().getWorlds()) {
            if (world instanceof CraftWorld craftWorld) {
                ServerLevel level = craftWorld.getHandle();
                if (level.dimension().equals(Level.OVERWORLD)) {
                    return level;
                }
            }
        }
        return null;
    }
}
