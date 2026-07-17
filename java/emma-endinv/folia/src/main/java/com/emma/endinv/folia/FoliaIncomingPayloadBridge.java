package com.emma.endinv.folia;

import com.emma.endinv.folia.scheduler.PayloadDispatch;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toServer.*;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
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
        ModPacketPayload decode(FriendlyByteBuf buf);
    }

    private static final Map<String, PayloadDecoder> DECODERS = Map.ofEntries(
            Map.entry(channel("item_click"), ItemClickPayload::decode),
            Map.entry(channel("item_modify"), CreativeItemModPayload::decode),
            Map.entry(channel("page_context"), ItemPageContext::decode),
            Map.entry(channel("open_endinv"), OpenEndInvPayload::decode),
            Map.entry(channel("quick_move_page"), QuickMoveToPagePayload::decode),
            Map.entry(channel("bulk_quick_move_from_page"), BulkQuickMoveFromPagePayload::decode),
            Map.entry(channel("star_item"), StarItemPayload::decode),
            Map.entry(channel("set_active_station"), SetActiveStationPayload::decode),
            Map.entry(channel("swap_menu_slot"), SwapMenuSlotPayload::decode),
            Map.entry(channel("endinv_settings"), SyncedConfig::decode)
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
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
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

    private static String channel(String id) {
        return "endless_inventory:" + id;
    }
}
