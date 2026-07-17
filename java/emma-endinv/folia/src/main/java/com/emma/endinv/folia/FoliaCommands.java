package com.emma.endinv.folia;

import com.emma.endinv.commands.ConfigCommand;
import com.emma.endinv.commands.EndInvCommand;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.plugin.Plugin;

/** Registers Brigadier commands directly on the 1.20.1 server dispatcher. */
public final class FoliaCommands {

    private FoliaCommands() {}

    public static void register(Plugin plugin) {
        MinecraftServer mcServer = ((CraftServer) plugin.getServer()).getServer();
        var commands = mcServer.getCommands().getDispatcher();
        EndInvCommand.register(commands);
        ConfigCommand.register(commands);
    }
}
