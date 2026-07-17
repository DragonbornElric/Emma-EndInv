package com.emma.endinv.network.payloads;

import com.emma.endinv.AbstractModInitializer;
import com.emma.endinv.client.gui.ScreenFramework;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public interface ModPacketPayload {

    String id();

    default ResourceLocation payloadId(){
        return AbstractModInitializer.withModLocation(id());
    }

    void write(FriendlyByteBuf buffer);

    void handle(ModPacketContext context);

    static Optional<com.emma.endinv.client.gui.page.manager.PageManager> getClientPageMeta(){
        return Optional.ofNullable(ScreenFramework.getInstance());
    }
}
