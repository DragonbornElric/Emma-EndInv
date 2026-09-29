package com.emma.endinv.commands;

import com.emma.endinv.ModInfo;
import com.emma.endinv.ModRegistries;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.options.ServerConfigs;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Register Endless Inventory config commands
 *
 * @author Kay Zhang
 * @since 2025-10-17
 * @version 1.1.0
 */
public class ConfigCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("endinv").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                        Commands.literal("config")
                                .then(
                                        Commands.literal("autoPick")
                                                .then(
                                                        Commands.argument("enable", BoolArgumentType.bool())
                                                                .executes(
                                                                        context-> cmdSetAutoPick(context.getSource(),BoolArgumentType.getBool(context, "enable"))

                                                                )
                                                )
                                )
                )
        );
        // Per-player switch, open to everyone (the /endinv root needs permission level 2).
        dispatcher.register(Commands.literal("autopick")
                .then(Commands.literal("on").executes(ctx -> cmdSetPlayerAutoPick(ctx.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("off").executes(ctx -> cmdSetPlayerAutoPick(ctx.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("status").executes(ctx -> cmdAutoPickStatus(ctx.getSource().getPlayerOrException())))
                .executes(ctx -> cmdAutoPickStatus(ctx.getSource().getPlayerOrException()))
        );
    }

    private static int cmdSetPlayerAutoPick(ServerPlayer player, boolean enable) {
        var attachment = ModRegistries.NbtAttachments.getSyncedConfig();
        SyncedConfig updated = new SyncedConfig(attachment.computeIfAbsent(player).attaching(), enable);
        attachment.setTo(player, updated);
        ModInfo.getPacketDistributor().sendToPlayer(player, updated);
        return cmdAutoPickStatus(player);
    }

    private static int cmdAutoPickStatus(ServerPlayer player) {
        boolean mine = ModRegistries.NbtAttachments.getSyncedConfig().computeIfAbsent(player).autoPicking();
        boolean server = ServerConfigs.ENABLE_AUTOPICK.get();
        String text = "Auto-pickup into EndInv: " + (mine ? "on" : "off") + " for you";
        if (!server) text += " (turned off on this server)";
        player.sendSystemMessage(Component.literal(text));
        return mine && server ? 1 : 0;
    }

    private static int cmdSetAutoPick(CommandSourceStack source,boolean enable){
        try {
            ServerConfigs.ENABLE_AUTOPICK.set(enable);
            source.sendSuccess(()-> Component.literal((enable ? "Enabled" : "Disabled") + " auto-pickup on this server"), true);
            return 1;
        } catch (Exception e) {
            return 0;
        }

    }
}
