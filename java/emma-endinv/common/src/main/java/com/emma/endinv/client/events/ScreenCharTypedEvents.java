package com.emma.endinv.client.events;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;

import java.util.concurrent.CopyOnWriteArrayList;

public final class ScreenCharTypedEvents {

    private static final CopyOnWriteArrayList<BeforeCharTyped> LISTENERS = new CopyOnWriteArrayList<>();

    public static void register(BeforeCharTyped listener) {
        LISTENERS.add(listener);
    }

    public static boolean dispatch(GuiEventListener listener, CharacterEvent event) {
        for (BeforeCharTyped l : LISTENERS) {
            if (l.beforeCharTyped(listener, event)) return true;
        }
        return false;
    }

    @FunctionalInterface
    public interface BeforeCharTyped {
        boolean beforeCharTyped(GuiEventListener guiEventListener, CharacterEvent event);
    }

    private ScreenCharTypedEvents() {
    }
}
