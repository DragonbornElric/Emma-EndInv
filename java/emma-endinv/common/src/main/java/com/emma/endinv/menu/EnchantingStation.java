package com.emma.endinv.menu;

import com.emma.endinv.EndlessInventory;
import com.emma.endinv.SourceInventory;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The EndInv enchanting station: vanilla's enchanting table ({@code EnchantmentMenu}) with its
 * power taken from the bookshelves put into the station (0..15, saved on the EndInv) instead of
 * bookshelves around a block. Shared by the common menu and the Folia menu.
 *
 * <p>Menu buttons: 0..2 pick an enchantment (as vanilla), {@link #BOOKSHELF_INSERT_BUTTON} puts the
 * bookshelves on the cursor in (up to 15 in all), {@link #BOOKSHELF_TAKE_BUTTON} takes one back out.
 */
public final class EnchantingStation {

    public static final int BOOKSHELF_INSERT_BUTTON = 2000;
    public static final int BOOKSHELF_TAKE_BUTTON = 2001;
    public static final Identifier EMPTY_SLOT_LAPIS_LAZULI = Identifier.withDefaultNamespace("container/slot/lapis_lazuli");

    private final Player player;
    private final SourceInventory source;
    private final RandomSource random = RandomSource.create();
    final SimpleContainer slots = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            recompute();
        }
    };
    private final DataSlot seed = DataSlot.standalone();
    private final DataSlot bookshelves = DataSlot.standalone();
    public final int[] costs = new int[3];
    public final int[] enchantClue = {-1, -1, -1};
    public final int[] levelClue = {-1, -1, -1};

    public EnchantingStation(Player player, SourceInventory source) {
        this.player = player;
        this.source = source;
        this.seed.set(player.getEnchantmentSeed());
        this.bookshelves.set(source instanceof EndlessInventory endInv ? endInv.getBookshelves() : 0);
    }

    /** Item slot and lapis slot, at the vanilla table's columns in the station area. */
    public Slot[] createSlots(int craftY, Station station, java.util.function.Supplier<Station> activeStation) {
        // Station area top is craftY - 5; the texture is drawn from row 10, so its slot frames (row 46) land at +36.
        int y = craftY - 5 + 37;
        Slot item = new Slot(slots, 0, 15, y) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean isActive() { return activeStation.get() == station; }
        };
        Slot lapis = new Slot(slots, 1, 35, y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.LAPIS_LAZULI); }
            @Override public Identifier getNoItemIcon() { return EMPTY_SLOT_LAPIS_LAZULI; }
            @Override public boolean isActive() { return activeStation.get() == station; }
        };
        return new Slot[]{item, lapis};
    }

    public void addDataSlots(Consumer<DataSlot> add) {
        for (int i = 0; i < 3; i++) add.accept(DataSlot.shared(costs, i));
        add.accept(seed);
        for (int i = 0; i < 3; i++) add.accept(DataSlot.shared(enchantClue, i));
        for (int i = 0; i < 3; i++) add.accept(DataSlot.shared(levelClue, i));
        add.accept(bookshelves);
    }

    public int getBookshelves() { return bookshelves.get(); }
    public int getSeed() { return seed.get(); }
    public int getLapisCount() { return slots.getItem(1).getCount(); }
    public ItemStack getItem() { return slots.getItem(0); }
    public static boolean isLapis(ItemStack stack) { return stack.is(Items.LAPIS_LAZULI); }

    /** Server side, every tick: follow the EndInv's bookshelf count (changed here, by the API or another viewer). */
    public void tick() {
        if (!(player instanceof ServerPlayer) || !(source instanceof EndlessInventory endInv)) return;
        if (bookshelves.get() != endInv.getBookshelves()) {
            bookshelves.set(endInv.getBookshelves());
            recompute();
        }
    }

    /** Vanilla's {@code EnchantmentMenu.slotsChanged} with the stored bookshelves as power. Server only. */
    private void recompute() {
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack stack = slots.getItem(0);
        if (stack.isEmpty() || !stack.isEnchantable()) {
            for (int i = 0; i < 3; i++) {
                costs[i] = 0;
                enchantClue[i] = -1;
                levelClue[i] = -1;
            }
            return;
        }
        ServerLevel level = sp.level();
        IdMap<Holder<Enchantment>> holders = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
        int power = bookshelves.get();
        random.setSeed(seed.get());
        for (int i = 0; i < 3; i++) {
            costs[i] = EnchantmentHelper.getEnchantmentCost(random, i, power, stack);
            enchantClue[i] = -1;
            levelClue[i] = -1;
            if (costs[i] < i + 1) costs[i] = 0;
        }
        for (int i = 0; i < 3; i++) {
            if (costs[i] > 0) {
                List<EnchantmentInstance> list = enchantmentList(level.registryAccess(), stack, i, costs[i]);
                if (!list.isEmpty()) {
                    EnchantmentInstance ench = list.get(random.nextInt(list.size()));
                    enchantClue[i] = holders.getId(ench.enchantment());
                    levelClue[i] = ench.level();
                }
            }
        }
    }

    /** Menu button click. @return true when handled (the menu then syncs) */
    public boolean clickButton(AbstractContainerMenu menu, Player player, int id, boolean active) {
        if (id == BOOKSHELF_INSERT_BUTTON || id == BOOKSHELF_TAKE_BUTTON) {
            if (!(source instanceof EndlessInventory endInv)) return false;
            ItemStack carried = menu.getCarried();
            int have = endInv.getBookshelves();
            if (id == BOOKSHELF_INSERT_BUTTON) {
                if (!carried.is(Items.BOOKSHELF) || have >= EndlessInventory.MAX_BOOKSHELVES) return false;
                int add = Math.min(carried.getCount(), EndlessInventory.MAX_BOOKSHELVES - have);
                if (!player.hasInfiniteMaterials()) {
                    carried.shrink(add);
                    menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                }
                endInv.setBookshelves(have + add);
            } else {
                if (have <= 0) return false;
                if (carried.isEmpty()) {
                    menu.setCarried(new ItemStack(Items.BOOKSHELF));
                } else if (carried.is(Items.BOOKSHELF) && carried.getCount() < carried.getMaxStackSize()) {
                    carried.grow(1);
                    menu.setCarried(carried);
                } else {
                    return false;
                }
                endInv.setBookshelves(have - 1);
            }
            tick();
            return true;
        }
        if (!active || id < 0 || id >= 3) return false;
        return enchant(player, id);
    }

    /** Vanilla's {@code EnchantmentMenu.clickMenuButton}. */
    private boolean enchant(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer sp)) return false;
        ItemStack itemStack = slots.getItem(0);
        ItemStack currency = slots.getItem(1);
        int enchantmentCost = buttonId + 1;
        if ((currency.isEmpty() || currency.getCount() < enchantmentCost) && !player.hasInfiniteMaterials()) return false;
        if (costs[buttonId] <= 0 || itemStack.isEmpty()
                || (player.experienceLevel < enchantmentCost || player.experienceLevel < costs[buttonId]) && !player.hasInfiniteMaterials()) {
            return false;
        }
        ServerLevel level = sp.level();
        List<EnchantmentInstance> newEnchantment = enchantmentList(level.registryAccess(), itemStack, buttonId, costs[buttonId]);
        if (newEnchantment.isEmpty()) return true;
        player.onEnchantmentPerformed(itemStack, enchantmentCost);
        ItemStack enchanted = itemStack;
        if (itemStack.is(Items.BOOK)) {
            enchanted = itemStack.transmuteCopy(Items.ENCHANTED_BOOK);
            slots.setItem(0, enchanted);
        }
        for (EnchantmentInstance e : newEnchantment) enchanted.enchant(e.enchantment(), e.level());
        currency.consume(enchantmentCost, player);
        if (currency.isEmpty()) slots.setItem(1, ItemStack.EMPTY);
        player.awardStat(Stats.ENCHANT_ITEM);
        CriteriaTriggers.ENCHANTED_ITEM.trigger(sp, enchanted, enchantmentCost);
        seed.set(player.getEnchantmentSeed());
        slots.setChanged();
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        return true;
    }

    private List<EnchantmentInstance> enchantmentList(RegistryAccess access, ItemStack stack, int slot, int cost) {
        random.setSeed(seed.get() + slot);
        Optional<HolderSet.Named<Enchantment>> tag = access.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
        if (tag.isEmpty()) return List.of();
        List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(random, stack, cost, tag.get().stream());
        if (stack.is(Items.BOOK) && list.size() > 1) list.remove(random.nextInt(list.size()));
        return list;
    }

    /** Station closed or menu removed: item and lapis go back to the player. Server only. */
    public void returnToPlayer() {
        if (player.level().isClientSide()) return;
        for (int i = 0; i < slots.getContainerSize(); i++) {
            ItemStack s = slots.removeItemNoUpdate(i);
            if (!s.isEmpty()) player.getInventory().placeItemBackInInventory(s);
        }
        slots.setChanged();
    }

    /**
     * Server side, for the API and bots: put up to {@code count} bookshelves from the player's
     * inventory, then EndInv, into the enchanting station (15 at most in all; nothing is taken in
     * creative). @return how many were added
     */
    public static int addBookshelvesFromStorage(Player player, @org.jetbrains.annotations.Nullable SourceInventory source, int count) {
        if (!(source instanceof EndlessInventory endInv) || count <= 0 || !StationUnlocks.enabled()) return 0;
        int want = Math.min(count, EndlessInventory.MAX_BOOKSHELVES - endInv.getBookshelves());
        int added = 0;
        if (player.hasInfiniteMaterials()) {
            added = Math.max(0, want);
        } else {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize() && added < want; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.is(Items.BOOKSHELF)) {
                    int take = Math.min(stack.getCount(), want - added);
                    stack.shrink(take);
                    added += take;
                }
            }
            if (added > 0) inv.setChanged();
            if (added < want) {
                var key = com.emma.endinv.util.ItemKey.asKey(new ItemStack(Items.BOOKSHELF));
                if (endInv.getItemMap().containsKey(key)) added += endInv.takeItem(key, want - added).getCount();
            }
        }
        if (added > 0) endInv.setBookshelves(endInv.getBookshelves() + added);
        return added;
    }
}
