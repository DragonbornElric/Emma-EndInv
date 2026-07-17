package com.emma.endinv.network.payloads;

import com.emma.endinv.AbstractModInitializer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;

// Server-only override: strips getClientPageMeta() of its ScreenFramework dependency
// so this interface can compile against the Folia dev bundle (server-only classpath).
public interface ModPacketPayload extends CustomPacketPayload {

    String id();

    default Type<? extends CustomPacketPayload> type() {
        return new Type<>(AbstractModInitializer.withModLocation(id()));
    }

    void handle(ModPacketContext context);

    @SuppressWarnings("unchecked")
    static <T> Optional<T> getClientPageMeta() {
        return Optional.empty();
    }
}
