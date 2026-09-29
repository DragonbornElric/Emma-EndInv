package com.emma.endinv.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * The Storage Tag: a vanilla paper stack marked with custom data, so it needs no item registration and works
 * unchanged on Folia (where plugins cannot add items) and for vanilla clients. Right-clicking a container with it
 * tracks that container in the {@link StorageIndex}; the tag itself is never consumed or stored in the container.
 *
 * <p>Keep {@link #create} in sync with {@code data/emma_endinv/recipe/storage_tag.json} so crafted and
 * command-given tags stack together.
 */
public final class StorageTag {

    public static final String MARKER = "endinv_storage_tag";
    // The name/lore keys keep the pre-1.4 "endless_inventory" namespace: they are saved on every tag
    // item, and new tags must stay identical to existing ones to stack with them.
    private static final CompoundTag MARKER_TAG = markerTag();

    private StorageTag() {}

    public static ItemStack create(int count) {
        ItemStack stack = new ItemStack(Items.PAPER, count);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(markerTag()));
        stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("item.endless_inventory.storage_tag", "Storage Tag"));
        stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("name_tag"));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        Style gray = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatableWithFallback("item.endless_inventory.storage_tag.lore1",
                        "Right-click a chest to track its contents").withStyle(gray),
                Component.translatableWithFallback("item.endless_inventory.storage_tag.lore2",
                        "Sneak + right-click to stop tracking").withStyle(gray))));
        return stack;
    }

    public static boolean isTag(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.matchedBy(MARKER_TAG);
    }

    private static CompoundTag markerTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        return tag;
    }
}
