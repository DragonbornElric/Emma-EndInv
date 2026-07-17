package com.emma.endinv.folia.debug;

import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.options.ServerConfigs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.stream.Collectors;

public class CraftingDebugListener implements Listener {

    private static boolean enabled() { return ServerConfigs.ENABLE_CRAFT_DEBUG_LOG.get(); }

    private final Plugin plugin;

    public CraftingDebugListener(Plugin plugin) {
        this.plugin = plugin;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static boolean isCraftingView(InventoryView view) {
        InventoryType t = view.getType();
        return t == InventoryType.CRAFTING || t == InventoryType.WORKBENCH;
    }

    private static String fmt(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return "AIR";
        return item.getType().name() + "x" + item.getAmount();
    }

    private static String fmtMatrix(org.bukkit.inventory.ItemStack[] matrix) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < matrix.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("s").append(i).append("=").append(fmt(matrix[i]));
        }
        return sb.append("]").toString();
    }

    // NMS ItemStack (from EndInv)
    private static String fmtNms(ItemStack s) {
        if (s.isEmpty()) return "AIR";
        String key = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        return key + "x" + s.getCount();
    }

    private void log(String msg) {
        plugin.getLogger().info("[CRAFT-DBG] " + msg);
    }

    // ── events ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        if (!enabled()) return;
        InventoryView view = event.getView();
        if (!isCraftingView(view)) return;
        String holder = event.getInventory().getHolder() != null
                ? event.getInventory().getHolder().getClass().getSimpleName() : "null";
        log("OPEN player=" + event.getPlayer().getName()
                + " type=" + view.getType() + " holder=" + holder);
        if (event.getPlayer() instanceof org.bukkit.entity.Player p) {
            ServerPlayer nms = ((CraftPlayer) p).getHandle();
            log("OPEN menu=" + nms.containerMenu.getClass().getSimpleName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!enabled()) return;
        InventoryView view = event.getView();
        if (!isCraftingView(view)) return;
        log("CLOSE player=" + event.getPlayer().getName() + " type=" + view.getType());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!enabled()) return;
        CraftingInventory inv = event.getInventory();
        String playerName = event.getView().getPlayer().getName();
        String recipeKey = event.getRecipe() instanceof org.bukkit.Keyed k
                ? k.getKey().toString() : "none";
        log("PREPARE player=" + playerName
                + " recipe=" + recipeKey
                + " repair=" + event.isRepair()
                + " matrix=" + fmtMatrix(inv.getMatrix())
                + " result=" + fmt(inv.getResult()));
        // EndInv snapshot
        if (event.getView().getPlayer() instanceof org.bukkit.entity.Player p) {
            ServerPlayer nms = ((CraftPlayer) p).getHandle();
            ServerLevelEndInv.getEndInvForPlayer(nms).ifPresent(endInv -> {
                List<ItemStack> items = endInv.getItemsAsList();
                String snap = items.isEmpty() ? "empty"
                        : items.stream().map(CraftingDebugListener::fmtNms)
                               .collect(Collectors.joining(", "));
                log("PREPARE endInv=[" + snap + "]");
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (!enabled()) return;
        InventoryView view = event.getView();
        if (!isCraftingView(view)) return;
        log("CLICK player=" + event.getWhoClicked().getName()
                + " action=" + event.getAction()
                + " click=" + event.getClick()
                + " slot=" + event.getSlotType() + "/" + event.getRawSlot()
                + " cur=" + fmt(event.getCurrentItem())
                + " cursor=" + fmt(event.getCursor()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraft(CraftItemEvent event) {
        if (!enabled()) return;
        String recipeKey = event.getRecipe() instanceof org.bukkit.Keyed k
                ? k.getKey().toString() : "unknown";
        log("CRAFT player=" + event.getWhoClicked().getName()
                + " recipe=" + recipeKey
                + " result=" + fmt(event.getCurrentItem())
                + " click=" + event.getClick());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (!enabled()) return;
        InventoryView view = event.getView();
        if (!isCraftingView(view)) return;
        log("DRAG player=" + event.getWhoClicked().getName()
                + " slots=" + event.getRawSlots()
                + " type=" + event.getType());
    }
}
