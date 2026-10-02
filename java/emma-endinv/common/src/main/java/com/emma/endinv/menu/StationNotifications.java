package com.emma.endinv.menu;

import com.emma.endinv.ModInfo;
import com.emma.endinv.api.StationEvent;
import com.emma.endinv.network.payloads.toClient.StationEventPayload;
import com.emma.endinv.options.ServerConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Server side: tells a player a station in their EndInv finished ({@link StationEvent.Reason#DONE})
 * or burned out ({@link StationEvent.Reason#OUT_OF_FUEL}), whether its screen is open or it runs in
 * the background. Three ways: a chat line (when {@code StationChatMessages} is on), a
 * {@link StationEventPayload} for the client API, and the server API listeners.
 */
public final class StationNotifications {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final List<BiConsumer<ServerPlayer, StationEvent>> LISTENERS = new CopyOnWriteArrayList<>();

    private StationNotifications() {}

    public static void addListener(BiConsumer<ServerPlayer, StationEvent> listener) {
        LISTENERS.add(listener);
    }

    public static void notify(@Nullable ServerPlayer player, Station st, StationEvent.Reason reason, ItemStack result) {
        if (player == null) return;
        Item block = st.unlockItem();
        if (block == null) return;
        StationEvent event = new StationEvent(block, reason, reason == StationEvent.Reason.DONE ? result.copy() : ItemStack.EMPTY);
        if (ServerConfigs.STATION_CHAT_MESSAGES.get()) player.sendSystemMessage(message(event));
        ModInfo.getPacketDistributor().sendToPlayer(player, new StationEventPayload(event));
        for (var listener : LISTENERS) {
            try {
                listener.accept(player, event);
            } catch (RuntimeException e) {
                LOGGER.warn("EndInv station listener failed", e);
            }
        }
    }

    /** "[EndInv] Furnace is done: 8 Iron Ingot" / "[EndInv] Furnace is out of fuel". English fallback for Folia/Paper. */
    private static Component message(StationEvent event) {
        Component station = new ItemStack(event.station()).getHoverName();
        Component text;
        if (event.reason() == StationEvent.Reason.DONE) {
            Component result = event.result().isEmpty()
                    ? Component.empty()
                    : Component.literal(event.result().getCount() + " ").append(event.result().getHoverName());
            text = Component.translatableWithFallback("emma_endinv.station.done", "%s is done: %s", station, result);
        } else {
            text = Component.translatableWithFallback("emma_endinv.station.out_of_fuel", "%s is out of fuel", station);
        }
        return Component.literal("[EndInv] ").withStyle(ChatFormatting.GOLD).append(text.copy().withStyle(ChatFormatting.WHITE));
    }
}
