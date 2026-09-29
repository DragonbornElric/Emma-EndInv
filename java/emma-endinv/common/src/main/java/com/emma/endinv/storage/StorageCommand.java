package com.emma.endinv.storage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /storage} — search the storage index from chat, for players without the client mod (e.g. vanilla clients
 * on Folia) and for quick lookups.
 * <ul>
 *     <li>{@code /storage find <item>} — where an item is stored, nearest first (EndInv search syntax: {@code #tag}, {@code @mod}, …)</li>
 *     <li>{@code /storage list} — the nearest tracked containers</li>
 *     <li>{@code /storage tag [count]} — give Storage Tags (game masters only; players can craft them from paper + chest)</li>
 * </ul>
 */
public final class StorageCommand {

    private static final int MAX_LINES = 10;

    private StorageCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("storage")
                .then(Commands.literal("find")
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .executes(ctx -> find(ctx.getSource(), StringArgumentType.getString(ctx, "item")))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("tag")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> give(ctx.getSource(), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> give(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    private static int find(CommandSourceStack source, String query) {
        List<StorageTracker.Hit> hits = StorageTracker.search(query);
        if (hits.isEmpty()) {
            source.sendFailure(Component.literal("No tracked container holds \"" + query + "\"."));
            return 0;
        }
        // Total per item, then one line per location, nearest first.
        Map<String, Long> totals = new LinkedHashMap<>();
        for (StorageTracker.Hit hit : hits) {
            totals.merge(hit.item().toKey().toStack(1).getHoverName().getString(), (long) hit.item().count(), Long::sum);
        }
        StringBuilder header = new StringBuilder("Found ");
        int shown = 0;
        for (var e : totals.entrySet()) {
            if (shown++ > 0) header.append(", ");
            if (shown > 4) { header.append("…"); break; }
            header.append(e.getValue()).append(" × ").append(e.getKey());
        }
        source.sendSuccess(() -> Component.literal(header.toString()).withStyle(ChatFormatting.GREEN), false);

        Vec3 origin = source.getPosition();
        List<StorageTracker.Hit> sorted = hits.stream()
                .sorted(Comparator.comparingDouble(hit -> distanceSq(source, hit.container(), origin)))
                .toList();
        for (int i = 0; i < Math.min(MAX_LINES, sorted.size()); i++) {
            StorageTracker.Hit hit = sorted.get(i);
            String line = "  " + hit.item().count() + " × " + hit.item().toKey().toStack(1).getHoverName().getString()
                    + (hit.nested() ? " (in a box)" : "")
                    + " — " + describe(source, hit.container(), origin);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        if (sorted.size() > MAX_LINES) {
            int more = sorted.size() - MAX_LINES;
            source.sendSuccess(() -> Component.literal("  …and " + more + " more.").withStyle(ChatFormatting.GRAY), false);
        }
        return hits.size();
    }

    private static int list(CommandSourceStack source) {
        List<TrackedContainer> all = List.copyOf(StorageIndex.all());
        if (all.isEmpty()) {
            source.sendFailure(Component.literal("No containers are tracked yet. Right-click one with a Storage Tag."));
            return 0;
        }
        Vec3 origin = source.getPosition();
        source.sendSuccess(() -> Component.literal(all.size() + " tracked containers, nearest first:").withStyle(ChatFormatting.GREEN), false);
        all.stream()
                .sorted(Comparator.comparingDouble(c -> distanceSq(source, c, origin)))
                .limit(MAX_LINES)
                .forEach(c -> {
                    String line = "  " + describe(source, c, origin) + " — " + c.items().size() + " types, " + c.totalItems() + " items";
                    source.sendSuccess(() -> Component.literal(line), false);
                });
        return all.size();
    }

    private static int give(CommandSourceStack source, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var stack = StorageTag.create(count);
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
        source.sendSuccess(() -> Component.literal("Gave " + count + " Storage Tag" + (count == 1 ? "" : "s") + "."), false);
        return count;
    }

    private static String describe(CommandSourceStack source, TrackedContainer c, Vec3 origin) {
        String where = c.displayName() + " at " + StorageTracker.coords(c.pos());
        if (!c.dimension().equals(source.getLevel().dimension())) {
            return where + " (" + c.dimension().identifier().getPath() + ")";
        }
        return where + " (" + Math.round(Math.sqrt(distanceSq(source, c, origin))) + " m)";
    }

    private static double distanceSq(CommandSourceStack source, TrackedContainer c, Vec3 origin) {
        if (!c.dimension().equals(source.getLevel().dimension())) return Double.MAX_VALUE;
        return Vec3.atCenterOf(c.pos()).distanceToSqr(origin);
    }
}
