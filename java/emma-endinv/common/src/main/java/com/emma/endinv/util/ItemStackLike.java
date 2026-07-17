package com.emma.endinv.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record ItemStackLike(Item item, int count, CompoundTag tag) {

    public static final Codec<ItemStackLike> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ItemStackLike::item),
                            Codec.INT.fieldOf("count").forGetter(ItemStackLike::count),
                            CompoundTag.CODEC.optionalFieldOf("tag")
                                    .forGetter(like -> Optional.ofNullable(like.tag()))
                    )
                    .apply(instance, (item, count, tag) -> new ItemStackLike(item, count, tag.orElse(null)))
    );

    public ItemStackLike {
        if (tag != null) {
            tag = tag.copy();
        }
    }

    public static void encode(FriendlyByteBuf buffer, ItemStackLike value) {
        // ItemStack's vanilla packet codec drops the item identity when count is
        // zero. Starred entries intentionally use zero to represent an item that
        // is not currently stored, so encode the three fields independently.
        buffer.writeId(BuiltInRegistries.ITEM, value.item());
        buffer.writeVarInt(value.count());
        buffer.writeNbt(value.tag());
    }

    public static ItemStackLike decode(FriendlyByteBuf buffer) {
        return new ItemStackLike(
                buffer.readById(BuiltInRegistries.ITEM),
                buffer.readVarInt(),
                buffer.readNbt()
        );
    }

    public static ItemStackLike asKey(ItemKey stack) {
        return new ItemStackLike(stack.item(), 0, stack.tag());
    }

    public static ItemStackLike asKey(ItemKey stack, int count) {
        return new ItemStackLike(stack.item(), count, stack.tag());
    }

    public static ItemStackLike asKey(ItemStack stack) {
        return new ItemStackLike(stack.getItem(), stack.getCount(), stack.getTag());
    }

    public ItemKey toKey() {
        return new ItemKey(item, tag);
    }

    public ItemStack toStack() {
        ItemStack stack = new ItemStack(item, count);
        stack.setTag(tag == null ? null : tag.copy());
        return stack;
    }
}
