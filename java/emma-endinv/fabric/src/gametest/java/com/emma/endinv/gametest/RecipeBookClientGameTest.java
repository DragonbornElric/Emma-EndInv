package com.emma.endinv.gametest;

import com.emma.endinv.client.gui.EndlessInventoryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Issue #1: in the EndInv crafting station with the recipe book beside the panel, clicking a recipe
 * closed the book, and opening it again moved the panel but didn't draw the book.
 * Drives real mouse input at 1600x900, GUI scale 3 (the book sits beside the panel).
 */
public class RecipeBookClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1600, 900);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(3);
            mc.resizeGui();
        });

        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getServer().runCommand("gamemode survival @a");
            sp.getServer().runCommand("recipe give @a *");
            sp.getServer().runCommand("give @a minecraft:gold_ingot 32");
            ctx.waitTicks(40);

            ctx.getInput().pressKey(GLFW.GLFW_KEY_I);
            ctx.waitForScreen(EndlessInventoryScreen.class);
            ctx.waitTicks(5);
            log(ctx, "opened");

            clickWidget(ctx, "craftingButton");
            ctx.waitTicks(5);
            log(ctx, "crafting station");

            if (!bookVisible(ctx)) {
                clickRecipeBookButton(ctx);
                ctx.waitTicks(5);
            }
            log(ctx, "book opened");
            screenshot(ctx, "endinv-1-book-open");
            check(bookVisible(ctx), "recipe book should be open after clicking its button");
            check(bookDrawnBesidePanel(ctx), "open book should sit left of the panel");

            // Show craftable recipes only, then click the first one.
            int[] filter = ctx.computeOnClient(mc -> {
                AbstractWidget w = (AbstractWidget) get(book((EndlessInventoryScreen) mc.gui.screen()), RecipeBookComponent.class, "filterButton");
                return new int[]{w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
            });
            click(ctx, filter[0], filter[1]);
            ctx.waitTicks(5);
            int[] pos = craftableRecipeCentre(ctx);
            if (pos == null) {
                screenshot(ctx, "endinv-x-no-craftable");
                throw new AssertionError("[EndInvTest] no craftable recipe shown");
            }
            click(ctx, pos[0], pos[1]);
            ctx.waitTicks(10);
            log(ctx, "after recipe click");
            screenshot(ctx, "endinv-2-after-recipe-click");
            check(bookVisible(ctx), "recipe book should stay open after picking a recipe (book beside panel)");

            // Close and reopen with the button.
            clickRecipeBookButton(ctx);
            ctx.waitTicks(5);
            log(ctx, "book closed");
            check(!bookVisible(ctx), "recipe book should close from its button");
            check(ctx.computeOnClient(mc -> leftPos(mc) == (mc.gui.screen().width - 176) / 2), "panel should be centred with the book closed");

            clickRecipeBookButton(ctx);
            ctx.waitTicks(5);
            log(ctx, "book reopened");
            screenshot(ctx, "endinv-3-book-reopened");
            check(bookVisible(ctx), "recipe book should reopen from its button");
            check(bookDrawnBesidePanel(ctx), "reopened book should sit left of the panel");

            // A recipe click must still work after the reopen (the book takes clicks).
            int[] pos2 = craftableRecipeCentre(ctx);
            check(pos2 != null, "reopened book should show a craftable recipe");
            if (pos2 != null) click(ctx, pos2[0], pos2[1]);
            ctx.waitTicks(10);
            log(ctx, "after second recipe click");
            check(bookVisible(ctx), "recipe book should stay open after a second recipe click");

            // Escape closes the whole screen (as in vanilla with the book beside the panel), not just the book.
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(5);
            check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "Escape should close the screen");
            if (!failures.isEmpty()) throw new AssertionError("[EndInvTest] " + failures.size() + " check(s) failed: " + failures);
            System.out.println("[EndInvTest] PASS: recipe book checks");
        }
    }

    // --- helpers ---

    private final List<String> failures = new java.util.ArrayList<>();

    /** Records a failed check and carries on, so one run shows every symptom. */
    private void check(boolean ok, String what) {
        System.out.println("[EndInvTest] " + (ok ? "ok: " : "FAIL: ") + what);
        if (!ok) failures.add(what);
    }

    private static void screenshot(ClientGameTestContext ctx, String name) {
        System.out.println("[EndInvTest] screenshot " + ctx.takeScreenshot(name));
    }

    private static int[] craftableRecipeCentre(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            RecipeButton b = craftableButton(mc);
            return b == null ? null : new int[]{b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2};
        });
    }

    private static void log(ClientGameTestContext ctx, String step) {
        String s = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof EndlessInventoryScreen screen)) return "screen=" + mc.gui.screen();
            RecipeBookComponent<?> book = book(screen);
            return "screen " + screen.width + "x" + screen.height + " leftPos=" + leftPos(mc)
                    + " book=" + book.getClass().getSimpleName() + " visible=" + book.isVisible()
                    + " xOffset=" + get(book, RecipeBookComponent.class, "xOffset");
        });
        System.out.println("[EndInvTest] " + step + ": " + s);
    }

    private static void click(ClientGameTestContext ctx, double guiX, double guiY) {
        int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
        ctx.waitTick();
        ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTick();
    }

    private static void clickWidget(ClientGameTestContext ctx, String field) {
        int[] p = ctx.computeOnClient(mc -> {
            AbstractWidget w = (AbstractWidget) get(mc.gui.screen(), EndlessInventoryScreen.class, field);
            return new int[]{w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
        });
        click(ctx, p[0], p[1]);
    }

    /** Clicks the recipe-book button widget where it actually is on screen. */
    private static void clickRecipeBookButton(ClientGameTestContext ctx) {
        int[] p = ctx.computeOnClient(mc -> {
            for (var child : mc.gui.screen().children()) {
                if (child instanceof ImageButton b
                        && get(b, ImageButton.class, "sprites") == RecipeBookComponent.RECIPE_BUTTON_SPRITES) {
                    return new int[]{b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2};
                }
            }
            throw new IllegalStateException("no recipe book button");
        });
        click(ctx, p[0], p[1]);
    }

    private static boolean bookVisible(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.gui.screen() instanceof EndlessInventoryScreen s && book(s).isVisible());
    }

    /** The book's page buttons are laid out and sit left of the panel, so they are drawn and clickable. */
    private static boolean bookDrawnBesidePanel(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            RecipeButton b = anyVisibleButton(mc);
            return b != null && b.getX() + b.getWidth() <= leftPos(mc);
        });
    }

    private static RecipeBookComponent<?> book(EndlessInventoryScreen screen) {
        return (RecipeBookComponent<?>) get(screen, AbstractRecipeBookScreen.class, "recipeBookComponent");
    }

    private static int leftPos(Minecraft mc) {
        return (int) get(mc.gui.screen(), net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, "leftPos");
    }

    @SuppressWarnings("unchecked")
    private static List<RecipeButton> pageButtons(Minecraft mc) {
        RecipeBookComponent<?> book = book((EndlessInventoryScreen) mc.gui.screen());
        Object page = get(book, RecipeBookComponent.class, "recipeBookPage");
        return (List<RecipeButton>) get(page, page.getClass(), "buttons");
    }

    private static RecipeButton anyVisibleButton(Minecraft mc) {
        for (RecipeButton b : pageButtons(mc)) if (b.visible) return b;
        return null;
    }

    private static RecipeButton craftableButton(Minecraft mc) {
        for (RecipeButton b : pageButtons(mc)) {
            if (b.visible && b.getCollection().hasCraftable()) return b;
        }
        return null;
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
