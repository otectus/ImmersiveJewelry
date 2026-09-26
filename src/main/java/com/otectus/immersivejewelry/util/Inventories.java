package com.otectus.immersivejewelry.util;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Counting and taking ingredients from a player's main inventory. Creative players pay nothing. */
public final class Inventories {

    public static int count(Player player, Ingredient ingredient) {
        if (player.getAbilities().instabuild) return Integer.MAX_VALUE;
        if (isEmpty(ingredient)) return 0;
        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ingredient.test(s)) total += s.getCount();
        }
        return total;
    }

    public static boolean has(Player player, @Nullable Ingredient ingredient, int count) {
        return ingredient == null || count <= 0 || count(player, ingredient) >= count;
    }

    /** Takes {@code count} matching items, or nothing (returns null) if there are not enough. */
    @Nullable
    public static List<ItemStack> take(Player player, @Nullable Ingredient ingredient, int count) {
        List<ItemStack> out = new ArrayList<>();
        if (ingredient == null || count <= 0 || player.getAbilities().instabuild) return out;
        if (count(player, ingredient) < count) return null;
        Inventory inv = player.getInventory();
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !ingredient.test(s)) continue;
            int n = Math.min(left, s.getCount());
            out.add(s.split(n));
            left -= n;
        }
        inv.setChanged();
        return out;
    }

    public static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, stack);
    }

    /** A representative stack of an ingredient for display. */
    public static ItemStack display(@Nullable Ingredient ingredient, int count) {
        List<ItemStack> items = items(ingredient);
        return items.isEmpty() ? ItemStack.EMPTY : items.get(0).copyWithCount(Math.max(1, count));
    }

    /**
     * The items an ingredient accepts. For a tag with no items Forge stands in a named Barrier, and
     * {@link Ingredient#isEmpty} is false for every tag ingredient; here the placeholder is dropped, so an ingredient
     * over an empty tag (a modded metal's nuggets, a gem mod that is not installed) has no items.
     */
    public static List<ItemStack> items(@Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return List.of();
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : ingredient.getItems()) {
            if (!s.isEmpty() && !(s.is(Items.BARRIER) && s.hasCustomHoverName())) out.add(s);
        }
        return out;
    }

    public static boolean isEmpty(@Nullable Ingredient ingredient) {
        return items(ingredient).isEmpty();
    }

    private Inventories() {}
}
