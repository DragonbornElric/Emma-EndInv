package com.emma.endinv.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 *
 * @param item
 * @param tag the complete 1.20.1 item NBT identity, or {@code null}.
 */
public record ItemKey(Item item, @Nullable CompoundTag tag) {

    public static final ItemKey EMPTY = new ItemKey(Items.AIR, null);

    public static final Codec<ItemKey> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ItemKey::item),
                    CompoundTag.CODEC.optionalFieldOf("tag").forGetter(key -> Optional.ofNullable(key.tag()))
            ).apply(instance, (item, tag) -> new ItemKey(item, tag.orElse(null)))
    );

    public ItemKey {
        if (tag != null) {
            tag = tag.copy();
        }
    }

    public static void encode(FriendlyByteBuf buffer, ItemKey key) {
        buffer.writeItem(key.toStack(1));
    }

    public static ItemKey decode(FriendlyByteBuf buffer) {
        return asKey(buffer.readItem());
    }

    public boolean isEmpty() {
        return Objects.equals(item, Items.AIR);
    }

    public ItemStack toStack(int count){
        ItemStack stack = new ItemStack(item, count);
        stack.setTag(tag == null ? null : tag.copy());
        return stack;
    }

    public static ItemKey asKey(ItemStack stack){
        return new ItemKey(stack.getItem(), stack.getTag());
    }
}
