package com.otectus.immersivejewelry.api.gem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One gemstone instance: which gem it is, how large, its natural character, and (once cut) its cut and cut quality.
 * Immutable; every value is clamped on construction, so data read from NBT or the network is always in range.
 *
 * @param gem            gem definition id
 * @param size           current size in carats
 * @param originalSize   size when first appraised (never below {@code size})
 * @param naturalQuality the stone's natural luster, 0..1
 * @param inclusions     how included the stone is, 0 (clean) to 1
 * @param cut            gem cut id, or null while rough
 * @param cutQuality     quality of the cut, 0..1 (0 while rough)
 * @param damage         physical damage state
 * @param qualityCap     the best cut quality this stone can still reach, lowered by fractures
 */
public record GemData(ResourceLocation gem, float size, float originalSize, float naturalQuality, float inclusions,
                      @Nullable ResourceLocation cut, float cutQuality, GemDamage damage, float qualityCap) {
    public static final float MIN_SIZE = 0.05F;
    public static final float MAX_SIZE = 5.0F;

    public GemData {
        size = clampSize(size);
        originalSize = Math.max(size, clampSize(originalSize));
        naturalQuality = clamp01(naturalQuality);
        inclusions = clamp01(inclusions);
        cutQuality = cut == null ? 0F : clamp01(cutQuality);
        qualityCap = clamp01(qualityCap);
        if (damage == null) damage = GemDamage.NONE;
    }

    public static GemData rough(ResourceLocation gem, float size, float naturalQuality, float inclusions) {
        return new GemData(gem, size, size, naturalQuality, inclusions, null, 0F, GemDamage.NONE, 1F);
    }

    public boolean isCut() {
        return cut != null;
    }

    public GemData withCut(ResourceLocation newCut, float quality, float newSize, GemDamage newDamage, float newCap) {
        return new GemData(gem, newSize, originalSize, naturalQuality, inclusions, newCut, Math.min(quality, newCap), newDamage, newCap);
    }

    public GemData withDamage(GemDamage newDamage) {
        return new GemData(gem, size, originalSize, naturalQuality, inclusions, cut, cutQuality, newDamage, qualityCap);
    }

    public GemData withCutQuality(float quality) {
        return new GemData(gem, size, originalSize, naturalQuality, inclusions, cut, quality, damage, qualityCap);
    }

    public GemData withSize(float newSize) {
        return new GemData(gem, newSize, originalSize, naturalQuality, inclusions, cut, cutQuality, damage, qualityCap);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Gem", gem.toString());
        tag.putFloat("Size", size);
        tag.putFloat("OriginalSize", originalSize);
        tag.putFloat("Natural", naturalQuality);
        tag.putFloat("Inclusions", inclusions);
        if (cut != null) {
            tag.putString("Cut", cut.toString());
            tag.putFloat("CutQuality", cutQuality);
        }
        if (damage != GemDamage.NONE) tag.putString("Damage", damage.id());
        if (qualityCap < 1F) tag.putFloat("Cap", qualityCap);
        return tag;
    }

    public static Optional<GemData> load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("Gem", Tag.TAG_STRING)) return Optional.empty();
        ResourceLocation gem = ResourceLocation.tryParse(tag.getString("Gem"));
        if (gem == null) return Optional.empty();
        ResourceLocation cut = tag.contains("Cut", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("Cut")) : null;
        float size = tag.contains("Size") ? tag.getFloat("Size") : 1F;
        float original = tag.contains("OriginalSize") ? tag.getFloat("OriginalSize") : size;
        return Optional.of(new GemData(gem, size, original, tag.getFloat("Natural"), tag.getFloat("Inclusions"), cut,
                tag.getFloat("CutQuality"), GemDamage.byId(tag.getString("Damage")),
                tag.contains("Cap") ? tag.getFloat("Cap") : 1F));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(gem);
        buf.writeFloat(size);
        buf.writeFloat(originalSize);
        buf.writeFloat(naturalQuality);
        buf.writeFloat(inclusions);
        buf.writeNullable(cut, FriendlyByteBuf::writeResourceLocation);
        buf.writeFloat(cutQuality);
        buf.writeEnum(damage);
        buf.writeFloat(qualityCap);
    }

    public static GemData read(FriendlyByteBuf buf) {
        return new GemData(buf.readResourceLocation(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readNullable(FriendlyByteBuf::readResourceLocation), buf.readFloat(), buf.readEnum(GemDamage.class), buf.readFloat());
    }

    private static float clampSize(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, MIN_SIZE, MAX_SIZE) : 1F;
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0F, 1F) : 0F;
    }
}
