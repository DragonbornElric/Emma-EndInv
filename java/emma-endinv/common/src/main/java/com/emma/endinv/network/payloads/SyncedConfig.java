package com.emma.endinv.network.payloads;

import com.emma.endinv.ModRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Synced endless inventory config data shared between client preferences and the server.
 * Only minimal flags remain after refactor; layout data lives purely on the client.
 * <p>
 *     Since 1.1.0, attaching filed only presents player's client attaching config.<br>
 *     To check whether player is able to attach
 * </p>
 *
 *
 * @param attaching presents player's client attaching config.
 */
public record SyncedConfig(boolean attaching, boolean autoPicking) implements ModPacketPayload {

    public static final SyncedConfig DEFAULT = new SyncedConfig(true, true);
    public static final Codec<SyncedConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("attaching", true).forGetter(SyncedConfig::attaching),
                    Codec.BOOL.optionalFieldOf("auto_pickup", true).forGetter(SyncedConfig::autoPicking)
            ).apply(instance, SyncedConfig::new)
    );

    public static void encode(SyncedConfig config, FriendlyByteBuf buffer) {
        buffer.writeBoolean(config.attaching);
        buffer.writeBoolean(config.autoPicking);
    }

    public static SyncedConfig decode(FriendlyByteBuf buffer) {
        boolean attaching = buffer.readBoolean();
        boolean autoPicking = buffer.readBoolean();
        return new SyncedConfig(attaching, autoPicking);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        encode(this, buffer);
    }

    @Override
    public String id() {
        return "endinv_settings";
    }

    public void handle(ModPacketContext context) {
        if (context.player() != null) {
            ModRegistries.NbtAttachments.getSyncedConfig().setTo(context.player(), this);
        }
    }

    public boolean checkForAttaching() {
        return attaching;
    }
}
