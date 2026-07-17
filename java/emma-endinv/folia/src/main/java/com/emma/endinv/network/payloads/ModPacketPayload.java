package com.emma.endinv.network.payloads;

import com.emma.endinv.AbstractModInitializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

// Server-only override: strips getClientPageMeta() of its ScreenFramework dependency
// so this interface can compile against the Folia dev bundle (server-only classpath).
public interface ModPacketPayload {

    String id();

    default ResourceLocation payloadId() {
        return AbstractModInitializer.withModLocation(id());
    }

    void write(FriendlyByteBuf buffer);

    void handle(ModPacketContext context);

    @SuppressWarnings("unchecked")
    static <T> Optional<T> getClientPageMeta() {
        return Optional.empty();
    }
}
