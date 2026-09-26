package com.otectus.immersivejewelry.api.jewelry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A metal blank or frame (the form is the item). Holds what the metalwork contributes to a finished piece.
 *
 * @param metal           the metal
 * @param smithingQuality quality of the metalwork, 0..1 (Immersive Smithing's overall score / 100)
 * @param integrity       structural condition carried from earlier use, 0..1 (1 for a new blank)
 * @param reworks         how many times stones were removed from this frame
 * @param fallback        crafted without Immersive Smithing
 * @param smithing        copy of Immersive Smithing's quality compound, or null
 */
public record BlankData(MetalRef metal, float smithingQuality, float integrity, int reworks, boolean fallback,
                        @Nullable CompoundTag smithing) {

    public BlankData {
        if (metal == null) metal = MetalRef.UNKNOWN;
        smithingQuality = Float.isFinite(smithingQuality) ? Mth.clamp(smithingQuality, 0F, 1F) : 0.5F;
        integrity = Float.isFinite(integrity) ? Mth.clamp(integrity, 0F, 1F) : 1F;
        reworks = Mth.clamp(reworks, 0, 1000);
    }

    public static BlankData of(MetalRef metal, float smithingQuality) {
        return new BlankData(metal, smithingQuality, 1F, 0, false, null);
    }

    public static Optional<BlankData> get(ItemStack stack) {
        CompoundTag root = JewelryNbt.root(stack);
        if (root == null || !root.contains("Metal", Tag.TAG_STRING)) return Optional.empty();
        return Optional.of(load(root));
    }

    public static BlankData load(CompoundTag root) {
        return new BlankData(MetalRef.load(root), root.contains("SmithingQuality") ? root.getFloat("SmithingQuality") : 0.5F,
                root.contains("Integrity") ? root.getFloat("Integrity") : 1F, root.getInt("Reworks"), root.getBoolean("Fallback"),
                root.contains("Smithing", Tag.TAG_COMPOUND) ? root.getCompound("Smithing").copy() : null);
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        metal.save(root);
        root.putFloat("SmithingQuality", smithingQuality);
        if (integrity < 1F) root.putFloat("Integrity", integrity);
        if (reworks > 0) root.putInt("Reworks", reworks);
        if (fallback) root.putBoolean("Fallback", true);
        if (smithing != null) root.put("Smithing", smithing.copy());
        return root;
    }

    public void apply(ItemStack stack) {
        JewelryNbt.write(stack, save());
    }

    public BlankData withSmithing(MetalRef newMetal, float quality, @Nullable CompoundTag smithingTag) {
        return new BlankData(newMetal, quality, integrity, reworks, false, smithingTag);
    }
}
