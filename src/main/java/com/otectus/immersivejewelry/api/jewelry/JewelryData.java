package com.otectus.immersivejewelry.api.jewelry;

import com.otectus.immersivejewelry.api.gem.GemData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The full composition of a finished piece: form, metal, set stones, design, finish and craftsmanship. Immutable and
 * clamped. Workmanship is stored as computed when the piece was made, so later config changes never rewrite history.
 *
 * @param form             jewelry form id
 * @param metal            frame metal
 * @param gems             the set stones, principal stone first (all of the same gem)
 * @param design           the design it was made to, or null (converted or commanded pieces)
 * @param finish           finish id, or null
 * @param smithingQuality  metalwork quality, 0..1
 * @param settingQuality   stone-setting quality, 0..1
 * @param workmanship      overall workmanship, 0..1
 * @param integrity        structural condition, 0..1 (0 = broken and inert)
 * @param reworks          how many times stones were removed from this frame
 * @param defects          setting defects left by the setter
 * @param maker            the jeweler's name, or null
 * @param legacyConverted  converted from Jewelcraft Reforged
 * @param smithing         copy of Immersive Smithing's quality compound from the blank, or null
 */
public record JewelryData(ResourceLocation form, MetalRef metal, List<GemData> gems, @Nullable ResourceLocation design,
                          @Nullable ResourceLocation finish, float smithingQuality, float settingQuality, float workmanship,
                          float integrity, int reworks, Set<SettingDefect> defects, @Nullable String maker,
                          boolean legacyConverted, @Nullable CompoundTag smithing) {
    public static final int MAX_GEMS = 8;

    public JewelryData {
        if (metal == null) metal = MetalRef.UNKNOWN;
        gems = gems == null ? List.of() : List.copyOf(gems.size() > MAX_GEMS ? gems.subList(0, MAX_GEMS) : gems);
        smithingQuality = clamp01(smithingQuality);
        settingQuality = clamp01(settingQuality);
        workmanship = clamp01(workmanship);
        integrity = clamp01(integrity);
        reworks = Mth.clamp(reworks, 0, 1000);
        defects = defects == null || defects.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(defects));
        if (maker != null && maker.length() > 64) maker = maker.substring(0, 64);
    }

    public boolean hasGem() {
        return !gems.isEmpty();
    }

    @Nullable
    public GemData principal() {
        return gems.isEmpty() ? null : gems.get(0);
    }

    /** Mean cut quality of the set stones (0 without stones). */
    public float cutQuality() {
        if (gems.isEmpty()) return 0F;
        float sum = 0F;
        for (GemData g : gems) sum += g.cutQuality();
        return sum / gems.size();
    }

    public boolean isBroken() {
        return integrity <= 0F;
    }

    public JewelryData withIntegrity(float value) {
        return new JewelryData(form, metal, gems, design, finish, smithingQuality, settingQuality, workmanship, value, reworks,
                defects, maker, legacyConverted, smithing);
    }

    public JewelryData withFinish(@Nullable ResourceLocation value) {
        return new JewelryData(form, metal, gems, design, value, smithingQuality, settingQuality, workmanship, integrity, reworks,
                defects, maker, legacyConverted, smithing);
    }

    public JewelryData withGems(List<GemData> value) {
        return new JewelryData(form, metal, value, design, finish, smithingQuality, settingQuality, workmanship, integrity, reworks,
                defects, maker, legacyConverted, smithing);
    }

    public JewelryData withSetting(float setting, float newWorkmanship, Set<SettingDefect> newDefects) {
        return new JewelryData(form, metal, gems, design, finish, smithingQuality, setting, newWorkmanship, integrity, reworks,
                newDefects, maker, legacyConverted, smithing);
    }

    /** The frame left behind when every stone is removed. */
    public BlankData toBlank(float integrityCost) {
        return new BlankData(metal, smithingQuality, integrity - integrityCost, reworks + 1, false, smithing);
    }

    public static Optional<JewelryData> get(ItemStack stack) {
        CompoundTag root = JewelryNbt.root(stack);
        if (root == null || !root.contains("Form", Tag.TAG_STRING)) return Optional.empty();
        return load(root);
    }

    public static Optional<JewelryData> load(CompoundTag root) {
        ResourceLocation form = ResourceLocation.tryParse(root.getString("Form"));
        if (form == null) return Optional.empty();
        List<GemData> gems = new ArrayList<>();
        for (Tag t : root.getList("Gems", Tag.TAG_COMPOUND)) {
            GemData.load((CompoundTag) t).ifPresent(gems::add);
        }
        EnumSet<SettingDefect> defects = EnumSet.noneOf(SettingDefect.class);
        for (Tag t : root.getList("Defects", Tag.TAG_STRING)) {
            SettingDefect d = SettingDefect.byId(t.getAsString());
            if (d != null) defects.add(d);
        }
        return Optional.of(new JewelryData(form, MetalRef.load(root), gems, optionalId(root, "Design"), optionalId(root, "Finish"),
                root.getFloat("SmithingQuality"), root.getFloat("SettingQuality"), root.getFloat("Workmanship"),
                root.contains("Integrity") ? root.getFloat("Integrity") : 1F, root.getInt("Reworks"), defects,
                root.contains("Maker", Tag.TAG_STRING) ? root.getString("Maker") : null, root.getBoolean("LegacyConverted"),
                root.contains("Smithing", Tag.TAG_COMPOUND) ? root.getCompound("Smithing").copy() : null));
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putString("Form", form.toString());
        metal.save(root);
        ListTag list = new ListTag();
        for (GemData g : gems) list.add(g.save());
        root.put("Gems", list);
        if (design != null) root.putString("Design", design.toString());
        if (finish != null) root.putString("Finish", finish.toString());
        root.putFloat("SmithingQuality", smithingQuality);
        root.putFloat("CutQuality", cutQuality()); // informational: always recomputed from the stones
        root.putFloat("SettingQuality", settingQuality);
        root.putFloat("Workmanship", workmanship);
        root.putFloat("Integrity", integrity);
        if (reworks > 0) root.putInt("Reworks", reworks);
        if (!defects.isEmpty()) {
            ListTag d = new ListTag();
            for (SettingDefect defect : defects) d.add(StringTag.valueOf(defect.id()));
            root.put("Defects", d);
        }
        if (maker != null) root.putString("Maker", maker);
        if (legacyConverted) root.putBoolean("LegacyConverted", true);
        if (smithing != null) root.put("Smithing", smithing.copy());
        return root;
    }

    public void apply(ItemStack stack) {
        CompoundTag previous = JewelryNbt.root(stack);
        CompoundTag root = save();
        // Keep bookkeeping that is not part of the composition (the legacy source compound).
        if (previous != null && previous.contains("Legacy", Tag.TAG_COMPOUND)) root.put("Legacy", previous.getCompound("Legacy"));
        JewelryNbt.write(stack, root);
    }

    @Nullable
    private static ResourceLocation optionalId(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(key)) : null;
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0F, 1F) : 0F;
    }
}
