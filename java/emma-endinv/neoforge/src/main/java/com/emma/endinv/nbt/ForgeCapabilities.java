package com.emma.endinv.nbt;

import com.emma.endinv.AbstractModInitializer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ForgeCapabilities {

    public static final Capability<IEndInvUuid> END_INV_UUID =
            CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<ISyncedConfig> END_INV_CONFIG =
            CapabilityManager.get(new CapabilityToken<>() {});

    private ForgeCapabilities() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ForgeCapabilities::registerTypes);
        MinecraftForge.EVENT_BUS.addGenericListener(
                Entity.class, ForgeCapabilities::attachToEntity);
    }

    private static void registerTypes(RegisterCapabilitiesEvent event) {
        event.register(IEndInvUuid.class);
        event.register(ISyncedConfig.class);
    }

    private static void attachToEntity(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) return;

        NbtCapabilityProvider<EndInvUuidCapability> uuidProvider =
                new NbtCapabilityProvider<>(
                        new EndInvUuidCapability(),
                        castCapability(END_INV_UUID)
                );
        event.addCapability(
                AbstractModInitializer.withModLocation("uuid"), uuidProvider);
        event.addListener(uuidProvider::invalidate);

        NbtCapabilityProvider<SyncedConfigCapability> configProvider =
                new NbtCapabilityProvider<>(
                        new SyncedConfigCapability(),
                        castCapability(END_INV_CONFIG)
                );
        event.addCapability(
                AbstractModInitializer.withModLocation("synced_config"), configProvider);
        event.addListener(configProvider::invalidate);
    }

    @SuppressWarnings("unchecked")
    private static <T, R extends T> Capability<R> castCapability(Capability<T> capability) {
        return (Capability<R>) capability;
    }
}
