package com.emma.endinv.folia;

import com.emma.endinv.folia.scheduler.PayloadDispatch;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toServer.*;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.slf4j.Logger;

import java.util.Map;

/** Listens on Bukkit plugin-message channels and dispatches decoded payloads to their handlers
 *  on the player's region thread (Folia-safe). */
public final class FoliaIncomingPayloadBridge implements PluginMessageListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private interface PayloadDecoder {
        ModPacketPayload decode(RegistryFriendlyByteBuf buf);
    }

    private static final Map<String, PayloadDecoder> DECODERS = Map.ofEntries(
            Map.entry(channel(ItemClickPayload.TYPE),                decodeWith(ItemClickPayload.STREAM_CODEC)),
            Map.entry(channel(CreativeItemModPayload.TYPE),          decodeWith(CreativeItemModPayload.STREAM_CODEC)),
            Map.entry(channel(ItemPageContext.TYPE),                 decodeWith(ItemPageContext.STREAM_CODEC)),
            Map.entry(channel(OpenEndInvPayload.TYPE),               decodeWith(OpenEndInvPayload.STREAM_CODEC)),
            Map.entry(channel(QuickMoveToPagePayload.TYPE),          decodeWith(QuickMoveToPagePayload.STREAM_CODEC)),
            Map.entry(channel(BulkQuickMoveFromPagePayload.TYPE),    decodeWith(BulkQuickMoveFromPagePayload.STREAM_CODEC)),
            Map.entry(channel(StarItemPayload.TYPE),                 decodeWith(StarItemPayload.STREAM_CODEC)),
            Map.entry(channel(SetActiveStationPayload.TYPE),         decodeWith(SetActiveStationPayload.STREAM_CODEC)),
            Map.entry(channel(com.emma.endinv.network.payloads.toServer.UnlockStationPayload.TYPE), decodeWith(com.emma.endinv.network.payloads.toServer.UnlockStationPayload.STREAM_CODEC)),
            Map.entry(channel(com.emma.endinv.network.payloads.toServer.AddBookshelvesPayload.TYPE), decodeWith(com.emma.endinv.network.payloads.toServer.AddBookshelvesPayload.STREAM_CODEC)),
            Map.entry(channel(SwapMenuSlotPayload.TYPE),             decodeWith(SwapMenuSlotPayload.STREAM_CODEC)),
            Map.entry(channel(ManageEndInvPayload.TYPE),             decodeWith(ManageEndInvPayload.STREAM_CODEC)),
            Map.entry(channel(com.emma.endinv.storage.StorageRequestPayload.TYPE), decodeWith(com.emma.endinv.storage.StorageRequestPayload.STREAM_CODEC)),
            Map.entry(channel(SyncedConfig.TYPE),                    decodeWith(SyncedConfig.STREAM_CODEC))
    );

    private final MinecraftServer server;
    private final JavaPlugin plugin;

    public FoliaIncomingPayloadBridge(MinecraftServer server, JavaPlugin plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    public void register() {
        var messenger = plugin.getServer().getMessenger();
        for (String channel : DECODERS.keySet()) {
            messenger.registerIncomingPluginChannel(plugin, channel, this);
        }
    }

    public void unregister() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        PayloadDecoder decoder = DECODERS.get(channel);
        if (decoder == null) return;
        if (!(player instanceof CraftPlayer craftPlayer)) {
            LOGGER.warn("EndInv payload on channel={} from non-CraftPlayer {}", channel, player.getClass().getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(message), server.registryAccess());
        try {
            ModPacketPayload payload = decoder.decode(buf);
            // Hop to the player's region thread before invoking handle() (Folia requirement).
            PayloadDispatch.dispatch(plugin, serverPlayer, payload);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode EndInv payload channel={} player={}", channel, serverPlayer.getName().getString(), e);
        } finally {
            buf.release();
        }
    }

    private static <T extends ModPacketPayload> PayloadDecoder decodeWith(StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        return codec::decode;
    }

    private static String channel(CustomPacketPayload.Type<?> type) {
        return type.id().toString();
    }
}
