package com.otectus.immersivejewelry.api.jewelry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The one place that knows where Immersive Jewelry keeps its item data: a namespaced {@code immersive_jewelry}
 * compound in the stack tag, so other mods' NBT (names, enchantments, Smithing quality) is never touched.
 */
public final class JewelryNbt {
    public static final String ROOT = "immersive_jewelry";
    public static final int VERSION = 1;

    @Nullable
    public static CompoundTag root(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(ROOT, Tag.TAG_COMPOUND) ? tag.getCompound(ROOT) : null;
    }

    /** Replaces the root compound with {@code data} (stamped with the current version). */
    public static void write(ItemStack stack, CompoundTag data) {
        data.putInt("Version", VERSION);
        stack.getOrCreateTag().put(ROOT, data);
    }

    public static void clear(ItemStack stack) {
        stack.removeTagKey(ROOT);
    }

    private JewelryNbt() {}
}
