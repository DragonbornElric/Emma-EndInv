package com.emma.endinv.manage;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.ModInfo;
import com.emma.endinv.ModRegistries;
import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.data.EndlessInventoryData;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.network.payloads.toClient.EndInvContent;
import com.emma.endinv.network.payloads.toClient.EndInvDetailPayload;
import com.emma.endinv.network.payloads.toClient.EndInvListPayload;
import com.emma.endinv.network.payloads.toClient.EndInvMetadata;
import com.emma.endinv.network.payloads.toClient.SetStarredPagePayload;
import com.emma.endinv.network.payloads.toServer.ManageEndInvPayload;
import com.emma.endinv.options.ServerConfigs;
import com.emma.endinv.util.Accessibility;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemStackLike;
import com.emma.endinv.util.ItemState;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side logic behind the inventory manager screen and the matching {@code /endinv} commands.
 *
 * <p>Permissions:
 * <ul>
 *     <li>Admins ({@link #isAdmin}) can see and act on every EndInv.</li>
 *     <li>Owners can change who their own EndInv is shared with.</li>
 *     <li>Anyone can select (use) an EndInv they already have access to.</li>
 *     <li>Take / move / clear / delete are admin-only and write a snapshot of the source first.</li>
 * </ul>
 */
public final class EndInvManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Limits for one detail view; larger inventories are truncated to the biggest stacks. */
    private static final int DETAIL_LIMIT = 2000;
    /** Encoded-size budget for the detail view, well under the 1 MiB custom payload limit (items with large components such as filled shulker boxes add up quickly). */
    private static final int DETAIL_BYTE_BUDGET = 512 * 1024;

    public enum Action {
        LIST,
        INSPECT,
        SELECT,
        SET_ACCESS,
        WHITELIST_ADD,
        WHITELIST_REMOVE,
        TAKE_ALL,
        MOVE_ALL,
        CLEAR,
        DELETE
    }

    public record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }

    private EndInvManager() {}

    // ── Permissions ─────────────────────────────────────────────────────────

    /**
     * Admin = listed in the {@code Admins} server config (by name or UUID). When that list is empty,
     * operators with permission level 4 are admins instead.
     */
    public static boolean isAdmin(ServerPlayer player) {
        List<String> admins = ServerConfigs.ADMINS.get();
        if (admins == null || admins.isEmpty()) {
            return Commands.hasPermission(Commands.LEVEL_OWNERS).test(player.createCommandSourceStack());
        }
        String name = player.getName().getString();
        String uuid = player.getUUID().toString();
        for (Object entry : admins) {
            String admin = String.valueOf(entry).trim();
            if (admin.equalsIgnoreCase(name) || admin.equalsIgnoreCase(uuid)) return true;
        }
        return false;
    }

    public static boolean canView(ServerPlayer player, EndlessInventory endInv) {
        return isAdmin(player) || endInv.accessible(player);
    }

    public static boolean canShare(ServerPlayer player, EndlessInventory endInv) {
        return isAdmin(player) || endInv.isOwner(player);
    }

    // ── Packet entry point ──────────────────────────────────────────────────

    public static void handle(ServerPlayer player, ManageEndInvPayload request) {
        EndlessInventoryData data = ServerLevelEndInv.levelEndInvData;
        if (data == null) {
            send(player, new EndInvListPayload(false, List.of(), "EndInv data is not loaded yet."));
            return;
        }
        EndlessInventory target = data.fromUUID(request.target());
        Result result = switch (request.action()) {
            case LIST -> Result.ok("");
            case INSPECT -> inspect(player, target);
            case SELECT -> select(player, target);
            case SET_ACCESS -> setAccess(player, target, request.arg());
            case WHITELIST_ADD -> whitelist(player, target, request.arg(), true);
            case WHITELIST_REMOVE -> whitelist(player, target, request.arg(), false);
            case TAKE_ALL -> takeAll(player, target);
            case MOVE_ALL -> moveAll(player, target, data.fromUUID(request.other()));
            case CLEAR -> clear(player, target);
            case DELETE -> delete(player, target);
        };
        if (!result.message().isEmpty()) {
            LOGGER.info("EndInv manager: {} {} {} -> {}", player.getName().getString(), request.action(), request.target(), result.message());
        }
        if (request.action() == Action.SELECT && !result.message().isEmpty()) {
            // The client closes its screens right after asking, so report the outcome in chat too.
            player.sendSystemMessage(Component.literal(result.message()));
        }
        send(player, buildList(player, result.message()));
        // Keep the open detail view in sync after actions that change contents.
        if (target != null && result.success() && request.action() != Action.INSPECT && request.action() != Action.DELETE
                && canView(player, target)) {
            send(player, detail(player.level().getServer(), target));
        }
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    private static Result inspect(ServerPlayer player, @Nullable EndlessInventory target) {
        if (target == null) return Result.fail("That Endless Inventory no longer exists.");
        if (!canView(player, target)) return Result.fail("You do not have access to that Endless Inventory.");
        send(player, detail(player.level().getServer(), target));
        return Result.ok("");
    }

    public static Result select(ServerPlayer player, @Nullable EndlessInventory target) {
        EndlessInventoryData data = ServerLevelEndInv.levelEndInvData;
        if (data == null || target == null) return Result.fail("That Endless Inventory no longer exists.");
        if (!canView(player, target)) return Result.fail("You do not have access to that Endless Inventory.");
        assign(player, target);
        return Result.ok("Now using " + describe(player.level().getServer(), target) + ".");
    }

    /** Make {@code target} the player's EndInv (persisted) without a permission check. */
    public static void assign(ServerPlayer player, EndlessInventory target) {
        EndlessInventoryData data = ServerLevelEndInv.levelEndInvData;
        if (data == null) return;
        data.setSelection(player.getUUID(), target.getUuid());
        ModRegistries.NbtAttachments.getEndInvUUID().setTo(player, target.getUuid());
        refreshPlayer(player);
    }

    private static Result setAccess(ServerPlayer player, @Nullable EndlessInventory target, String arg) {
        if (target == null) return Result.fail("That Endless Inventory no longer exists.");
        if (!canShare(player, target)) return Result.fail("Only the owner or an admin can change sharing.");
        Accessibility access;
        try {
            access = Accessibility.valueOf(arg);
        } catch (IllegalArgumentException e) {
            return Result.fail("Unknown access level " + arg);
        }
        target.setAccessibility(access);
        return Result.ok("Set " + describe(player.level().getServer(), target) + " to " + access + ".");
    }

    private static Result whitelist(ServerPlayer player, @Nullable EndlessInventory target, String name, boolean add) {
        if (target == null) return Result.fail("That Endless Inventory no longer exists.");
        if (!canShare(player, target)) return Result.fail("Only the owner or an admin can change sharing.");
        MinecraftServer server = player.level().getServer();
        UUID uuid = resolvePlayer(server, name.trim());
        if (uuid == null) return Result.fail("Unknown player '" + name + "'. They need to have joined this server at least once.");
        String shown = nameOf(server, uuid);
        if (add) {
            if (target.white_list.contains(uuid)) return Result.fail(shown + " is already on the list.");
            target.addToWhitelist(uuid);
            String hint = target.getAccessibility() == Accessibility.RESTRICTED ? "" : " (only applies while access is RESTRICTED)";
            return Result.ok("Shared with " + shown + hint + ".");
        }
        if (!target.white_list.contains(uuid)) return Result.fail(shown + " is not on the list.");
        target.removeFromWhitelist(uuid);
        return Result.ok("Stopped sharing with " + shown + ".");
    }

    private static Result takeAll(ServerPlayer player, @Nullable EndlessInventory source) {
        if (!isAdmin(player)) return Result.fail("Only admins can take from another Endless Inventory.");
        if (source == null) return Result.fail("That Endless Inventory no longer exists.");
        EndlessInventory own = ServerLevelEndInv.getEndInvForPlayer(player).orElse(null);
        if (own == null) return Result.fail("You have no Endless Inventory to take into.");
        return moveAll(player, source, own);
    }

    public static Result moveAll(ServerPlayer player, @Nullable EndlessInventory source, @Nullable EndlessInventory dest) {
        if (!isAdmin(player)) return Result.fail("Only admins can move items between Endless Inventories.");
        if (source == null || dest == null) return Result.fail("That Endless Inventory no longer exists.");
        if (source == dest) return Result.fail("Source and destination are the same Endless Inventory.");
        MinecraftServer server = player.level().getServer();
        String snapshot = EndInvSnapshots.save(server, source, "move");
        if (snapshot == null) return Result.fail("Could not write a safety snapshot; nothing was moved.");
        long moved = 0, left = 0;
        for (Map.Entry<ItemKey, ItemState> entry : source.getItemMap().entrySet()) {
            ItemKey key = entry.getKey();
            int count = entry.getValue().count();
            int remaining = dest.addItem(key, count).getCount();
            int accepted = count - remaining;
            if (accepted > 0) source.takeItem(key, accepted);
            moved += accepted;
            left += remaining;
        }
        source.setChanged();
        dest.setChanged();
        String msg = "Moved " + moved + " items from " + describe(server, source) + " to " + describe(server, dest) + ".";
        if (left > 0) msg += " " + left + " items did not fit and stayed behind.";
        return Result.ok(msg + " Snapshot: " + snapshot);
    }

    public static Result clear(ServerPlayer player, @Nullable EndlessInventory target) {
        if (!isAdmin(player)) return Result.fail("Only admins can clear an Endless Inventory.");
        if (target == null) return Result.fail("That Endless Inventory no longer exists.");
        MinecraftServer server = player.level().getServer();
        String snapshot = EndInvSnapshots.save(server, target, "clear");
        if (snapshot == null) return Result.fail("Could not write a safety snapshot; nothing was cleared.");
        target.clearContent();
        return Result.ok("Cleared " + describe(server, target) + ". Snapshot: " + snapshot);
    }

    public static Result delete(ServerPlayer player, @Nullable EndlessInventory target) {
        EndlessInventoryData data = ServerLevelEndInv.levelEndInvData;
        if (!isAdmin(player)) return Result.fail("Only admins can delete an Endless Inventory.");
        if (data == null || target == null) return Result.fail("That Endless Inventory no longer exists.");
        MinecraftServer server = player.level().getServer();
        // Other online players may hold this object in an open menu; deleting it under them would drop
        // whatever they put in afterwards. Ask for it to be freed first (clearing is still allowed).
        for (UUID viewer : target.viewerIds) {
            if (viewer.equals(player.getUUID())) continue;
            ServerPlayer other = server.getPlayerList().getPlayer(viewer);
            if (other != null) {
                return Result.fail(other.getName().getString() + " is online and using it. Switch them to another inventory first, or clear it instead.");
            }
        }
        String snapshot = EndInvSnapshots.save(server, target, "delete");
        if (snapshot == null) return Result.fail("Could not write a safety snapshot; nothing was deleted.");
        String described = describe(server, target);
        boolean wasOwn = target.viewerIds.contains(player.getUUID());
        target.notifyViewersRemoved(server);
        data.byIndexRemove(data.getIndex(target));
        if (wasOwn) {
            ModRegistries.NbtAttachments.getEndInvUUID().setTo(player, ModInfo.DEFAULT_UUID);
            refreshPlayer(player);
        }
        return Result.ok("Deleted " + described + ". Snapshot: " + snapshot);
    }

    // ── Views ───────────────────────────────────────────────────────────────

    public static EndInvListPayload buildList(ServerPlayer player, String message) {
        EndlessInventoryData data = ServerLevelEndInv.levelEndInvData;
        boolean admin = isAdmin(player);
        if (data == null) return new EndInvListPayload(admin, List.of(), message);
        MinecraftServer server = player.level().getServer();
        UUID current = ServerLevelEndInv.getEndInvForPlayer(player).map(EndlessInventory::getUuid).orElse(null);
        List<EndInvSummary> entries = new ArrayList<>();
        for (int i = 0; i < data.levelEndInvs.size(); i++) {
            EndlessInventory endInv = data.levelEndInvs.get(i);
            boolean viewable = admin || endInv.accessible(player);
            if (!viewable) continue;
            Map<ItemKey, ItemState> items = endInv.getItemMap();
            long total = 0;
            for (ItemState state : items.values()) total += state.count();
            entries.add(new EndInvSummary(
                    endInv.getUuid(),
                    i,
                    endInv.getOwnerUUID() == null ? "" : nameOf(server, endInv.getOwnerUUID()),
                    endInv.getAccessibility(),
                    endInv.white_list.stream().map(uuid -> nameOf(server, uuid)).toList(),
                    usersOf(server, data, endInv),
                    items.size(),
                    total,
                    Objects.equals(endInv.getUuid(), current),
                    true,
                    canShare(player, endInv),
                    admin));
        }
        return new EndInvListPayload(admin, entries, message);
    }

    private static EndInvDetailPayload detail(MinecraftServer server, EndlessInventory endInv) {
        List<Map.Entry<ItemKey, ItemState>> entries = new ArrayList<>(endInv.getItemMap().entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<ItemKey, ItemState> e) -> e.getValue().count()).reversed());
        List<ItemStackLike> items = new ArrayList<>();
        RegistryFriendlyByteBuf sizing = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            for (Map.Entry<ItemKey, ItemState> e : entries) {
                if (items.size() >= DETAIL_LIMIT) break;
                ItemStackLike like = ItemStackLike.asKey(e.getKey(), e.getValue().count());
                ItemStackLike.STREAM_CODEC.encode(sizing, like);
                if (sizing.writerIndex() > DETAIL_BYTE_BUDGET) break;
                items.add(like);
            }
        } finally {
            sizing.release();
        }
        return new EndInvDetailPayload(endInv.getUuid(), items, entries.size());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Point the player at whatever {@link ServerLevelEndInv#getEndInvForPlayer} now resolves to:
     * drop cached menus/monitors bound to the old inventory and resend contents.
     */
    public static void refreshPlayer(ServerPlayer player) {
        EndlessInventoryData data = ServerLevelEndInv.levelEndInvData;
        if (data == null) return;
        for (EndlessInventory endInv : data.levelEndInvs) {
            endInv.viewerIds.remove(player.getUUID());
        }
        ServerLevelEndInv.PAGE_META_DATA_MANAGER.remove(player);
        if (player.containerMenu instanceof EndlessInventoryMenu) {
            player.closeContainer();
        }
        ServerLevelEndInv.getEndInvForPlayer(player).ifPresent(endInv -> {
            send(player, new EndInvContent(endInv.getItemMap()));
            send(player, EndInvMetadata.getWith(endInv));
            send(player, new SetStarredPagePayload(endInv.getStarredItems()));
        });
    }

    private static List<String> usersOf(MinecraftServer server, EndlessInventoryData data, EndlessInventory endInv) {
        Set<String> names = new LinkedHashSet<>();
        for (UUID uuid : data.playersUsing(endInv.getUuid())) names.add(nameOf(server, uuid));
        for (UUID uuid : endInv.viewerIds) {
            if (server.getPlayerList().getPlayer(uuid) != null) names.add(nameOf(server, uuid));
        }
        return new ArrayList<>(names);
    }

    public static String nameOf(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        EndlessInventoryData data = ServerLevelEndInv.levelEndInvData;
        String known = data == null ? null : data.getKnownName(uuid);
        return known != null ? known : uuid.toString().substring(0, 8);
    }

    public static String describe(MinecraftServer server, EndlessInventory endInv) {
        EndlessInventoryData data = ServerLevelEndInv.levelEndInvData;
        int index = data == null ? -1 : data.getIndex(endInv);
        String owner = endInv.getOwnerUUID() == null ? "shared" : nameOf(server, endInv.getOwnerUUID()) + "'s";
        return "#" + index + " (" + owner + ")";
    }

    @Nullable
    private static UUID resolvePlayer(MinecraftServer server, String name) {
        if (name.isEmpty()) return null;
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        EndlessInventoryData data = ServerLevelEndInv.levelEndInvData;
        UUID known = data == null ? null : data.findKnownPlayer(name);
        if (known != null) return known;
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void send(ServerPlayer player, com.emma.endinv.network.payloads.ModPacketPayload payload) {
        ModInfo.getPacketDistributor().sendToPlayer(player, payload);
    }
}
