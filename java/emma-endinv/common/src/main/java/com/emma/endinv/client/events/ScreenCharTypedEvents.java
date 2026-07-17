package com.emma.endinv.client.events;

import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.concurrent.CopyOnWriteArrayList;

public final class ScreenCharTypedEvents {

    private static final CopyOnWriteArrayList<BeforeCharTyped> LISTENERS = new CopyOnWriteArrayList<>();

    public static void register(BeforeCharTyped listener) {
        LISTENERS.add(listener);
    }

    public static boolean dispatch(GuiEventListener listener, char codePoint, int modifiers) {
        for (BeforeCharTyped l : LISTENERS) {
            if (l.beforeCharTyped(listener, codePoint, modifiers)) return true;
        }
        return false;
    }

    @FunctionalInterface
    public interface BeforeCharTyped {
        boolean beforeCharTyped(GuiEventListener guiEventListener, char codePoint, int modifiers);
    }

    private ScreenCharTypedEvents() {
    }
}
