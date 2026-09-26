package com.emma.endinv.client.gui;

import com.emma.endinv.ModInfo;
import com.emma.endinv.manage.EndInvManager.Action;
import com.emma.endinv.manage.EndInvSummary;
import com.emma.endinv.network.payloads.toClient.EndInvDetailPayload;
import com.emma.endinv.network.payloads.toClient.EndInvListPayload;
import com.emma.endinv.network.payloads.toServer.ManageEndInvPayload;
import com.emma.endinv.util.Accessibility;
import com.emma.endinv.util.ItemStackLike;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lists the Endless Inventories this player can see (all of them for admins) and lets them
 * select one to use, change sharing, and — for admins — take, move, clear or delete contents.
 * All decisions are made server-side by {@code EndInvManager}; this screen only sends requests
 * and renders the replies ({@link EndInvListPayload}, {@link EndInvDetailPayload}).
 */
public class EndInvManagerScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int CELL = 18;
    private static final int GAP = 4;

    private static final int COLOR_PANEL = 0xC0101010;
    private static final int COLOR_ROW_SELECTED = 0x80406080;
    private static final int COLOR_ROW_HOVER = 0x40FFFFFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFFA0A0A0;
    private static final int COLOR_CURRENT = 0xFF55FF55;
    private static final int COLOR_WARN = 0xFFFFAA55;

    @Nullable private static EndInvListPayload lastList;
    @Nullable private static EndInvDetailPayload lastDetail;

    private final Screen parent;
    @Nullable private UUID selectedId;
    @Nullable private UUID moveTargetId;
    private int listScroll;
    private int gridScroll;
    private String nameInput = "";

    private int left, top, panelW, panelH, listW, rightX, rightW;
    private int listTop, listBottom, gridTop, gridBottom;
    @Nullable private EditBox nameBox;

    public EndInvManagerScreen(Screen parent) {
        super(Component.translatable("title.endinv.manager"));
        this.parent = parent;
        lastList = null;
        lastDetail = null;
        send(ManageEndInvPayload.of(Action.LIST));
    }

    // ── Packet callbacks (client thread) ────────────────────────────────────

    public static void onList(EndInvListPayload payload) {
        lastList = payload;
        if (Minecraft.getInstance().screen instanceof EndInvManagerScreen screen) {
            screen.listUpdated();
        }
    }

    public static void onDetail(EndInvDetailPayload payload) {
        lastDetail = payload;
        if (Minecraft.getInstance().screen instanceof EndInvManagerScreen screen
                && Objects.equals(screen.selectedId, payload.inventoryId())) {
            screen.gridScroll = Mth.clamp(screen.gridScroll, 0, screen.maxGridScroll());
        }
    }

    private void listUpdated() {
        List<EndInvSummary> entries = entries();
        if (selectedId == null || find(selectedId) == null) {
            EndInvSummary initial = entries.stream().filter(EndInvSummary::current).findFirst()
                    .orElse(entries.isEmpty() ? null : entries.getFirst());
            select(initial);
        }
        if (moveTargetId != null && (find(moveTargetId) == null || moveTargetId.equals(selectedId))) {
            moveTargetId = null;
        }
        rebuildWidgets();
    }

    // ── Layout & widgets ────────────────────────────────────────────────────

    @Override
    protected void init() {
        panelW = Math.min(width - 16, 470);
        panelH = Math.min(height - 16, 280);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        listW = Math.min(160, panelW * 2 / 5);
        rightX = left + listW + 12;
        rightW = panelW - listW - 18;
        listTop = top + 20;
        listBottom = top + panelH - 28;

        EndInvSummary sel = selected();
        boolean admin = lastList != null && lastList.admin();
        int y = top + 20 + 44;

        if (sel != null) {
            // Row 1: use + access
            Button use = Button.builder(Component.translatable("endinv.manager.use"), b -> useSelected())
                    .bounds(rightX, y, 70, 20).build();
            use.active = sel.canSelect() && !sel.current();
            use.setTooltip(Tooltip.create(Component.translatable("endinv.manager.use.tip")));
            addRenderableWidget(use);
            int accessW = (rightW - 74) / 3;
            int ax = rightX + 74;
            for (Accessibility access : Accessibility.values()) {
                Button b = Button.builder(Component.literal(capitalize(access.name())),
                                btn -> send(new ManageEndInvPayload(Action.SET_ACCESS, sel.id(), ModInfo.DEFAULT_UUID, access.name())))
                        .bounds(ax, y, accessW - 2, 20).build();
                b.active = sel.canShare() && sel.access() != access;
                b.setTooltip(Tooltip.create(Component.literal(access.description())));
                addRenderableWidget(b);
                ax += accessW;
            }
            y += 22;

            // Row 2: share with a player (whitelist)
            nameBox = new EditBox(font, rightX, y, rightW - 112, 20, Component.translatable("endinv.manager.player"));
            nameBox.setMaxLength(36);
            nameBox.setHint(Component.translatable("endinv.manager.player"));
            nameBox.setValue(nameInput);
            nameBox.setResponder(value -> nameInput = value);
            nameBox.active = sel.canShare();
            addRenderableWidget(nameBox);
            Button share = Button.builder(Component.translatable("endinv.manager.share"),
                            b -> sendNamed(Action.WHITELIST_ADD, sel))
                    .bounds(rightX + rightW - 108, y, 52, 20).build();
            share.active = sel.canShare();
            share.setTooltip(Tooltip.create(Component.translatable("endinv.manager.share.tip")));
            addRenderableWidget(share);
            Button unshare = Button.builder(Component.translatable("endinv.manager.unshare"),
                            b -> sendNamed(Action.WHITELIST_REMOVE, sel))
                    .bounds(rightX + rightW - 54, y, 54, 20).build();
            unshare.active = sel.canShare();
            addRenderableWidget(unshare);
            y += 22;

            if (admin) {
                // Row 3: take / move
                int third = (rightW - 4) / 3;
                Button take = Button.builder(Component.translatable("endinv.manager.take_all"),
                                b -> confirm(Component.translatable("endinv.manager.take_all.confirm", label(sel)),
                                        ManageEndInvPayload.of(Action.TAKE_ALL, sel.id())))
                        .bounds(rightX, y, third, 20).build();
                take.active = !sel.current() && sel.itemTypes() > 0;
                take.setTooltip(Tooltip.create(Component.translatable("endinv.manager.take_all.tip")));
                addRenderableWidget(take);

                EndInvSummary target = moveTargetId == null ? null : find(moveTargetId);
                Button pickTarget = Button.builder(Component.literal(target == null ? "To: …" : "To: " + label(target)),
                                b -> cycleMoveTarget())
                        .bounds(rightX + third + 2, y, third, 20).build();
                pickTarget.active = entries().size() > 1;
                pickTarget.setTooltip(Tooltip.create(Component.translatable("endinv.manager.move_target.tip")));
                addRenderableWidget(pickTarget);

                Button move = Button.builder(Component.translatable("endinv.manager.move_all"),
                                b -> {
                                    EndInvSummary t = moveTargetId == null ? null : find(moveTargetId);
                                    if (t == null) return;
                                    confirm(Component.translatable("endinv.manager.move_all.confirm", label(sel), label(t)),
                                            new ManageEndInvPayload(Action.MOVE_ALL, sel.id(), t.id(), ""));
                                })
                        .bounds(rightX + 2 * third + 4, y, rightW - 2 * third - 4, 20).build();
                move.active = target != null && sel.itemTypes() > 0;
                addRenderableWidget(move);
                y += 22;

                // Row 4: clear / delete
                int half = (rightW - 2) / 2;
                Button clear = Button.builder(Component.translatable("endinv.manager.clear"),
                                b -> confirm(Component.translatable("endinv.manager.clear.confirm", label(sel)),
                                        ManageEndInvPayload.of(Action.CLEAR, sel.id())))
                        .bounds(rightX, y, half, 20).build();
                clear.active = sel.itemTypes() > 0;
                addRenderableWidget(clear);
                Button delete = Button.builder(Component.translatable("endinv.manager.delete"),
                                b -> confirm(Component.translatable("endinv.manager.delete.confirm", label(sel)),
                                        ManageEndInvPayload.of(Action.DELETE, sel.id())))
                        .bounds(rightX + half + 2, y, rightW - half - 2, 20).build();
                addRenderableWidget(delete);
                y += 22;
            }
        }

        gridTop = y + 14;
        gridBottom = top + panelH - 40;

        addRenderableWidget(Button.builder(Component.translatable("endinv.manager.refresh"), b -> {
            send(ManageEndInvPayload.of(Action.LIST));
            if (selectedId != null) send(ManageEndInvPayload.of(Action.INSPECT, selectedId));
        }).bounds(left + panelW - 164, top + panelH - 24, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelW - 82, top + panelH - 24, 76, 20).build());
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(left, top, left + panelW, top + panelH, COLOR_PANEL);
        Component heading = lastList != null && lastList.admin()
                ? title.copy().append(Component.translatable("endinv.manager.admin_suffix"))
                : title;
        g.centeredText(font, heading, left + panelW / 2, top + 6, COLOR_TEXT);

        renderList(g, mouseX, mouseY);
        renderDetails(g);
        renderGrid(g, mouseX, mouseY);

        if (lastList != null && !lastList.message().isEmpty()) {
            g.textWithWordWrap(font, Component.literal(lastList.message()), left + 6, top + panelH - 36, panelW - 176, COLOR_WARN);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = left + 6;
        g.fill(x - 1, listTop - 1, x + listW + 1, listBottom + 1, 0x60000000);
        if (lastList == null) {
            g.text(font, Component.translatable("endinv.manager.loading"), x + 4, listTop + 4, COLOR_DIM);
            return;
        }
        List<EndInvSummary> entries = entries();
        if (entries.isEmpty()) {
            g.text(font, Component.translatable("endinv.manager.none"), x + 4, listTop + 4, COLOR_DIM);
            return;
        }
        g.enableScissor(x, listTop, x + listW, listBottom);
        int y = listTop - listScroll;
        for (EndInvSummary entry : entries) {
            if (y + ROW_HEIGHT > listTop && y < listBottom) {
                boolean hovered = mouseX >= x && mouseX < x + listW && mouseY >= y && mouseY < y + ROW_HEIGHT
                        && mouseY >= listTop && mouseY < listBottom;
                if (entry.id().equals(selectedId)) g.fill(x, y, x + listW, y + ROW_HEIGHT, COLOR_ROW_SELECTED);
                else if (hovered) g.fill(x, y, x + listW, y + ROW_HEIGHT, COLOR_ROW_HOVER);
                String line1 = (entry.current() ? "▶ " : "") + label(entry) + " · " + capitalize(entry.access().name());
                String line2 = entry.itemTypes() + " types · " + compact(entry.totalItems()) + " items";
                g.text(font, font.plainSubstrByWidth(line1, listW - 6), x + 3, y + 2, entry.current() ? COLOR_CURRENT : COLOR_TEXT);
                g.text(font, font.plainSubstrByWidth(line2, listW - 6), x + 3, y + 12, COLOR_DIM);
            }
            y += ROW_HEIGHT;
        }
        g.disableScissor();
    }

    private void renderDetails(GuiGraphicsExtractor g) {
        EndInvSummary sel = selected();
        if (sel == null) return;
        int y = top + 20;
        String owner = sel.owner().isEmpty() ? "(none)" : sel.owner();
        g.text(font, font.plainSubstrByWidth("#" + sel.index() + "  Owner: " + owner
                + (sel.current() ? "  (you are using this)" : ""), rightW), rightX, y, sel.current() ? COLOR_CURRENT : COLOR_TEXT);
        g.text(font, font.plainSubstrByWidth("Access: " + capitalize(sel.access().name()) + " — " + sel.access().description(), rightW),
                rightX, y + 10, COLOR_DIM);
        String shared = sel.whitelist().isEmpty() ? "nobody" : String.join(", ", sel.whitelist());
        if (sel.access() != Accessibility.RESTRICTED && !sel.whitelist().isEmpty()) shared += " (only used when Restricted)";
        g.text(font, font.plainSubstrByWidth("Shared with: " + shared, rightW), rightX, y + 20, COLOR_DIM);
        String users = sel.users().isEmpty() ? "nobody" : String.join(", ", sel.users());
        g.text(font, font.plainSubstrByWidth("Used by: " + users, rightW), rightX, y + 30, COLOR_DIM);
    }

    private void renderGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        EndInvSummary sel = selected();
        if (sel == null) return;
        EndInvDetailPayload detail = detailFor(sel);
        int cols = Math.max(1, rightW / CELL);
        String header = detail == null ? "Loading contents…"
                : "Contents (" + detail.totalTypes() + " types"
                + (detail.items().size() < detail.totalTypes() ? ", largest " + detail.items().size() + " shown" : "") + ")";
        g.text(font, font.plainSubstrByWidth(header, rightW), rightX, gridTop - 11, COLOR_DIM);
        g.fill(rightX - 1, gridTop - 1, rightX + cols * CELL + 1, gridBottom + 1, 0x60000000);
        if (detail == null || gridBottom - gridTop < CELL) return;

        List<ItemStackLike> items = detail.items();
        int firstRow = gridScroll;
        int visibleRows = (gridBottom - gridTop) / CELL;
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = (firstRow + row) * cols + col;
                if (idx >= items.size()) break;
                ItemStackLike like = items.get(idx);
                ItemStack stack = like.toKey().toStack(1);
                int cx = rightX + col * CELL + 1;
                int cy = gridTop + row * CELL + 1;
                g.item(stack, cx, cy);
                g.itemDecorations(font, stack, cx, cy, compact(like.count()));
                if (mouseX >= cx - 1 && mouseX < cx + CELL - 1 && mouseY >= cy - 1 && mouseY < cy + CELL - 1) {
                    g.fill(cx, cy, cx + 16, cy + 16, 0x40FFFFFF);
                    g.setTooltipForNextFrame(Component.literal(stack.getHoverName().getString() + " × " + like.count()), mouseX, mouseY);
                }
            }
        }
        if (items.isEmpty()) {
            g.text(font, Component.translatable("endinv.manager.empty"), rightX + 4, gridTop + 4, COLOR_DIM);
        }
    }

    // ── Input ───────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int x = left + 6;
        if (mx >= x && mx < x + listW && my >= listTop && my < listBottom && lastList != null) {
            int idx = (int) ((my - listTop + listScroll) / ROW_HEIGHT);
            List<EndInvSummary> entries = entries();
            if (idx >= 0 && idx < entries.size()) {
                select(entries.get(idx));
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = left + 6;
        if (mouseX >= x && mouseX < x + listW && mouseY >= listTop && mouseY < listBottom) {
            int max = Math.max(0, entries().size() * ROW_HEIGHT - (listBottom - listTop));
            listScroll = Mth.clamp(listScroll - (int) (scrollY * ROW_HEIGHT), 0, max);
            return true;
        }
        if (mouseX >= rightX && mouseX < rightX + rightW && mouseY >= gridTop && mouseY < gridBottom) {
            gridScroll = Mth.clamp(gridScroll - (int) Math.signum(scrollY), 0, maxGridScroll());
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

    private void select(@Nullable EndInvSummary entry) {
        selectedId = entry == null ? null : entry.id();
        gridScroll = 0;
        if (selectedId != null && (lastDetail == null || !selectedId.equals(lastDetail.inventoryId()))) {
            send(ManageEndInvPayload.of(Action.INSPECT, selectedId));
        }
    }

    private void useSelected() {
        EndInvSummary sel = selected();
        if (sel == null) return;
        send(ManageEndInvPayload.of(Action.SELECT, sel.id()));
        // The server rebinds pages and closes an open EndInv menu; close our screens too so the
        // next open starts from the newly selected inventory instead of a stale attached view.
        var player = minecraft.player;
        if (player != null && player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        } else {
            minecraft.setScreen(null);
        }
    }

    private void sendNamed(Action action, EndInvSummary sel) {
        String name = nameInput.trim();
        if (name.isEmpty()) return;
        send(new ManageEndInvPayload(action, sel.id(), ModInfo.DEFAULT_UUID, name));
    }

    private void cycleMoveTarget() {
        List<EndInvSummary> others = entries().stream().filter(e -> !e.id().equals(selectedId)).toList();
        if (others.isEmpty()) {
            moveTargetId = null;
        } else {
            int current = -1;
            for (int i = 0; i < others.size(); i++) {
                if (others.get(i).id().equals(moveTargetId)) current = i;
            }
            moveTargetId = others.get((current + 1) % others.size()).id();
        }
        rebuildWidgets();
    }

    private void confirm(Component question, ManageEndInvPayload request) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) send(request);
            minecraft.setScreen(this);
        }, Component.translatable("endinv.manager.confirm_title"), question));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void send(ManageEndInvPayload payload) {
        ModInfo.getPacketDistributor().sendToServer(payload);
    }

    private static List<EndInvSummary> entries() {
        return lastList == null ? List.of() : lastList.entries();
    }

    @Nullable
    private static EndInvSummary find(UUID id) {
        for (EndInvSummary entry : entries()) {
            if (entry.id().equals(id)) return entry;
        }
        return null;
    }

    @Nullable
    private EndInvSummary selected() {
        return selectedId == null ? null : find(selectedId);
    }

    @Nullable
    private static EndInvDetailPayload detailFor(EndInvSummary entry) {
        return lastDetail != null && lastDetail.inventoryId().equals(entry.id()) ? lastDetail : null;
    }

    private int maxGridScroll() {
        EndInvSummary sel = selected();
        EndInvDetailPayload detail = sel == null ? null : detailFor(sel);
        if (detail == null) return 0;
        int cols = Math.max(1, rightW / CELL);
        int rows = (detail.items().size() + cols - 1) / cols;
        int visible = Math.max(1, (gridBottom - gridTop) / CELL);
        return Math.max(0, rows - visible);
    }

    private static String label(EndInvSummary entry) {
        return "#" + entry.index() + " " + (entry.owner().isEmpty() ? "Shared" : entry.owner());
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase();
    }

    private static String compact(long n) {
        if (n < 1_000) return Long.toString(n);
        if (n < 1_000_000) return trim(n / 1_000.0) + "k";
        if (n < 1_000_000_000) return trim(n / 1_000_000.0) + "M";
        return trim(n / 1_000_000_000.0) + "G";
    }

    private static String trim(double v) {
        return v >= 100 ? Long.toString((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v).replace(".0", "");
    }
}
