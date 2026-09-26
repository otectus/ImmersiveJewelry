package com.otectus.immersivejewelry.data.gem;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * How a raw stone of this gem is appraised when it first reaches a Lapidary Wheel: size range in carats, and the
 * mean and spread of its natural quality and inclusions. Spreads are triangular, so extremes are rare.
 */
public record Appraisal(float sizeMin, float sizeMax, float qualityMean, float qualitySpread, float inclusionsMean,
                        float inclusionsSpread) {
    public static final Appraisal DEFAULT = new Appraisal(0.7F, 1.3F, 0.55F, 0.3F, 0.25F, 0.2F);

    public record Roll(float size, float natural, float inclusions) {}

    public static Appraisal parse(JsonObject gem) {
        if (!gem.has("appraisal")) return DEFAULT;
        JsonObject o = GsonHelper.getAsJsonObject(gem, "appraisal");
        float min = Json.num(o, "size_min", DEFAULT.sizeMin, 0.05F, 5F);
        float max = Json.num(o, "size_max", Math.max(min, DEFAULT.sizeMax), min, 5F);
        return new Appraisal(min, max, Json.num(o, "quality_mean", DEFAULT.qualityMean, 0F, 1F),
                Json.num(o, "quality_spread", DEFAULT.qualitySpread, 0F, 1F),
                Json.num(o, "inclusions_mean", DEFAULT.inclusionsMean, 0F, 1F),
                Json.num(o, "inclusions_spread", DEFAULT.inclusionsSpread, 0F, 1F));
    }

    public Roll roll(RandomSource random) {
        float size = Mth.lerp((random.nextFloat() + random.nextFloat()) / 2F, sizeMin, sizeMax);
        float natural = Mth.clamp(qualityMean + (random.nextFloat() - random.nextFloat()) * qualitySpread, 0.02F, 1F);
        float inclusions = Mth.clamp(inclusionsMean + (random.nextFloat() - random.nextFloat()) * inclusionsSpread, 0F, 1F);
        return new Roll(size, natural, inclusions);
    }
}
