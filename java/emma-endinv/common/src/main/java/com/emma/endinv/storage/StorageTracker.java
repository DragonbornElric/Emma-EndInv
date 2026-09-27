package com.emma.endinv.storage;

import com.emma.endinv.ModInfo;
import com.emma.endinv.manage.EndInvManager;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemStackLike;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side logic of the storage tracker: tagging containers with a {@link StorageTag}, keeping each tracked
 * container's snapshot current, and answering the client screen.
 *
 * <p>Contents are kept current by polling: every {@link #REFRESH_INTERVAL_TICKS} each tracked container in a loaded
 * chunk is re-read and its snapshot replaced when it differs. Polling catches every kind of change (players, hoppers,
 * droppers, other mods) with one loader-neutral code path; a container in an unloaded chunk cannot change, so its
 * last snapshot stays valid. A container whose block is gone is dropped from the index on its next poll.
 *
 * <p>Fabric and NeoForge drive {@link #tick} from the server tick. Folia polls per chunk on the owning region thread
 * and calls {@link #refresh} directly.
 */
public final class StorageTracker {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int REFRESH_INTERVAL_TICKS = 20;
    public static final int SAVE_INTERVAL_TICKS = 20 * 60;
    /** Encoded-size budget per index packet, well under the 1 MiB custom payload limit. */
    private static final int PART_BYTE_BUDGET = 256 * 1024;

    private static int tickCounter;

    private StorageTracker() {}

    // ── Ticking (Fabric / NeoForge) ─────────────────────────────────────────

    public static void tick(MinecraftServer server) {
        if (!StorageIndex.isLoaded()) return;
        tickCounter++;
        if (tickCounter % REFRESH_INTERVAL_TICKS == 0) {
            for (TrackedContainer container : List.copyOf(StorageIndex.all())) {
                ServerLevel level = server.getLevel(container.dimension());
                if (level != null) refresh(level, container);
            }
        }
        if (tickCounter % SAVE_INTERVAL_TICKS == 0) {
            StorageIndex.saveIfDirty();
        }
    }

    // ── Tagging ─────────────────────────────────────────────────────────────

    /**
     * The Storage Tag this hand interaction uses, or empty. An off-hand tag also counts for the main-hand
     * interaction when the main hand is empty, since vanilla would otherwise open the container with the empty hand.
     */
    public static ItemStack tagFor(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (StorageTag.isTag(held)) return held;
        if (hand == InteractionHand.MAIN_HAND && held.isEmpty()) {
            ItemStack off = player.getOffhandItem();
            if (StorageTag.isTag(off)) return off;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Whether a right-click on {@code pos} should be taken over by the tracker instead of opening the container.
     * Safe to call on both sides; loaders cancel the vanilla interaction when this is true and, on the server,
     * then call {@link #onUseBlock}.
     */
    public static boolean shouldIntercept(Player player, InteractionHand hand, Level level, BlockPos pos) {
        if (player.isSpectator() || tagFor(player, hand).isEmpty()) return false;
        return level.getBlockEntity(pos) instanceof Container;
    }

    public static void onUseBlock(ServerPlayer player, InteractionHand hand, BlockPos clicked) {
        if (!StorageIndex.isLoaded()) return;
        ServerLevel level = player.level();
        ItemStack tag = tagFor(player, hand);
        Container container = containerAt(level, clicked);
        if (tag.isEmpty() || container == null) return;

        BlockState state = level.getBlockState(clicked);
        BlockPos pos = canonical(level, clicked, state);
        StorageIndex.Key key = new StorageIndex.Key(level.dimension(), pos.asLong());
        TrackedContainer existing = findTracked(level, clicked, state);

        if (player.isShiftKeyDown()) {
            if (existing == null) {
                player.sendOverlayMessage(Component.literal("This container is not tracked.").withStyle(ChatFormatting.GRAY));
            } else if (!canModify(player, existing)) {
                player.sendOverlayMessage(Component.literal("Only " + existing.ownerName() + " or an admin can stop tracking this.")
                        .withStyle(ChatFormatting.RED));
            } else {
                StorageIndex.remove(existing.key());
                player.sendSystemMessage(Component.literal("Stopped tracking " + existing.displayName() + " at " + coords(existing.pos()) + ".")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return;
        }

        String label = tag.has(DataComponents.CUSTOM_NAME) ? tag.getHoverName().getString() : "";
        TrackedContainer entry;
        boolean added = existing == null;
        if (added) {
            entry = new TrackedContainer(level.dimension(), pos, Optional.empty(), "", label,
                    player.getUUID(), player.getName().getString(), 0L, List.of(), List.of());
        } else {
            entry = existing;
            if (!existing.key().equals(key)) StorageIndex.remove(existing.key());
            if (!label.isEmpty() && canModify(player, existing)) entry = entry.withLabel(label);
        }
        entry = snapshot(level, entry.withPos(pos, entry.partner()), container, state);
        StorageIndex.put(entry);

        String summary = entry.items().size() + " item types, " + entry.totalItems() + " items";
        if (added) {
            player.sendSystemMessage(Component.literal("Now tracking " + entry.displayName() + " at " + coords(pos) + " (" + summary + ").")
                    .withStyle(ChatFormatting.GREEN));
            LOGGER.info("Storage tracker: {} tracked {} at {} in {}", player.getName().getString(), entry.displayName(), coords(pos),
                    level.dimension().identifier());
        } else {
            player.sendOverlayMessage(Component.literal(entry.displayName() + ": " + summary + " — sneak + right-click to stop tracking")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    // ── Refreshing ──────────────────────────────────────────────────────────

    /**
     * Re-read one tracked container if its chunk is loaded. Must run on the thread that owns the chunk
     * (the server thread, or the region thread on Folia).
     */
    public static void refresh(ServerLevel level, TrackedContainer entry) {
        BlockPos pos = entry.pos();
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        Container container = containerAt(level, pos);
        if (container == null) {
            if (state.hasBlockEntity() && level.getBlockEntity(pos) == null) return; // block entity not ready yet
            // The tracked half of a double chest was broken: keep tracking the half that is left.
            BlockPos partner = entry.partner().orElse(null);
            if (partner != null && level.isLoaded(partner) && containerAt(level, partner) != null) {
                StorageIndex.remove(entry.key());
                BlockState partnerState = level.getBlockState(partner);
                StorageIndex.put(snapshot(level, entry.withPos(canonical(level, partner, partnerState), Optional.empty()),
                        containerAt(level, partner), partnerState));
                return;
            }
            StorageIndex.remove(entry.key());
            LOGGER.info("Storage tracker: {} at {} is gone, no longer tracked", entry.displayName(), coords(pos));
            return;
        }
        BlockPos canonical = canonical(level, pos, state);
        if (!canonical.equals(pos)) {
            // A second chest joined this one and the canonical half changed; re-key (replacing any entry of the other half).
            StorageIndex.remove(entry.key());
            StorageIndex.put(snapshot(level, entry.withPos(canonical, entry.partner()), container, state));
            return;
        }
        TrackedContainer updated = snapshot(level, entry, container, state);
        if (updated != entry) StorageIndex.put(updated);
    }

    /** The entry with fresh contents, or {@code entry} itself (same instance) when nothing changed. */
    private static TrackedContainer snapshot(ServerLevel level, TrackedContainer entry, Container container, BlockState state) {
        Map<ItemKey, Integer> items = new LinkedHashMap<>();
        Map<ItemKey, Integer> nested = new LinkedHashMap<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            DataComponentPatch patch = stack.getComponentsPatch();
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) {
                contents.nonEmptyItemCopyStream().forEach(inner -> nested.merge(ItemKey.asKey(inner), inner.getCount(), Integer::sum));
                // The box's own contents are indexed as nested items; keeping them in the key too would make every
                // shulker box unique and bloat the index.
                patch = patch.forget(type -> type == DataComponents.CONTAINER);
            }
            items.merge(new ItemKey(stack.getItem(), patch), stack.getCount(), Integer::sum);
        }
        List<ItemStackLike> itemList = sorted(items);
        List<ItemStackLike> nestedList = sorted(nested);
        Optional<BlockPos> partner = partnerOf(level, entry.pos(), level.getBlockState(entry.pos()));
        String blockName = nameOf(level, entry.pos(), state);
        if (itemList.equals(entry.items()) && nestedList.equals(entry.nested())
                && partner.equals(entry.partner()) && blockName.equals(entry.blockName())) {
            return entry;
        }
        return entry.withContents(partner, blockName, itemList, nestedList, System.currentTimeMillis());
    }

    private static List<ItemStackLike> sorted(Map<ItemKey, Integer> counts) {
        List<ItemStackLike> list = new ArrayList<>(counts.size());
        counts.forEach((key, count) -> list.add(ItemStackLike.asKey(key, count)));
        list.sort(Comparator.comparingInt(ItemStackLike::count).reversed());
        return list;
    }

    // ── Block helpers ───────────────────────────────────────────────────────

    /** The container at {@code pos}; for a double chest, the combined 54-slot container. */
    @Nullable
    public static Container containerAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            Container combined = ChestBlock.getContainer(chest, state, level, pos, true);
            if (combined != null) return combined;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container container ? container : null;
    }

    private static Optional<BlockPos> partnerOf(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return Optional.empty();
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        BlockState otherState = level.getBlockState(other);
        if (otherState.getBlock() != state.getBlock() || otherState.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                || !other.relative(ChestBlock.getConnectedDirection(otherState)).equals(pos)) {
            return Optional.empty();
        }
        return Optional.of(other);
    }

    /** Both halves of a double chest map to the same entry: the lower of the two positions. */
    private static BlockPos canonical(Level level, BlockPos pos, BlockState state) {
        return partnerOf(level, pos, state).filter(other -> other.compareTo(pos) < 0).orElse(pos);
    }

    @Nullable
    private static TrackedContainer findTracked(Level level, BlockPos pos, BlockState state) {
        TrackedContainer direct = StorageIndex.get(new StorageIndex.Key(level.dimension(), canonical(level, pos, state).asLong()));
        if (direct != null) return direct;
        // Tracked before the chest became a double chest: the entry may still sit under either half.
        TrackedContainer here = StorageIndex.get(new StorageIndex.Key(level.dimension(), pos.asLong()));
        if (here != null) return here;
        Optional<BlockPos> partner = partnerOf(level, pos, state);
        return partner.map(p -> StorageIndex.get(new StorageIndex.Key(level.dimension(), p.asLong()))).orElse(null);
    }

    private static String nameOf(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof Nameable nameable && nameable.hasCustomName()) {
            return nameable.getName().getString();
        }
        return state.getBlock().getName().getString();
    }

    // ── Requests from the client screen ─────────────────────────────────────

    public static boolean canModify(ServerPlayer player, TrackedContainer container) {
        return player.getUUID().equals(container.owner()) || EndInvManager.isAdmin(player);
    }

    public static void handleRequest(ServerPlayer player, StorageRequestPayload request) {
        if (!StorageIndex.isLoaded()) {
            sendIndex(player, "The storage index is not loaded yet.");
            return;
        }
        String message = switch (request.action()) {
            case LIST -> "";
            case UNTRACK -> {
                StorageIndex.Key key = new StorageIndex.Key(request.dimension(), request.pos().asLong());
                TrackedContainer target = StorageIndex.get(key);
                if (target == null) yield "That container is no longer tracked.";
                if (!canModify(player, target)) yield "Only " + target.ownerName() + " or an admin can stop tracking that container.";
                StorageIndex.remove(key);
                LOGGER.info("Storage tracker: {} untracked {} at {}", player.getName().getString(), target.displayName(), coords(target.pos()));
                yield "Stopped tracking " + target.displayName() + " at " + coords(target.pos()) + ".";
            }
        };
        sendIndex(player, message);
    }

    /** Send the whole index to one player, split into parts that fit the payload size limit. */
    public static void sendIndex(ServerPlayer player, String message) {
        boolean admin = EndInvManager.isAdmin(player);
        List<TrackedContainer> all = List.copyOf(StorageIndex.all());
        List<List<TrackedContainer>> parts = new ArrayList<>();
        List<TrackedContainer> current = new ArrayList<>();
        RegistryFriendlyByteBuf scratch = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().getServer().registryAccess());
        try {
            int bytes = 0;
            for (TrackedContainer container : all) {
                scratch.clear();
                TrackedContainer.encode(scratch, container);
                int size = scratch.readableBytes();
                if (!current.isEmpty() && bytes + size > PART_BYTE_BUDGET) {
                    parts.add(current);
                    current = new ArrayList<>();
                    bytes = 0;
                }
                current.add(container);
                bytes += size;
            }
        } finally {
            scratch.release();
        }
        parts.add(current);
        for (int i = 0; i < parts.size(); i++) {
            ModInfo.getPacketDistributor().sendToPlayer(player,
                    new StorageIndexPayload(i, i == parts.size() - 1, admin, message, parts.get(i)));
        }
    }

    // ── Search (used by the /storage command) ───────────────────────────────

    public record Hit(TrackedContainer container, ItemStackLike item, boolean nested) {}

    /** Every (container, item) pair whose item matches {@code query}, using the EndInv search syntax. */
    public static List<Hit> search(String query) {
        List<Hit> hits = new ArrayList<>();
        for (TrackedContainer container : StorageIndex.all()) {
            for (ItemStackLike like : container.items()) {
                if (com.emma.endinv.util.SearchUtil.matchesSearch(like.toKey().toStack(1), query)) hits.add(new Hit(container, like, false));
            }
            for (ItemStackLike like : container.nested()) {
                if (com.emma.endinv.util.SearchUtil.matchesSearch(like.toKey().toStack(1), query)) hits.add(new Hit(container, like, true));
            }
        }
        return hits;
    }

    public static String coords(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
