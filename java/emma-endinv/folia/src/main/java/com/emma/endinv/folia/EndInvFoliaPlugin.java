package com.emma.endinv.folia;

import com.emma.endinv.IPlatform;
import com.emma.endinv.ModInfo;
import com.emma.endinv.ModRegistries;
import com.emma.endinv.NbtAttachment;
import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.network.IPacketDistributor;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.options.ServerConfigs;
import com.emma.endinv.options.config.json.JsonConfigurationHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Supplier;

public final class EndInvFoliaPlugin extends JavaPlugin {

    private static EndInvFoliaPlugin instance;
    private FoliaIncomingPayloadBridge payloadBridge;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask tickTask;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask saveTask;
    private static final long SAVE_INTERVAL_TICKS = 20L * 60L;

    public static EndInvFoliaPlugin get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        MinecraftServer mcServer = ((CraftServer) getServer()).getServer();

        // 1. Bootstrap ILoaderProvider so ILoaderProvider.get().isClient() returns false
        FoliaLoaderProvider.setConfigDir(getDataFolder().toPath());

        // 2. Register MenuType + NbtAttachments (must happen before any player events)
        FoliaMenuRegistry.init();

        // 3. Load config.yml → wire ServerConfigs
        FoliaConfigLoader.load(this);

        // 4. Register item stubs (no-op — items are client/dev-only on Folia)
        ModRegistries.Items.testEndInv = () -> null;
        ModRegistries.Items.screenDebugger = () -> null;

        // 5. Wire IPlatform + IPacketDistributor
        ModInfo.platformContext = new FoliaPlatform();
        ModInfo.setPacketDistributor(new FoliaPacketDistributor(mcServer));

        // 6. Load server config
        new JsonConfigurationHandler(
                getDataFolder().toPath().resolve("endless_inventory-server.json"),
                ServerConfigs.getConfigs()
        ).load();

        // 7. Inject S2C payload codecs into GAMEPLAY_STREAM_CODEC
        FoliaPayloadRegistry.register();

        // 8. Register Brigadier commands
        FoliaCommands.register(this);

        // 9. Register plugin-message bridge (C2S payloads → handle on player region thread)
        payloadBridge = new FoliaIncomingPayloadBridge(mcServer, this);
        payloadBridge.register();

        // 10. Register Bukkit event listeners
        getServer().getPluginManager().registerEvents(new FoliaEventListeners(this), this);
        getServer().getPluginManager().registerEvents(new com.emma.endinv.folia.debug.CraftingDebugListener(this), this);
        getServer().getPluginManager().registerEvents(new FoliaRecipeBookPlacementListener(this), this);

        // 11. Periodic tick for broadcastChanges + background cooking (global region, 1 tick period)
        tickTask = getServer().getGlobalRegionScheduler()
                .runAtFixedRate(this, sch -> FoliaEventListeners.tickBackgroundCooking(mcServer), 1L, 1L);

        // 11b. Folia's autosave never writes SavedData, so EndInv was only persisted on clean shutdown and a
        // crash/host reboot lost everything since startup. Flush it every minute when dirty.
        saveTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, sch -> {
            if (!getServer().getWorlds().isEmpty()
                    && getServer().getWorlds().getFirst() instanceof org.bukkit.craftbukkit.CraftWorld craftWorld) {
                com.emma.endinv.data.EndlessInventoryData.saveNow(craftWorld.getHandle());
            }
        }, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);

        // 12. Handle /reload: worlds already loaded before onEnable
        if (!getServer().getWorlds().isEmpty()) {
            var overworld = getServer().getWorlds().getFirst();
            if (overworld instanceof org.bukkit.craftbukkit.CraftWorld craftWorld) {
                com.emma.endinv.data.EndlessInventoryData.init(craftWorld.getHandle());
                for (net.minecraft.server.level.ServerPlayer p : mcServer.getPlayerList().getPlayers()) {
                    new FoliaEventListeners(this).scheduleSync(p);
                }
            }
        }

        getLogger().info("EmmaEndInv (Folia) enabled");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        if (saveTask != null) { saveTask.cancel(); saveTask = null; }
        if (payloadBridge != null) { payloadBridge.unregister(); payloadBridge = null; }

        // Force-flush EndInv data — Pelican Panel may kill the process before async save completes
        if (!getServer().getWorlds().isEmpty()) {
            var overworld = getServer().getWorlds().getFirst();
            if (overworld instanceof org.bukkit.craftbukkit.CraftWorld craftWorld) {
                craftWorld.getHandle().getDataStorage().saveAndJoin();
                getLogger().info("EndInv data flushed to disk");
            }
        }
        getLogger().info("EmmaEndInv (Folia) disabled");
        instance = null;
    }
}
