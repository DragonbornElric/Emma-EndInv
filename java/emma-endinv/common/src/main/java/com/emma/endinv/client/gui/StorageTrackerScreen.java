package com.emma.endinv.client.gui;

import com.emma.endinv.ModInfo;
import com.emma.endinv.storage.StorageIndexPayload;
import com.emma.endinv.storage.StorageRequestPayload;
import com.emma.endinv.storage.StorageTracker;
import com.emma.endinv.storage.TrackedContainer;
import com.emma.endinv.util.ItemKey;
import com.emma.endinv.util.ItemStackLike;
import com.emma.endinv.util.SearchUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Searchable view of the server's storage index: every container tagged with a Storage Tag and what it holds.
 *
 * <p>"By item" lists every stored item type with its total; selecting one lists where it is, nearest first.
 * "By container" lists the tracked containers; selecting one shows its contents. The server sends the full index
 * ({@link StorageIndexPayload}) when the screen opens and after every request; filtering happens here.
 */
public class StorageTrackerScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int CELL = 18;

    private static final int COLOR_PANEL = 0xC0101010;
    private static final int COLOR_ROW_SELECTED = 0x80406080;
    private static final int COLOR_ROW_HOVER = 0x40FFFFFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFFA0A0A0;
    private static final int COLOR_ACCENT = 0xFF55FF55;
    private static final int COLOR_WARN = 0xFFFFAA55;

    // ── Index received from the server (client thread only) ─────────────────

    private static final List<TrackedContainer> received = new ArrayList<>();
    private static List<TrackedContainer> index = List.of();
    private static boolean loaded;
    private static boolean admin;
    private static String serverMessage = "";
    private static int indexVersion;

    public static void onIndex(StorageIndexPayload payload) {
        if (payload.part() == 0) received.clear();
        received.addAll(payload.containers());
        if (!payload.last()) return;
        index = List.copyOf(received);
        received.clear();
        loaded = true;
        admin = payload.admin();
        serverMessage = payload.message();
        indexVersion++;
        if (Minecraft.getInstance().screen instanceof StorageTrackerScreen screen) screen.indexUpdated();
    }

    /** The last full index received, for other client code (e.g. {@code EmmaEndInvApi}). Empty until requested once. */
    public static List<TrackedContainer> lastIndex() {
        return index;
    }

    public static void requestIndex() {
        ModInfo.getPacketDistributor().sendToServer(StorageRequestPayload.list());
    }

    // ── View model ──────────────────────────────────────────────────────────

    private enum Mode { ITEMS, CONTAINERS }

    /** One location of an item: {@code nested} when it sits inside a shulker box in that container. */
    private record Location(TrackedContainer container, int count, boolean nested) {}

    private record ItemGroup(ItemKey key, ItemStack icon, long total, List<Location> locations) {}

    private static Mode mode = Mode.ITEMS;
    private static String query = "";

    private final Screen parent;
    private List<ItemGroup> groups = List.of();
    private List<TrackedContainer> containers = List.of();
    private final Map<ItemKey, ItemStack> iconCache = new HashMap<>();
    private final Map<ItemKey, Boolean> matchCache = new HashMap<>();
    @Nullable private ItemKey selectedItem;
    @Nullable private TrackedContainer selectedContainer;
    private int builtVersion = -1;
    private int listScroll;
    private int rightScroll;
    private String status = "";

    private int left, top, panelW, panelH, listW, rightX, rightW;
    private int listTop, listBottom, rightTop, rightBottom;
    @Nullable private Button copyButton;
    @Nullable private Button untrackButton;

    public StorageTrackerScreen(Screen parent) {
        super(Component.translatable("title.endinv.storage"));
        this.parent = parent;
        requestIndex();
    }

    private void indexUpdated() {
        rebuildView();
        updateButtons();
    }

    private void rebuildView() {
        builtVersion = indexVersion;
        matchCache.clear();
        Map<ItemKey, List<Location>> byItem = new LinkedHashMap<>();
        List<TrackedContainer> matchingContainers = new ArrayList<>();
        for (TrackedContainer container : index) {
            boolean any = false;
            for (ItemStackLike like : container.items()) {
                if (matches(like.toKey())) {
                    byItem.computeIfAbsent(like.toKey(), k -> new ArrayList<>()).add(new Location(container, like.count(), false));
                    any = true;
                }
            }
            for (ItemStackLike like : container.nested()) {
                if (matches(like.toKey())) {
                    byItem.computeIfAbsent(like.toKey(), k -> new ArrayList<>()).add(new Location(container, like.count(), true));
                    any = true;
                }
            }
            if (any || query.isBlank()) matchingContainers.add(container);
        }
        List<ItemGroup> newGroups = new ArrayList<>(byItem.size());
        byItem.forEach((key, locations) -> {
            locations.sort(Comparator.comparingDouble((Location l) -> distanceSq(l.container())).thenComparing(l -> -l.count()));
            long total = 0;
            for (Location l : locations) total += l.count();
            newGroups.add(new ItemGroup(key, icon(key), total, List.copyOf(locations)));
        });
        newGroups.sort(Comparator.comparingLong(ItemGroup::total).reversed());
        matchingContainers.sort(Comparator.comparingDouble(StorageTrackerScreen::distanceSq));
        groups = newGroups;
        containers = matchingContainers;

        // Keep the selection when it still exists, otherwise select the first row.
        if (selectedItem == null || groups.stream().noneMatch(g -> g.key().equals(selectedItem))) {
            selectedItem = groups.isEmpty() ? null : groups.getFirst().key();
            rightScroll = 0;
        }
        if (selectedContainer != null) {
            TrackedContainer previous = selectedContainer;
            selectedContainer = containers.stream().filter(c -> c.key().equals(previous.key())).findFirst().orElse(null);
        }
        if (selectedContainer == null) {
            selectedContainer = containers.isEmpty() ? null : containers.getFirst();
            rightScroll = 0;
        }
        listScroll = Mth.clamp(listScroll, 0, maxListScroll());
        rightScroll = Mth.clamp(rightScroll, 0, maxRightScroll());
    }

    private boolean matches(ItemKey key) {
        if (query.isBlank()) return true;
        return matchCache.computeIfAbsent(key, k -> SearchUtil.matchesSearch(icon(k), query));
    }

    private ItemStack icon(ItemKey key) {
        return iconCache.computeIfAbsent(key, k -> k.toStack(1));
    }

    // ── Layout & widgets ────────────────────────────────────────────────────

    @Override
    protected void init() {
        panelW = Math.min(width - 16, 480);
        panelH = Math.min(height - 16, 290);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        listW = Math.min(200, panelW * 2 / 5);
        rightX = left + listW + 12;
        rightW = panelW - listW - 18;
        listTop = top + 42;
        listBottom = top + panelH - 28;

        EditBox search = new EditBox(font, left + 6, top + 18, listW, 18, Component.translatable("endinv.storage.search"));
        search.setMaxLength(64);
        search.setHint(Component.translatable("endinv.storage.search"));
        search.setValue(query);
        search.setResponder(value -> {
            if (value.equals(query)) return;
            query = value;
            listScroll = 0;
            rebuildView();
            updateButtons();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        Button modeButton = Button.builder(Component.translatable(mode == Mode.ITEMS ? "endinv.storage.mode.items" : "endinv.storage.mode.containers"),
                        b -> {
                            mode = mode == Mode.ITEMS ? Mode.CONTAINERS : Mode.ITEMS;
                            listScroll = 0;
                            rightScroll = 0;
                            rebuildWidgets();
                        })
                .bounds(rightX, top + 17, 90, 20).build();
        modeButton.setTooltip(Tooltip.create(Component.translatable("endinv.storage.mode.tip")));
        addRenderableWidget(modeButton);

        rightTop = mode == Mode.CONTAINERS ? top + 42 + 46 : top + 42 + 24;
        rightBottom = top + panelH - 28;
        copyButton = Button.builder(Component.translatable("endinv.storage.copy"), b -> {
                    if (selectedContainer != null) copyCoords(selectedContainer);
                })
                .bounds(rightX, top + 42 + 22, 80, 20).build();
        copyButton.setTooltip(Tooltip.create(Component.translatable("endinv.storage.copy.tip")));
        addRenderableWidget(copyButton);
        untrackButton = Button.builder(Component.translatable("endinv.storage.untrack"), b -> {
                    if (selectedContainer != null) confirmUntrack(selectedContainer);
                })
                .bounds(rightX + 84, top + 42 + 22, 90, 20).build();
        addRenderableWidget(untrackButton);

        addRenderableWidget(Button.builder(Component.translatable("endinv.storage.refresh"), b -> requestIndex())
                .bounds(left + panelW - 164, top + panelH - 24, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelW - 82, top + panelH - 24, 76, 20).build());

        if (builtVersion != indexVersion) rebuildView();
        updateButtons();
    }

    /** Show the container buttons only in "By container" mode with a selection; untrack only for its owner or an admin. */
    private void updateButtons() {
        if (copyButton == null || untrackButton == null) return;
        TrackedContainer sel = selectedContainer;
        boolean show = mode == Mode.CONTAINERS && sel != null;
        copyButton.visible = show;
        untrackButton.visible = show;
        untrackButton.active = show && (admin || (minecraft.player != null && minecraft.player.getUUID().equals(sel.owner())));
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(left, top, left + panelW, top + panelH, COLOR_PANEL);
        g.centeredText(font, title, left + panelW / 2, top + 6, COLOR_TEXT);
        g.text(font, font.plainSubstrByWidth(summary(), rightW - 96), rightX + 96, top + 23, COLOR_DIM);

        renderList(g, mouseX, mouseY);
        if (mode == Mode.ITEMS) renderLocations(g, mouseX, mouseY);
        else renderContainer(g, mouseX, mouseY);

        String message = !status.isEmpty() ? status : serverMessage;
        if (!message.isEmpty()) {
            g.textWithWordWrap(font, Component.literal(message), left + 6, top + panelH - 22, panelW - 176, COLOR_WARN);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private String summary() {
        if (!loaded) return "";
        if (mode == Mode.ITEMS) {
            return groups.size() + " item types in " + index.size() + " containers";
        }
        return containers.size() + (query.isBlank() ? "" : " of " + index.size()) + " containers";
    }

    private void renderList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = left + 6;
        g.fill(x - 1, listTop - 1, x + listW + 1, listBottom + 1, 0x60000000);
        if (!loaded) {
            g.text(font, Component.translatable("endinv.storage.loading"), x + 4, listTop + 4, COLOR_DIM);
            return;
        }
        if (index.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("endinv.storage.none"), x + 4, listTop + 4, listW - 8, COLOR_DIM);
            return;
        }
        int rows = mode == Mode.ITEMS ? groups.size() : containers.size();
        if (rows == 0) {
            g.text(font, Component.translatable("endinv.storage.no_match"), x + 4, listTop + 4, COLOR_DIM);
            return;
        }
        g.enableScissor(x, listTop, x + listW, listBottom);
        int first = listScroll / ROW_HEIGHT;
        for (int i = first; i < rows; i++) {
            int y = listTop + i * ROW_HEIGHT - listScroll;
            if (y >= listBottom) break;
            boolean hovered = mouseX >= x && mouseX < x + listW && mouseY >= Math.max(y, listTop) && mouseY < Math.min(y + ROW_HEIGHT, listBottom);
            if (mode == Mode.ITEMS) {
                ItemGroup group = groups.get(i);
                rowBackground(g, x, y, group.key().equals(selectedItem), hovered);
                g.item(group.icon(), x + 2, y + 3);
                int tx = x + 22, tw = listW - 24;
                g.text(font, font.plainSubstrByWidth(group.icon().getHoverName().getString(), tw), tx, y + 2, COLOR_TEXT);
                String where = group.locations().size() == 1 ? "1 place" : group.locations().size() + " places";
                Location nearest = group.locations().getFirst();
                g.text(font, font.plainSubstrByWidth(compact(group.total()) + " · " + where + " · " + distanceText(nearest.container()), tw),
                        tx, y + 12, COLOR_DIM);
            } else {
                TrackedContainer c = containers.get(i);
                rowBackground(g, x, y, selectedContainer != null && c.key().equals(selectedContainer.key()), hovered);
                g.text(font, font.plainSubstrByWidth(c.displayName(), listW - 6), x + 3, y + 2, COLOR_TEXT);
                g.text(font, font.plainSubstrByWidth(StorageTracker.coords(c.pos()) + " · " + distanceText(c) + " · " + c.items().size() + " types",
                        listW - 6), x + 3, y + 12, COLOR_DIM);
            }
        }
        g.disableScissor();
    }

    private void rowBackground(GuiGraphicsExtractor g, int x, int y, boolean selected, boolean hovered) {
        if (selected) g.fill(x, y, x + listW, y + ROW_HEIGHT, COLOR_ROW_SELECTED);
        else if (hovered) g.fill(x, y, x + listW, y + ROW_HEIGHT, COLOR_ROW_HOVER);
    }

    /** "By item": where the selected item is, one row per container, nearest first. Clicking a row copies its coordinates. */
    private void renderLocations(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ItemGroup group = selectedGroup();
        if (group == null) return;
        int y0 = top + 42;
        g.item(group.icon(), rightX, y0);
        g.text(font, font.plainSubstrByWidth(group.icon().getHoverName().getString(), rightW - 22), rightX + 20, y0, COLOR_TEXT);
        g.text(font, font.plainSubstrByWidth("Total " + group.total() + " in " + group.locations().size()
                + (group.locations().size() == 1 ? " container" : " containers") + " — click a row to copy its coordinates", rightW - 22),
                rightX + 20, y0 + 10, COLOR_DIM);

        g.fill(rightX - 1, rightTop - 1, rightX + rightW + 1, rightBottom + 1, 0x60000000);
        g.enableScissor(rightX, rightTop, rightX + rightW, rightBottom);
        List<Location> locations = group.locations();
        for (int i = rightScroll / ROW_HEIGHT; i < locations.size(); i++) {
            int y = rightTop + i * ROW_HEIGHT - rightScroll;
            if (y >= rightBottom) break;
            Location loc = locations.get(i);
            boolean hovered = mouseX >= rightX && mouseX < rightX + rightW
                    && mouseY >= Math.max(y, rightTop) && mouseY < Math.min(y + ROW_HEIGHT, rightBottom);
            if (hovered) g.fill(rightX, y, rightX + rightW, y + ROW_HEIGHT, COLOR_ROW_HOVER);
            TrackedContainer c = loc.container();
            String line1 = loc.count() + " × in " + c.displayName() + (loc.nested() ? " (inside a box)" : "");
            String line2 = StorageTracker.coords(c.pos()) + " · " + distanceText(c)
                    + (c.ownerName().isEmpty() ? "" : " · tagged by " + c.ownerName());
            g.text(font, font.plainSubstrByWidth(line1, rightW - 6), rightX + 3, y + 2, sameDimension(c) ? COLOR_TEXT : COLOR_DIM);
            g.text(font, font.plainSubstrByWidth(line2, rightW - 6), rightX + 3, y + 12, COLOR_DIM);
        }
        g.disableScissor();
    }

    /** "By container": details and contents of the selected container. Items matching the search are outlined. */
    private void renderContainer(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        TrackedContainer c = selectedContainer;
        if (c == null) return;
        int y0 = top + 42;
        g.text(font, font.plainSubstrByWidth(c.displayName() + " at " + StorageTracker.coords(c.pos()) + " · " + distanceText(c), rightW),
                rightX, y0, COLOR_TEXT);
        String owner = c.ownerName().isEmpty() ? "" : "Tagged by " + c.ownerName() + " · ";
        g.text(font, font.plainSubstrByWidth(owner + c.totalItems() + " items · updated " + age(c.updated()), rightW),
                rightX, y0 + 10, COLOR_DIM);

        List<ItemStackLike> items = contentsOf(c);
        int cols = Math.max(1, rightW / CELL);
        g.fill(rightX - 1, rightTop - 1, rightX + cols * CELL + 1, rightBottom + 1, 0x60000000);
        if (items.isEmpty()) {
            g.text(font, "Empty", rightX + 4, rightTop + 4, COLOR_DIM);
            return;
        }
        int visibleRows = Math.max(0, (rightBottom - rightTop) / CELL);
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = (rightScroll + row) * cols + col;
                if (idx >= items.size()) return;
                ItemStackLike like = items.get(idx);
                boolean nested = idx >= c.items().size();
                ItemStack stack = icon(like.toKey());
                int cx = rightX + col * CELL + 1;
                int cy = rightTop + row * CELL + 1;
                if (nested) g.fill(cx - 1, cy - 1, cx + 17, cy + 17, 0x40A060FF);
                if (!query.isBlank() && matches(like.toKey())) g.fill(cx - 1, cy - 1, cx + 17, cy + 17, 0x6055FF55);
                g.item(stack, cx, cy);
                g.itemDecorations(font, stack, cx, cy, compact(like.count()));
                if (mouseX >= cx - 1 && mouseX < cx + CELL - 1 && mouseY >= cy - 1 && mouseY < cy + CELL - 1) {
                    g.fill(cx, cy, cx + 16, cy + 16, 0x40FFFFFF);
                    g.setTooltipForNextFrame(Component.literal(stack.getHoverName().getString() + " × " + like.count()
                            + (nested ? " (inside a box)" : "")), mouseX, mouseY);
                }
            }
        }
    }

    // ── Input ───────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int x = left + 6;
        if (loaded && mx >= x && mx < x + listW && my >= listTop && my < listBottom) {
            int idx = (int) ((my - listTop + listScroll) / ROW_HEIGHT);
            if (mode == Mode.ITEMS && idx >= 0 && idx < groups.size()) {
                selectedItem = groups.get(idx).key();
                rightScroll = 0;
                return true;
            }
            if (mode == Mode.CONTAINERS && idx >= 0 && idx < containers.size()) {
                selectedContainer = containers.get(idx);
                rightScroll = 0;
                updateButtons();
                return true;
            }
        }
        if (mode == Mode.ITEMS && mx >= rightX && mx < rightX + rightW && my >= rightTop && my < rightBottom) {
            ItemGroup group = selectedGroup();
            int idx = (int) ((my - rightTop + rightScroll) / ROW_HEIGHT);
            if (group != null && idx >= 0 && idx < group.locations().size()) {
                copyCoords(group.locations().get(idx).container());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = left + 6;
        if (mouseX >= x && mouseX < x + listW && mouseY >= listTop && mouseY < listBottom) {
            listScroll = Mth.clamp(listScroll - (int) (scrollY * ROW_HEIGHT), 0, maxListScroll());
            return true;
        }
        if (mouseX >= rightX && mouseX < rightX + rightW && mouseY >= rightTop && mouseY < rightBottom) {
            int step = mode == Mode.ITEMS ? (int) (scrollY * ROW_HEIGHT) : (int) Math.signum(scrollY);
            rightScroll = Mth.clamp(rightScroll - step, 0, maxRightScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    private void copyCoords(TrackedContainer c) {
        String coords = StorageTracker.coords(c.pos());
        minecraft.keyboardHandler.setClipboard(coords);
        status = "Copied " + coords + " (" + c.displayName() + ")";
    }

    private void confirmUntrack(TrackedContainer c) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                status = "";
                ModInfo.getPacketDistributor().sendToServer(
                        new StorageRequestPayload(StorageRequestPayload.Action.UNTRACK, c.dimension(), c.pos()));
            }
            minecraft.setScreen(this);
        }, Component.translatable("endinv.manager.confirm_title"),
                Component.translatable("endinv.storage.untrack.confirm", c.displayName())));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    @Nullable
    private ItemGroup selectedGroup() {
        if (selectedItem == null) return null;
        for (ItemGroup group : groups) {
            if (group.key().equals(selectedItem)) return group;
        }
        return null;
    }

    private static List<ItemStackLike> contentsOf(TrackedContainer c) {
        if (c.nested().isEmpty()) return c.items();
        List<ItemStackLike> all = new ArrayList<>(c.items().size() + c.nested().size());
        all.addAll(c.items());
        all.addAll(c.nested());
        return Collections.unmodifiableList(all);
    }

    private int maxListScroll() {
        int rows = mode == Mode.ITEMS ? groups.size() : containers.size();
        return Math.max(0, rows * ROW_HEIGHT - (listBottom - listTop));
    }

    private int maxRightScroll() {
        if (mode == Mode.ITEMS) {
            ItemGroup group = selectedGroup();
            int rows = group == null ? 0 : group.locations().size();
            return Math.max(0, rows * ROW_HEIGHT - (rightBottom - rightTop));
        }
        if (selectedContainer == null) return 0;
        int cols = Math.max(1, rightW / CELL);
        int rows = (contentsOf(selectedContainer).size() + cols - 1) / cols;
        return Math.max(0, rows - Math.max(1, (rightBottom - rightTop) / CELL));
    }

    private static boolean sameDimension(TrackedContainer c) {
        var level = Minecraft.getInstance().level;
        return level != null && Objects.equals(level.dimension(), c.dimension());
    }

    /** Squared distance from the player, or infinity for another dimension (sorts last). */
    private static double distanceSq(TrackedContainer c) {
        var player = Minecraft.getInstance().player;
        if (player == null || !sameDimension(c)) return Double.POSITIVE_INFINITY;
        return c.pos().getCenter().distanceToSqr(player.position());
    }

    private static String distanceText(TrackedContainer c) {
        if (!sameDimension(c)) return c.dimension().identifier().getPath().replace('_', ' ');
        var player = Minecraft.getInstance().player;
        if (player == null) return "";
        Vec3 delta = c.pos().getCenter().subtract(player.position());
        return Math.round(delta.horizontalDistance()) + " m " + direction(delta)
                + (Math.abs(delta.y) >= 4 ? (delta.y > 0 ? " ↑" : " ↓") + Math.round(Math.abs(delta.y)) : "");
    }

    private static String direction(Vec3 delta) {
        if (delta.horizontalDistanceSqr() < 4) return "";
        // Minecraft: -Z is north, +X is east.
        double angle = Math.toDegrees(Math.atan2(delta.x, -delta.z));
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[Math.floorMod((int) Math.round(angle / 45.0), 8)];
    }

    private static String age(long updated) {
        if (updated <= 0) return "never";
        long seconds = Math.max(0, (System.currentTimeMillis() - updated) / 1000);
        if (seconds < 60) return seconds + "s ago";
        if (seconds < 3600) return seconds / 60 + "m ago";
        if (seconds < 86400) return seconds / 3600 + "h ago";
        return seconds / 86400 + "d ago";
    }

    private static String compact(long n) {
        if (n < 1_000) return Long.toString(n);
        if (n < 1_000_000) return trim(n / 1_000.0) + "k";
        return trim(n / 1_000_000.0) + "M";
    }

    private static String trim(double v) {
        return v >= 100 ? Long.toString((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v).replace(".0", "");
    }
}
