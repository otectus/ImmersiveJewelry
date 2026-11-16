package com.otectus.immersivejewelry.workstation;

import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Item data that belongs to others: names, enchantments, other mods' affixes and anything outside the
 * {@code immersive_jewelry} compound. Every bench transition that turns one stack into another (a blank into a
 * finished piece, a piece back into a blank) carries it across, so nothing another mod or the player put on the item is
 * lost.
 */
public final class ForeignData {

    /** Copies every root key of {@code from} except Immersive Jewelry's own into {@code to} (keys {@code to} already has win). */
    public static void carry(ItemStack from, ItemStack to) {
        CompoundTag source = from.getTag();
        if (source == null || to.isEmpty()) return;
        CompoundTag target = to.getOrCreateTag();
        for (String key : source.getAllKeys()) {
            if (key.equals(JewelryNbt.ROOT) || target.contains(key)) continue;
            var value = source.get(key);
            if (value != null) target.put(key, value.copy());
        }
    }

    private ForeignData() {}
}
