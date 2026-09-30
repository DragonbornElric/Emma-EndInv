package com.emma.endinv.api;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side API: read and change a player's Endless Inventory from another mod.
 * <p>
 * Works on Fabric and NeoForge servers and inside the Folia/Paper plugin. Call it on the server
 * thread (Fabric/NeoForge) or on the player's region thread (Folia). Items are matched exactly:
 * same item and same data components (enchantments, damage, name, ...), like EndInv itself.
 * Changes are saved with the world and sent to the player's client on the next server tick.
 * Methods return empty/zero results when EndInv data isn't loaded yet (before the server starts).
 */
public final class EmmaEndInvServerApi {
    private EmmaEndInvServerApi() {}

    /** How many of {@code like} (item + components, count ignored) the player's EndInv holds. */
    public static int count(ServerPlayer player, ItemStack like) {
        if (like.isEmpty()) return 0;
        return endInv(player)
                .map(inv -> inv.getItemMap().get(ItemKey.asKey(like)))
                .map(ItemState::count)
                .orElse(0);
    }

    /**
     * Put {@code stack} into the player's EndInv. The given stack is not modified.
     * @return what didn't fit (empty when everything was stored, the full stack when EndInv isn't available)
     */
    public static ItemStack insert(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        Optional<EndlessInventory> inv = endInv(player);
        return inv.isPresent() ? inv.get().addItem(stack.copy()) : stack.copy();
    }

    /**
     * Take up to {@code count} of {@code like} (item + components) out of the player's EndInv.
     * @return the items taken (possibly fewer than asked, or empty)
     */
    public static ItemStack extract(ServerPlayer player, ItemStack like, int count) {
        if (like.isEmpty() || count <= 0 || count(player, like) == 0) return ItemStack.EMPTY;
        return endInv(player).map(inv -> inv.takeItem(ItemKey.asKey(like), count)).orElse(ItemStack.EMPTY);
    }

    /** A copy of everything in the player's EndInv: each key is a 1-count stack (one per item type,
     *  with its components), each value the stored count. */
    public static Map<ItemStack, Integer> contents(ServerPlayer player) {
        Map<ItemStack, Integer> out = new LinkedHashMap<>();
        endInv(player).ifPresent(inv -> inv.getItemMap().forEach((key, state) -> {
            if (state.count() > 0) out.put(key.toStack(1), state.count());
        }));
        return out;
    }

    private static Optional<EndlessInventory> endInv(ServerPlayer player) {
        return ServerLevelEndInv.getEndInvForPlayer(player);
    }
}
