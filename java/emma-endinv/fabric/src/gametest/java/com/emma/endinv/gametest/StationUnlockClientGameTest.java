package com.emma.endinv.gametest;

import com.emma.endinv.client.gui.EndlessInventoryScreen;
import com.emma.endinv.menu.EndlessInventoryMenu;
import com.emma.endinv.menu.Station;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * FreeCraftingStations = false: station buttons are locked until the player clicks one holding
 * that station's block, which is used up. Drives real mouse input on the EndInv screen.
 */
public class StationUnlockClientGameTest implements FabricClientGameTest {

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
            sp.getServer().runCommand("give @a minecraft:furnace 2");
            ctx.waitTicks(40);

            ctx.getInput().pressKey(GLFW.GLFW_KEY_I);
            ctx.waitForScreen(EndlessInventoryScreen.class);
            ctx.waitTicks(5);
            check(unlocked(ctx, Station.CRAFTING) && unlocked(ctx, Station.FURNACE), "free stations: all unlocked by default");

            // Open the crafting station, then lock stations: the open station closes.
            clickWidget(ctx, "craftingButton");
            ctx.waitTicks(5);
            check(active(ctx) == Station.CRAFTING, "free crafting station opens");
            sp.getServer().runCommand("endinv config freeStations false");
            ctx.waitTicks(10);
            check(!unlocked(ctx, Station.CRAFTING) && !unlocked(ctx, Station.FURNACE), "locked after freeStations false");
            check(active(ctx) == Station.NONE, "open station closes when it becomes locked");
            screenshot(ctx, "stations-1-locked");

            // Empty cursor: a locked button does nothing.
            clickWidget(ctx, "furnaceButton");
            ctx.waitTicks(5);
            check(active(ctx) == Station.NONE, "locked furnace doesn't open with an empty cursor");
            check(!unlocked(ctx, Station.FURNACE), "locked furnace stays locked with an empty cursor");

            // Pick up the 2 furnaces and put one in the furnace button.
            clickSlotHolding(ctx, Items.FURNACE);
            ctx.waitTicks(3);
            check(carriedCount(ctx, Items.FURNACE) == 2, "picked up 2 furnaces");
            hover(ctx, "furnaceButton");
            screenshot(ctx, "stations-2-holding-furnace");
            clickWidget(ctx, "furnaceButton");
            ctx.waitTicks(5);
            check(unlocked(ctx, Station.FURNACE), "furnace unlocked by clicking it holding a furnace");
            check(carriedCount(ctx, Items.FURNACE) == 1, "one furnace used up");
            check(!unlocked(ctx, Station.CRAFTING), "crafting table still locked");
            check(active(ctx) == Station.NONE, "unlocking doesn't open the station");

            // Wrong block: the furnace can't unlock the crafting table.
            clickWidget(ctx, "craftingButton");
            ctx.waitTicks(5);
            check(!unlocked(ctx, Station.CRAFTING), "a furnace doesn't unlock the crafting table");
            check(carriedCount(ctx, Items.FURNACE) == 1, "wrong block isn't used up");

            clickWidget(ctx, "furnaceButton");
            ctx.waitTicks(5);
            check(active(ctx) == Station.FURNACE, "unlocked furnace opens");
            screenshot(ctx, "stations-3-furnace-open");
            clickWidget(ctx, "furnaceButton");
            ctx.waitTicks(5);

            // The unlock is saved on the EndInv: still there after reopening the screen.
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(5);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_I);
            ctx.waitForScreen(EndlessInventoryScreen.class);
            ctx.waitTicks(5);
            check(unlocked(ctx, Station.FURNACE), "furnace stays unlocked after reopening");
            check(!unlocked(ctx, Station.SMOKER), "smoker still locked after reopening");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(5);

            // A running furnace shows a flame and a progress bar on its button.
            sp.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().get(0);
                com.emma.endinv.ServerLevelEndInv.getEndInvForPlayer(player).orElseThrow().setCookingState(Station.FURNACE,
                        new com.emma.endinv.menu.FurnaceState(new net.minecraft.world.item.ItemStack(Items.RAW_IRON, 8),
                                new net.minecraft.world.item.ItemStack(Items.COAL, 4), net.minecraft.world.item.ItemStack.EMPTY,
                                1200, 1600, 100, 200));
            });
            ctx.waitTicks(2);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_I);
            ctx.waitForScreen(EndlessInventoryScreen.class);
            ctx.waitTicks(10);
            check(ctx.computeOnClient(mc -> menu(mc).isCookingLit(Station.FURNACE)), "furnace lit while closed");
            check(ctx.computeOnClient(mc -> menu(mc).getCookProgress(Station.FURNACE) > 0f), "furnace cook progress synced");
            screenshot(ctx, "stations-4-furnace-running");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(5);

            // API, no screen open: smoker from the inventory, blast furnace from EndInv.
            sp.getServer().runCommand("give @a minecraft:smoker 1");
            sp.getServer().runOnServer(server -> com.emma.endinv.api.EmmaEndInvServerApi.insert(
                    server.getPlayerList().getPlayers().get(0), new net.minecraft.world.item.ItemStack(Items.BLAST_FURNACE, 3)));
            ctx.waitTicks(10);
            check(ctx.computeOnClient(mc -> com.emma.endinv.api.EmmaEndInvApi.unlockStation(Items.SMOKER)), "API sends smoker unlock");
            check(ctx.computeOnClient(mc -> com.emma.endinv.api.EmmaEndInvApi.unlockStation(Items.BLAST_FURNACE)), "API sends blast furnace unlock");
            check(!ctx.computeOnClient(mc -> com.emma.endinv.api.EmmaEndInvApi.unlockStation(Items.DIRT)), "API refuses a non-station block");
            ctx.waitTicks(10);
            check(sp.getServer().computeOnServer(server -> com.emma.endinv.api.EmmaEndInvServerApi.isStationUnlocked(
                    server.getPlayerList().getPlayers().get(0), Items.SMOKER)), "API unlocked the smoker");
            check(sp.getServer().computeOnServer(server -> com.emma.endinv.api.EmmaEndInvServerApi.isStationUnlocked(
                    server.getPlayerList().getPlayers().get(0), Items.BLAST_FURNACE)), "API unlocked the blast furnace from EndInv");
            check(sp.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getInventory().countItem(Items.SMOKER)) == 0,
                    "smoker taken from the inventory");
            check(sp.getServer().computeOnServer(server -> com.emma.endinv.api.EmmaEndInvServerApi.count(
                    server.getPlayerList().getPlayers().get(0), new net.minecraft.world.item.ItemStack(Items.BLAST_FURNACE))) == 2,
                    "one blast furnace taken from EndInv");
            check(!sp.getServer().computeOnServer(server -> com.emma.endinv.api.EmmaEndInvServerApi.unlockStation(
                    server.getPlayerList().getPlayers().get(0), Items.GRINDSTONE)), "server API: no grindstone, no unlock");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_I);
            ctx.waitForScreen(EndlessInventoryScreen.class);
            ctx.waitTicks(5);
            check(Boolean.TRUE.equals(ctx.computeOnClient(mc -> com.emma.endinv.api.EmmaEndInvApi.isStationUnlocked(Items.SMOKER))), "client API sees the smoker unlocked");
            check(Boolean.FALSE.equals(ctx.computeOnClient(mc -> com.emma.endinv.api.EmmaEndInvApi.isStationUnlocked(Items.GRINDSTONE))), "client API sees the grindstone locked");

            sp.getServer().runCommand("endinv config freeStations true");
            ctx.waitTicks(10);
            check(unlocked(ctx, Station.GRINDSTONE), "freeStations true unlocks every station again");

            // The open menu writes its cooking state back on close, so set the new state with it closed.
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(5);
            // Every running station gives off its block's particles: smoker, blast furnace and furnace lit, brewing stand smoking.
            sp.getServer().runOnServer(server -> {
                var endInv = com.emma.endinv.ServerLevelEndInv.getEndInvForPlayer(server.getPlayerList().getPlayers().get(0)).orElseThrow();
                for (Station st : new Station[] {Station.SMOKER, Station.BLAST_FURNACE}) {
                    endInv.setCookingState(st, new com.emma.endinv.menu.FurnaceState(
                            new net.minecraft.world.item.ItemStack(st == Station.SMOKER ? Items.BEEF : Items.RAW_IRON, 8),
                            new net.minecraft.world.item.ItemStack(Items.COAL, 4), net.minecraft.world.item.ItemStack.EMPTY,
                            1200, 1600, 40, 100));
                }
            });
            ctx.waitTicks(2);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_I);
            ctx.waitForScreen(EndlessInventoryScreen.class);
            ctx.waitTicks(40);
            check(ctx.computeOnClient(mc -> menu(mc).isCookingLit(Station.SMOKER) && menu(mc).isCookingLit(Station.BLAST_FURNACE)),
                    "smoker and blast furnace lit");
            screenshot(ctx, "stations-5-all-running");

            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(5);
            if (!failures.isEmpty()) throw new AssertionError("[EndInvTest] " + failures.size() + " check(s) failed: " + failures);
            System.out.println("[EndInvTest] PASS: station unlock checks");
        }
    }

    private void check(boolean ok, String what) {
        System.out.println("[EndInvTest] " + (ok ? "ok: " : "FAIL: ") + what);
        if (!ok) failures.add(what);
    }

    private static EndlessInventoryMenu menu(Minecraft mc) {
        return ((EndlessInventoryScreen) mc.gui.screen()).getMenu();
    }

    private static boolean unlocked(ClientGameTestContext ctx, Station st) {
        return ctx.computeOnClient(mc -> menu(mc).isStationUnlocked(st));
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

    private static void clickSlotHolding(ClientGameTestContext ctx, Item item) {
        int[] p = ctx.computeOnClient(mc -> {
            int left = (int) get(mc.gui.screen(), AbstractContainerScreen.class, "leftPos");
            int top = (int) get(mc.gui.screen(), AbstractContainerScreen.class, "topPos");
            for (Slot slot : menu(mc).slots) {
                if (slot.isActive() && slot.getItem().is(item)) return new int[]{left + slot.x + 8, top + slot.y + 8};
            }
            throw new IllegalStateException("no slot holding " + item);
        });
        click(ctx, p[0], p[1]);
    }

    private static int[] widgetCentre(ClientGameTestContext ctx, String field) {
        return ctx.computeOnClient(mc -> {
            AbstractWidget w = (AbstractWidget) get(mc.gui.screen(), EndlessInventoryScreen.class, field);
            return new int[]{w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
        });
    }

    private static void hover(ClientGameTestContext ctx, String field) {
        int[] p = widgetCentre(ctx, field);
        int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(p[0] * scale, p[1] * scale);
        ctx.waitTicks(3);
    }

    private static void clickWidget(ClientGameTestContext ctx, String field) {
        int[] p = widgetCentre(ctx, field);
        click(ctx, p[0], p[1]);
    }

    private static void click(ClientGameTestContext ctx, double guiX, double guiY) {
        int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
        ctx.waitTick();
        ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
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
