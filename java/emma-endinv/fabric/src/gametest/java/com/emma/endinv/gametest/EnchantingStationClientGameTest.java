package com.emma.endinv.gametest;

import com.emma.endinv.api.EmmaEndInvServerApi;
import com.emma.endinv.client.gui.EndlessInventoryScreen;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.menu.Station;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Enchanting station: bookshelves go in from the cursor (15 at most) and set the power, an item
 * enchants with lapis and levels, and DropStationsOnDeath drops the table and shelves on death.
 */
public class EnchantingStationClientGameTest implements FabricClientGameTest {

    private static final int ENCHANT_ITEM_SLOT = 69;
    private static final int ENCHANT_LAPIS_SLOT = 70;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1600, 900);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(3);
            mc.resizeGui();
        });

        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getServer().runCommand("gamemode survival @a");
            sp.getServer().runCommand("endinv config freeStations true");
            sp.getServer().runCommand("endinv config dropStationsOnDeath false");
            sp.getServer().runCommand("give @a minecraft:diamond_sword 1");
            sp.getServer().runCommand("give @a minecraft:lapis_lazuli 10");
            sp.getServer().runCommand("give @a minecraft:bookshelf 20");
            sp.getServer().runCommand("xp add @a 40 levels");
            ctx.waitTicks(40);

            openEndInv(ctx);
            clickWidget(ctx, "enchantingButton");
            ctx.waitTicks(5);
            check(active(ctx) == Station.ENCHANTING, "enchanting station opens");

            // Sword and lapis in with no bookshelves: weak enchantments only.
            clickSlotHolding(ctx, Items.DIAMOND_SWORD);
            clickSlot(ctx, ENCHANT_ITEM_SLOT);
            clickSlotHolding(ctx, Items.LAPIS_LAZULI);
            clickSlot(ctx, ENCHANT_LAPIS_SLOT);
            ctx.waitTicks(5);
            int weak = ctx.computeOnClient(mc -> menu(mc).getEnchanting().costs[2]);
            check(weak > 0 && weak <= 8, "no bookshelves: top cost 1..8, got " + weak);
            screenshot(ctx, "enchanting-1-no-shelves");

            // 20 bookshelves on the cursor: 15 go in, 5 stay.
            clickSlotHolding(ctx, Items.BOOKSHELF);
            clickBookshelfBox(ctx, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(5);
            check(shelves(sp) == 15, "15 bookshelves in the station");
            check(carriedCount(ctx, Items.BOOKSHELF) == 5, "5 bookshelves left on the cursor");
            int strong = ctx.computeOnClient(mc -> menu(mc).getEnchanting().costs[2]);
            check(strong == 30, "15 bookshelves: top cost 30, got " + strong);

            // Right-click takes one back out; left-click puts it back.
            clickBookshelfBox(ctx, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            ctx.waitTicks(5);
            check(shelves(sp) == 14 && carriedCount(ctx, Items.BOOKSHELF) == 6, "right-click takes one bookshelf out");
            clickBookshelfBox(ctx, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(5);
            check(shelves(sp) == 15 && carriedCount(ctx, Items.BOOKSHELF) == 5, "left-click puts it back");
            clickSlotHolding(ctx, Items.AIR);  // drop the cursor stack into an empty slot
            ctx.waitTicks(3);
            hoverOption(ctx, 2);
            screenshot(ctx, "enchanting-2-fifteen-shelves");

            // Enchant with the third option: costs 3 lapis and 3 levels.
            int levelsBefore = sp.getServer().computeOnServer(s -> player(s).experienceLevel);
            clickOption(ctx, 2);
            ctx.waitTicks(10);
            boolean enchanted = ctx.computeOnClient(mc -> menu(mc).slots.get(ENCHANT_ITEM_SLOT).getItem().isEnchanted());
            check(enchanted, "sword enchanted");
            check(ctx.computeOnClient(mc -> menu(mc).slots.get(ENCHANT_LAPIS_SLOT).getItem().getCount()) == 7, "3 lapis used");
            check(sp.getServer().computeOnServer(s -> player(s).experienceLevel) == levelsBefore - 3, "3 levels used");
            screenshot(ctx, "enchanting-3-enchanted");

            // Closing the station gives the sword and lapis back.
            clickWidget(ctx, "enchantingButton");
            ctx.waitTicks(5);
            check(sp.getServer().computeOnServer(s -> player(s).getInventory().countItem(Items.LAPIS_LAZULI)) == 7, "lapis back in the inventory on close");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(5);

            // Default: kept on death. Empty the inventory so only station drops are counted.
            sp.getServer().runCommand("clear @a");
            sp.getServer().runCommand("endinv config freeStations false");
            sp.getServer().runCommand("give @a minecraft:enchanting_table 1");
            ctx.waitTicks(5);
            check(sp.getServer().computeOnServer(s -> EmmaEndInvServerApi.unlockStation(player(s), Items.ENCHANTING_TABLE)), "enchanting table unlocked");
            die(ctx, sp);
            check(shelves(sp) == 15, "DropStationsOnDeath off: bookshelves kept on death");
            check(sp.getServer().computeOnServer(s -> EmmaEndInvServerApi.isStationUnlocked(player(s), Items.ENCHANTING_TABLE)), "DropStationsOnDeath off: table kept");

            // Flag on: the table and all 15 shelves drop where she died.
            sp.getServer().runCommand("kill @e[type=item]");
            sp.getServer().runCommand("clear @a");
            sp.getServer().runCommand("endinv config dropStationsOnDeath true");
            kill(ctx, sp);
            // Counted before respawning: back at spawn she picks them up again (auto-pickup into EndInv).
            int droppedShelves = droppedCount(sp, Items.BOOKSHELF);
            int droppedTables = droppedCount(sp, Items.ENCHANTING_TABLE);
            respawn(ctx);
            check(shelves(sp) == 0, "DropStationsOnDeath on: bookshelves gone from the station");
            check(!sp.getServer().computeOnServer(s -> EmmaEndInvServerApi.isStationUnlocked(player(s), Items.ENCHANTING_TABLE)), "table locked again");
            check(droppedShelves == 15, "15 bookshelves dropped, got " + droppedShelves);
            check(droppedTables == 1, "enchanting table dropped, got " + droppedTables);

            sp.getServer().runCommand("endinv config dropStationsOnDeath false");
            sp.getServer().runCommand("endinv config freeStations true");
            if (!failures.isEmpty()) throw new AssertionError("[EndInvTest] " + failures.size() + " check(s) failed: " + failures);
            System.out.println("[EndInvTest] PASS: enchanting station checks");
        }
    }

    private void check(boolean ok, String what) {
        System.out.println("[EndInvTest] " + (ok ? "ok: " : "FAIL: ") + what);
        if (!ok) failures.add(what);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static int shelves(TestSingleplayerContext sp) {
        return sp.getServer().computeOnServer(s -> EmmaEndInvServerApi.bookshelves(player(s)));
    }

    private static int droppedCount(TestSingleplayerContext sp, Item item) {
        return sp.getServer().computeOnServer(s -> {
            ServerPlayer p = player(s);
            int n = 0;
            for (ItemEntity e : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(64))) {
                if (e.getItem().is(item)) n += e.getItem().getCount();
            }
            return n;
        });
    }

    private static void die(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        kill(ctx, sp);
        respawn(ctx);
    }

    private static void kill(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runOnServer(s -> s.getPlayerList().getPlayers().get(0).kill(s.getPlayerList().getPlayers().get(0).level()));
        ctx.waitTicks(20);
    }

    private static void respawn(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.player.respawn());
        ctx.waitTicks(20);
        // Clear the death screen if it is still up.
        ctx.runOnClient(mc -> { if (mc.gui.screen() != null) mc.gui.setScreen(null); });
        ctx.waitTicks(5);
    }

    private static void openEndInv(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(GLFW.GLFW_KEY_I);
        ctx.waitForScreen(EndlessInventoryScreen.class);
        ctx.waitTicks(5);
    }

    private static EndlessInventoryMenu menu(Minecraft mc) {
        return ((EndlessInventoryScreen) mc.gui.screen()).getMenu();
    }

    private static Station active(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> menu(mc).getActiveStation());
    }

    private static int carriedCount(ClientGameTestContext ctx, Item item) {
        return ctx.computeOnClient(mc -> menu(mc).getCarried().is(item) ? menu(mc).getCarried().getCount() : 0);
    }

    private static void screenshot(ClientGameTestContext ctx, String name) {
        System.out.println("[EndInvTest] screenshot " + ctx.takeScreenshot(name));
    }

    private static int[] origin(Minecraft mc) {
        return new int[]{(int) get(mc.gui.screen(), AbstractContainerScreen.class, "leftPos"),
                (int) get(mc.gui.screen(), AbstractContainerScreen.class, "topPos")};
    }

    /** Top of the station area. */
    private static int enchantTop(Minecraft mc) {
        return origin(mc)[1] + 18 * menu(mc).getVisibleRows() + 18;
    }

    private static void clickSlot(ClientGameTestContext ctx, int index) {
        int[] p = ctx.computeOnClient(mc -> {
            int[] o = origin(mc);
            Slot slot = menu(mc).slots.get(index);
            return new int[]{o[0] + slot.x + 8, o[1] + slot.y + 8};
        });
        click(ctx, p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
    }

    /** Clicks the first player-inventory slot holding {@code item} (AIR: the first empty one). */
    private static void clickSlotHolding(ClientGameTestContext ctx, Item item) {
        int index = ctx.computeOnClient(mc -> {
            var slots = menu(mc).slots;
            for (int i = 33; i < 69; i++) {
                if (item == Items.AIR ? slots.get(i).getItem().isEmpty() : slots.get(i).getItem().is(item)) return i;
            }
            throw new IllegalStateException("no slot holding " + item);
        });
        clickSlot(ctx, index);
    }

    private static void clickBookshelfBox(ClientGameTestContext ctx, int button) {
        int[] p = ctx.computeOnClient(mc -> new int[]{origin(mc)[0] + 10 + 9, enchantTop(mc) + 2 + 9});
        click(ctx, p[0], p[1], button);
    }

    private static int[] optionCentre(ClientGameTestContext ctx, int i) {
        return ctx.computeOnClient(mc -> new int[]{origin(mc)[0] + 60 + 54, enchantTop(mc) + 2 + 17 * i + 8});
    }

    private static void hoverOption(ClientGameTestContext ctx, int i) {
        int[] p = optionCentre(ctx, i);
        int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(p[0] * scale, p[1] * scale);
        ctx.waitTicks(3);
    }

    private static void clickOption(ClientGameTestContext ctx, int i) {
        int[] p = optionCentre(ctx, i);
        click(ctx, p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void clickWidget(ClientGameTestContext ctx, String field) {
        int[] p = ctx.computeOnClient(mc -> {
            AbstractWidget w = (AbstractWidget) get(mc.gui.screen(), EndlessInventoryScreen.class, field);
            return new int[]{w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
        });
        click(ctx, p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void click(ClientGameTestContext ctx, double guiX, double guiY, int button) {
        int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
        ctx.waitTick();
        ctx.getInput().pressMouse(button);
        ctx.waitTick();
    }

    private static Object get(Object target, Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
