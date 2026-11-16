package com.otectus.immersivejewelry.workstation.sim;

import com.otectus.immersivejewelry.data.cut.CutPattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The bounded numbers of one cut, resolved by the server from the stone, the cut, the lap, the work profile and the
 * config when the cut is planned, then saved with the work: a datapack reload never mixes new tolerances into
 * a cut already under way. Units: depth is the share of a region's planned material removed (its target window sits
 * near 0.8); force is quill travel past first contact (0..1); sweep is the stone's position across the lap (-1..1).
 *
 * @param pattern       how the regions are laid out (radial facets, step faces, dome zones)
 * @param target        depth window centre of each region
 * @param window        half-width of the depth window
 * @param weight        how much each region counts in the cut quality (step corners count less)
 * @param bins          positions across a face the sweep is resolved into (flatness)
 * @param rate          depth per second at the top of the comfortable force window
 * @param forceLow      below this the lap barely bites
 * @param forceHigh     above this pressing harder cuts no faster, only heats and scratches
 * @param sweepTolerance lateral handle speed (units per second) a face tolerates before the stone rocks
 * @param domeSpeed     for domes: the sweep speed that rounds a zone instead of grinding a flat spot into it
 * @param heatRate      heat per second at full force
 * @param coolRate      heat shed per second with the quill lifted
 * @param chipThreshold heat at which the stone chips
 * @param dopOffset     how far the stone sits turned on its dop, in trim notches (the fine trim corrects it)
 * @param azimuthTolerance trim notches of error that cost nothing
 * @param order         the recommended order of regions (guidance only)
 */
public record FacetParams(CutPattern pattern, float[] target, float window, float[] weight, int bins, float rate, float forceLow,
                          float forceHigh, float sweepTolerance, float domeSpeed, float heatRate, float coolRate, float chipThreshold,
                          float dopOffset, float azimuthTolerance, int[] order) {
    public static final int VERSION = 1;
    public static final int MIN_REGIONS = 2, MAX_REGIONS = 16;
    public static final int MAX_TRIM = 4;

    /** What the parameters are made from. Lap: {@code precision}, {@code stability}, {@code speed}, {@code cooling}. */
    public record Inputs(int regions, CutPattern pattern, float cutDifficulty, float gemDifficulty, float hardness, float brittleness,
                         float inclusions, float size, float precision, float stability, float speed, float cooling,
                         float configDifficulty, boolean widerWindows, boolean gentlerRate, Tuning tuning) {}

    /**
     * Data-driven tuning from a {@code work_profiles} facet or dome profile. {@code targetActiveSeconds} sets the work rate
     * (it is never a deadline); the rest scale the defaults.
     */
    public record Tuning(float targetActiveSeconds, float windowScale, float forceLow, float forceHigh, float sweepTolerance) {
        public static final Tuning DEFAULT = new Tuning(0F, 1F, 0.28F, 0.72F, 1.6F);
    }

    public int regions() {
        return target.length;
    }

    public static FacetParams create(Inputs in, long seed) {
        Random random = new Random(seed);
        int k = Mth.clamp(in.regions(), MIN_REGIONS, MAX_REGIONS);
        Tuning tune = in.tuning() != null ? in.tuning() : Tuning.DEFAULT;
        float difficulty = Mth.clamp(in.cutDifficulty() * in.gemDifficulty() * in.configDifficulty(), 0.2F, 4F);
        float assistWindow = in.widerWindows() ? 1.35F : 1F;

        float[] target = new float[k];
        float[] weight = new float[k];
        for (int i = 0; i < k; i++) {
            target[i] = 0.8F + (random.nextFloat() - 0.5F) * 0.08F;
            weight[i] = in.pattern() == CutPattern.STEP && i % 2 == 1 ? 0.6F : 1F;
        }
        float window = Mth.clamp(0.12F * in.precision() * assistWindow * tune.windowScale() / (float) Math.pow(difficulty, 0.5), 0.05F, 0.24F);

        float hardnessFactor = 0.6F + in.hardness() / 12F;
        // Seconds of firm grinding one region takes: about 3.6 s on an average stone, never a deadline.
        float seconds = tune.targetActiveSeconds() > 0F ? Mth.clamp(tune.targetActiveSeconds() / k, 0.5F, 60F) : 3.6F;
        float rate = 0.8F / seconds * in.speed() / hardnessFactor * 1.35F * (in.gentlerRate() ? 0.75F : 1F);
        rate = Mth.clamp(rate, 0.02F, 3F);

        float forceLow = Mth.clamp(tune.forceLow(), 0.05F, 0.6F);
        float forceHigh = Mth.clamp(tune.forceHigh(), forceLow + 0.1F, 0.95F);
        float sweep = Mth.clamp(tune.sweepTolerance() * in.stability() * (in.widerWindows() ? 1.3F : 1F) / (0.7F + 0.6F * in.brittleness()),
                0.4F, 6F);
        float heatRate = 0.36F * (in.gentlerRate() ? 0.8F : 1F);
        float coolRate = 0.42F * in.cooling();
        float chipThreshold = Math.max(0.45F, 1F - 0.3F * in.brittleness() - 0.25F * in.inclusions());
        // A stone glued a little turned on its dop: up to about two notches, corrected by the fine trim.
        float dop = (random.nextFloat() - 0.5F) * 4F * Mth.clamp(difficulty, 0.5F, 1.5F);
        if (in.pattern() == CutPattern.DOME) dop = 0F;
        float azimuthTolerance = Mth.clamp(1.2F * in.precision() * assistWindow / difficulty, 0.4F, 3F);
        int bins = in.pattern() == CutPattern.DOME ? 8 : 6;
        return new FacetParams(in.pattern(), target, window, weight, bins, rate, forceLow, forceHigh, sweep,
                in.pattern() == CutPattern.DOME ? 0.2F : 0F, heatRate, coolRate, chipThreshold, dop, azimuthTolerance,
                order(k, in.pattern()));
    }

    /** Opposite faces in turn for radial cuts (symmetry), long sides before corners for step cuts, round the dome otherwise. */
    static int[] order(int k, CutPattern pattern) {
        int[] out = new int[k];
        int n = 0;
        if (pattern == CutPattern.RADIAL && k % 2 == 0) {
            for (int i = 0; i < k / 2; i++) {
                out[n++] = i;
                out[n++] = i + k / 2;
            }
        } else if (pattern == CutPattern.STEP) {
            for (int i = 0; i < k; i += 2) out[n++] = i;
            for (int i = 1; i < k; i += 2) out[n++] = i;
        } else {
            for (int i = 0; i < k; i++) out[n++] = i;
        }
        return out;
    }

    /** The region across the stone from {@code i} for an even radial cut, else -1. */
    public int opposite(int i) {
        int k = regions();
        return pattern == CutPattern.RADIAL && k % 2 == 0 ? (i + k / 2) % k : -1;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("V", VERSION);
        tag.putString("Pattern", pattern.name());
        SimIO.putFloats(tag, "Target", target);
        tag.putFloat("Window", window);
        SimIO.putFloats(tag, "Weight", weight);
        tag.putInt("Bins", bins);
        tag.putFloat("Rate", rate);
        tag.putFloat("ForceLow", forceLow);
        tag.putFloat("ForceHigh", forceHigh);
        tag.putFloat("Sweep", sweepTolerance);
        tag.putFloat("DomeSpeed", domeSpeed);
        tag.putFloat("Heat", heatRate);
        tag.putFloat("Cool", coolRate);
        tag.putFloat("Chip", chipThreshold);
        tag.putFloat("Dop", dopOffset);
        tag.putFloat("Azimuth", azimuthTolerance);
        SimIO.putInts(tag, "Order", order);
        return tag;
    }

    public static FacetParams load(CompoundTag tag) {
        CutPattern pattern;
        try {
            pattern = CutPattern.valueOf(tag.getString("Pattern"));
        } catch (IllegalArgumentException e) {
            pattern = CutPattern.RADIAL;
        }
        int k = Mth.clamp(tag.getList("Target", 5).size(), MIN_REGIONS, MAX_REGIONS);
        return new FacetParams(pattern, SimIO.getFloats(tag, "Target", k, 0.8F, 0.3F, 1.2F),
                SimIO.finite(tag.getFloat("Window"), 0.02F, 0.4F), SimIO.getFloats(tag, "Weight", k, 1F, 0.1F, 2F),
                Mth.clamp(tag.getInt("Bins"), 2, 8), SimIO.finite(tag.getFloat("Rate"), 0.02F, 3F),
                SimIO.finite(tag.getFloat("ForceLow"), 0.01F, 0.9F), SimIO.finite(tag.getFloat("ForceHigh"), 0.1F, 1F),
                SimIO.finite(tag.getFloat("Sweep"), 0.2F, 8F), SimIO.finite(tag.getFloat("DomeSpeed"), 0F, 4F),
                SimIO.finite(tag.getFloat("Heat"), 0F, 4F), SimIO.finite(tag.getFloat("Cool"), 0.01F, 4F),
                SimIO.finite(tag.getFloat("Chip"), 0.2F, 2F), SimIO.finite(tag.getFloat("Dop"), -MAX_TRIM, MAX_TRIM),
                SimIO.finite(tag.getFloat("Azimuth"), 0.1F, 4F), sanitizeOrder(SimIO.getInts(tag, "Order", k, 0, 0, k - 1), k));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static FacetParams read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return load(tag == null ? new CompoundTag() : tag);
    }

    private static int[] sanitizeOrder(int[] order, int k) {
        boolean[] seen = new boolean[k];
        for (int i : order) {
            if (seen[i]) return order(k, CutPattern.RADIAL);
            seen[i] = true;
        }
        return order;
    }
}
