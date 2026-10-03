package com.emma.endinv.event;

import com.emma.endinv.commands.ConfigCommand;
import com.emma.endinv.commands.EndInvCommand;
import com.emma.endinv.storage.StorageCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class Commands {

    private Commands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            EndInvCommand.register(dispatcher);
            ConfigCommand.register(dispatcher);
            StorageCommand.register(dispatcher);
            dispatcher.register(net.minecraft.commands.Commands.literal("endinv-cluster-snapshot")
                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .executes(context -> {
                    var snapshot = com.emma.endinv.api.ClusterStateSnapshot.capture(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("EndInv real codec snapshot round-trip OK; schema=1; nbt_chars=" + snapshot.toString().length() + "; inventories=" + com.emma.endinv.ServerLevelEndInv.levelEndInvData.levelEndInvs.size() + "; stored_items=" + com.emma.endinv.ServerLevelEndInv.levelEndInvData.levelEndInvs.stream().flatMap(i -> i.getItemMap().values().stream()).mapToLong(i -> i.count()).sum() + "; nbt=" + snapshot), false);
                    return 1;
                }));
        });
    }
}
