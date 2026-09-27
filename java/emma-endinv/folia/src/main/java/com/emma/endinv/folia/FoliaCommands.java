package com.emma.endinv.folia;

import com.emma.endinv.commands.ConfigCommand;
import com.emma.endinv.commands.EndInvCommand;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.Plugin;

/** Registers Brigadier commands via Paper's lifecycle event API. */
@SuppressWarnings("UnstableApiUsage")
public final class FoliaCommands {

    private FoliaCommands() {}

    public static void register(Plugin plugin) {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            MinecraftServer mcServer = ((CraftServer) plugin.getServer()).getServer();
            var commands = mcServer.getCommands().getDispatcher();
            EndInvCommand.register(commands);
            ConfigCommand.register(commands);
            com.emma.endinv.storage.StorageCommand.register(commands);
        });
    }
}
