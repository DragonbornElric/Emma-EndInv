package com.emma.endinv.menu.page;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static net.minecraft.tags.ItemTags.*;

// Server-only PageType: strips PageConstructor / buildPage() which reference client GUI classes.
// API-compatible with the common version for all server-side usages
// (registerName, itemClassify, icon, equals, toString, createClassifiedPage/createServerSafe).
public class PageType {

    public static final String DEFAULT_KEY = "all_items";

    public static final List<TagKey<Item>> WEAPON_TAGS = new ArrayList<>();
    public static final List<TagKey<Item>> TOOL_TAGS = new ArrayList<>();
    public static final List<TagKey<Item>> EQUIPPABLE_TAGS = new ArrayList<>();

    @Nullable public final Predicate<ItemStack> itemClassify;
    @Nullable public ResourceLocation icon;
    public final String registerName;

    public PageType(String registerName, @Nullable Predicate<ItemStack> itemClassify, @Nullable ResourceLocation icon) {
        this.registerName = registerName;
        this.itemClassify = itemClassify;
        this.icon = icon;
    }

    public static final PageType ALL_ITEMS = createPage(DEFAULT_KEY, null, "chest");
    public static final PageType BLOCK_ITEMS = createPage("block_items", s -> s.getItem() instanceof BlockItem, "stone");
    public static final PageType WEAPONS = createPage("weapons", PageType::isWeapon, "iron_sword");
    public static final PageType TOOLS = createPage("tools", PageType::isTool, "iron_pickaxe");
    public static final PageType EQUIPMENTS = createPage("equipments", PageType::isDefenceEquipment,
            new ResourceLocation("minecraft", "iron_chestplate"));
    public static final PageType CONSUMABLE = createPage("consumable", PageType::isFoodOrPotion, "bread");
    public static final PageType ENCHANTED_BOOKS = createPage("enchanted_books",
            s -> s.getItem() == Items.ENCHANTED_BOOK, new ResourceLocation("minecraft", "enchanted_book"));
    public static final PageType BOOKMARK = createPage(
            "bookmark", null, new ResourceLocation("minecraft", "book"));

    public static PageType createPage(String name, @Nullable Predicate<ItemStack> classify, String icon) {
        return new PageType(name, classify, new ResourceLocation("minecraft", icon));
    }

    public static PageType createPage(
            String name, @Nullable Predicate<ItemStack> classify, ResourceLocation icon) {
        return new PageType(name, classify, icon);
    }

    /** Compatibility shim — common code calls createClassifiedPage on client; server uses createPage. */
    public static PageType createClassifiedPage(String name, @Nullable Predicate<ItemStack> classify, String icon) {
        return createPage(name, classify, icon);
    }

    /** Compatibility shim — common code calls createServerSafe on client; server drops the factory. */
    public static PageType createServerSafe(String name, @Nullable Predicate<ItemStack> classify,
                                            @Nullable ResourceLocation icon, Object clientCtorIgnored) {
        return new PageType(name, classify, icon);
    }

    @Override
    public String toString() { return registerName; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PageType p
                && Objects.equals(p.itemClassify, itemClassify)
                && Objects.equals(p.registerName, registerName);
    }

    private static boolean isWeapon(ItemStack s) {
        Item item = s.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                || item instanceof ProjectileWeaponItem
                || WEAPON_TAGS.stream().anyMatch(s::is);
    }

    private static boolean isTool(ItemStack s) {
        Item item = s.getItem();
        return item instanceof AxeItem || item instanceof ShearsItem || item instanceof ShovelItem
                || item instanceof FlintAndSteelItem || item instanceof FishingRodItem
                || TOOL_TAGS.stream().anyMatch(s::is);
    }

    private static boolean isDefenceEquipment(ItemStack s) {
        Item item = s.getItem();
        return item instanceof ArmorItem || item instanceof ShieldItem || item instanceof ElytraItem
                || EQUIPPABLE_TAGS.stream().anyMatch(s::is);
    }

    private static boolean isFoodOrPotion(ItemStack s) {
        return s.getItem() instanceof PotionItem || s.isEdible();
    }

    static {
        WEAPON_TAGS.add(SWORDS);
        WEAPON_TAGS.add(AXES);
        TOOL_TAGS.add(AXES);
        TOOL_TAGS.add(PICKAXES);
        TOOL_TAGS.add(HOES);
        TOOL_TAGS.add(SHOVELS);
    }
}
