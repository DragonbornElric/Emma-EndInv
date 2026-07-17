package com.emma.endinv.event;

import net.minecraft.server.level.ServerPlayer;

public final class BlockBreakRedirect {

    private static final ThreadLocal<ServerPlayer> CURRENT_BREAKER = new ThreadLocal<>();

    private BlockBreakRedirect() {}

    public static ServerPlayer currentBreaker() {
        return CURRENT_BREAKER.get();
    }

    public static void pushBreaker(ServerPlayer sp) {
        CURRENT_BREAKER.set(sp);
    }

    public static void clearBreaker() {
        CURRENT_BREAKER.remove();
    }
}
