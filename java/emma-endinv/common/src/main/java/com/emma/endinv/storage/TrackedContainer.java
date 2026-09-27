package com.emma.endinv.storage;

import com.emma.endinv.util.ItemStackLike;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One tracked storage block and the last snapshot of its contents.
 *
 * @param pos       canonical position (for a double chest, the lower of the two halves)
 * @param partner   the other half of a double chest, so tracking survives either half being broken
 * @param blockName the container's custom name if it has one, otherwise the block's name
 * @param label     name given with a renamed Storage Tag; empty when unset
 * @param items     aggregated contents, largest first (shulker-box contents stripped from the box itself)
 * @param nested    aggregated contents of shulker boxes (and other item containers) stored inside
 * @param updated   epoch millis of the last change seen
 */
public record TrackedContainer(ResourceKey<Level> dimension, BlockPos pos, Optional<BlockPos> partner,
                               String blockName, String label, UUID owner, String ownerName, long updated,
                               List<ItemStackLike> items, List<ItemStackLike> nested) {

    public static final Codec<TrackedContainer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(TrackedContainer::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(TrackedContainer::pos),
            BlockPos.CODEC.optionalFieldOf("partner").forGetter(TrackedContainer::partner),
            Codec.STRING.optionalFieldOf("block_name", "").forGetter(TrackedContainer::blockName),
            Codec.STRING.optionalFieldOf("label", "").forGetter(TrackedContainer::label),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(TrackedContainer::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(TrackedContainer::ownerName),
            Codec.LONG.optionalFieldOf("updated", 0L).forGetter(TrackedContainer::updated),
            ItemStackLike.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(TrackedContainer::items),
            ItemStackLike.CODEC.listOf().optionalFieldOf("nested", List.of()).forGetter(TrackedContainer::nested)
    ).apply(i, TrackedContainer::new));

    public StorageIndex.Key key() {
        return new StorageIndex.Key(dimension, pos.asLong());
    }

    /** What to call this container in lists: the tag label, else the container's own name. */
    public String displayName() {
        return label.isEmpty() ? blockName : label;
    }

    public long totalItems() {
        long total = 0;
        for (ItemStackLike like : items) total += like.count();
        return total;
    }

    public TrackedContainer withContents(Optional<BlockPos> partner, String blockName, List<ItemStackLike> items,
                                         List<ItemStackLike> nested, long updated) {
        return new TrackedContainer(dimension, pos, partner, blockName, label, owner, ownerName, updated, items, nested);
    }

    public TrackedContainer withPos(BlockPos pos, Optional<BlockPos> partner) {
        return new TrackedContainer(dimension, pos, partner, blockName, label, owner, ownerName, updated, items, nested);
    }

    public TrackedContainer withLabel(String label) {
        return new TrackedContainer(dimension, pos, partner, blockName, label, owner, ownerName, updated, items, nested);
    }

    // ── Network (the client screen and the EmmaEndInvApi read this form) ────

    public static void encode(RegistryFriendlyByteBuf buf, TrackedContainer c) {
        buf.writeResourceKey(c.dimension);
        buf.writeBlockPos(c.pos);
        buf.writeOptional(c.partner, (b, p) -> b.writeBlockPos(p));
        buf.writeUtf(c.blockName);
        buf.writeUtf(c.label);
        UUIDUtil.STREAM_CODEC.encode(buf, c.owner);
        buf.writeUtf(c.ownerName);
        buf.writeLong(c.updated);
        buf.writeCollection(c.items, (b, like) -> ItemStackLike.STREAM_CODEC.encode((RegistryFriendlyByteBuf) b, like));
        buf.writeCollection(c.nested, (b, like) -> ItemStackLike.STREAM_CODEC.encode((RegistryFriendlyByteBuf) b, like));
    }

    public static TrackedContainer decode(RegistryFriendlyByteBuf buf) {
        return new TrackedContainer(
                buf.readResourceKey(Registries.DIMENSION),
                buf.readBlockPos(),
                buf.readOptional(b -> b.readBlockPos()),
                buf.readUtf(),
                buf.readUtf(),
                UUIDUtil.STREAM_CODEC.decode(buf),
                buf.readUtf(),
                buf.readLong(),
                buf.readList(b -> ItemStackLike.STREAM_CODEC.decode((RegistryFriendlyByteBuf) b)),
                buf.readList(b -> ItemStackLike.STREAM_CODEC.decode((RegistryFriendlyByteBuf) b)));
    }
}
