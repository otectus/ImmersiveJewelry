package com.otectus.immersivejewelry.workstation.sim;

import com.otectus.immersivejewelry.recipe.SettingStyle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * The bounded numbers of one setting, resolved by the server when the first irreversible closure commits the piece and
 * saved with the work. Units: a point's closure is 0 (open) .. 1 (just touching the stone) .. 1.4 (crushed); handle
 * travel is 0..1 of the pliers' or pusher's stroke.
 *
 * @param sockets    stones in the piece (1..8)
 * @param points     prongs, or bezel sectors, around each stone (2..12)
 * @param tolerance  half-width of the secure band around a closure of 1
 * @param gain       closure per unit of handle travel, before the stone's resistance
 * @param resistance how much the stone slows the last tenth of a closure (0 none .. 1 hard stop)
 * @param springBack share of a squeeze that springs back on release
 * @param chipAbove  closure past which a brittle stone chips (above 2 = never)
 * @param pushRate   bezel: closure per second under a full push
 * @param kernel     bezel: how far round the rim one push reaches (radians)
 * @param pryLimit   how far one pry can ease an overbent point back
 */
public record SettingParams(SettingStyle style, int sockets, int points, float tolerance, float gain, float resistance, float springBack,
                            float chipAbove, float pushRate, float kernel, float pryLimit) {
    public static final int VERSION = 1;
    public static final int MAX_SOCKETS = 8;
    public static final int MAX_POINTS = 12;

    /**
     * @param tightness  the design's tightness (narrows the band)
     * @param precision  the tool's precision (Fine Pliers widen the band); the bezel pusher counts as 1
     * @param stoneSize  mean stone size in carats (small stones are fiddlier)
     * @param brittleness the most brittle stone's brittleness
     */
    public record Inputs(SettingStyle style, int sockets, int points, float tightness, float precision, float stoneSize, float brittleness,
                         float configDifficulty, boolean widerWindows, boolean gentlerRate, Tuning tuning) {}

    /** Data-driven tuning from a {@code work_profiles} prong or bezel profile. */
    public record Tuning(float toleranceScale, float targetActiveSeconds) {
        public static final Tuning DEFAULT = new Tuning(1F, 0F);
    }

    public static SettingParams create(Inputs in) {
        Tuning tune = in.tuning() != null ? in.tuning() : Tuning.DEFAULT;
        int sockets = Mth.clamp(in.sockets(), 1, MAX_SOCKETS);
        int points = Mth.clamp(in.points(), 2, MAX_POINTS);
        float difficulty = Mth.clamp(in.tightness() * in.configDifficulty(), 0.25F, 4F);
        float small = 0.9F + 0.1F * Mth.clamp(in.stoneSize(), 0.3F, 1.5F);
        float tolerance = Mth.clamp(0.1F * in.precision() * small * (in.widerWindows() ? 1.4F : 1F) * tune.toleranceScale()
                / (float) Math.sqrt(difficulty) * (in.style() == SettingStyle.BEZEL ? 1.15F : 1F), 0.035F, 0.25F);
        float chipAbove = in.brittleness() > 0.3F ? 1.02F + 2.5F * tolerance - 0.12F * in.brittleness() : 2.5F;
        float seconds = tune.targetActiveSeconds() > 0F ? Mth.clamp(tune.targetActiveSeconds(), 4F, 120F) : 14F;
        // A bezel sector needs about a second of full push; a whole rim about the target time when swept evenly.
        float pushRate = Mth.clamp(points * sockets * 1.1F / seconds, 0.3F, 4F) * (in.gentlerRate() ? 0.8F : 1F);
        // A full squeeze from open reaches about 1.25: well past the band, so overbending is always possible; the
        // stone's resistance in the last tenth makes the band a quarter of the handle's travel.
        return new SettingParams(in.style(), sockets, points, tolerance, 1.6F, 0.5F, 0.05F, chipAbove, pushRate,
                Mth.TWO_PI / points * 0.9F, 0.2F);
    }

    /** Where point {@code i} sits around its stone (radians). Prongs sit between bezel sectors' centres. */
    public float angle(int point) {
        float rotation = style == SettingStyle.BEZEL ? 0F : Mth.PI / points;
        return rotation + point * Mth.TWO_PI / points;
    }

    /** The point across the stone from {@code point}, or -1 for an odd count. */
    public int opposite(int point) {
        return points % 2 == 0 ? (point + points / 2) % points : -1;
    }

    /** The recommended order around one stone: each point, then the one across from it (1, 3, 2, 4 for four prongs). */
    public int[] order() {
        int[] out = new int[points];
        int n = 0;
        if (points % 2 == 0) {
            for (int i = 0; i < points / 2; i++) {
                out[n++] = i;
                out[n++] = i + points / 2;
            }
        } else {
            for (int i = 0; i < points; i++) out[n++] = (i * 2) % points;
        }
        return out;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("V", VERSION);
        tag.putString("Style", style.id());
        tag.putInt("Sockets", sockets);
        tag.putInt("Points", points);
        tag.putFloat("Tolerance", tolerance);
        tag.putFloat("Gain", gain);
        tag.putFloat("Resistance", resistance);
        tag.putFloat("SpringBack", springBack);
        tag.putFloat("ChipAbove", chipAbove);
        tag.putFloat("PushRate", pushRate);
        tag.putFloat("Kernel", kernel);
        tag.putFloat("PryLimit", pryLimit);
        return tag;
    }

    public static SettingParams load(CompoundTag tag) {
        return new SettingParams(SettingStyle.byId(tag.getString("Style")), Mth.clamp(tag.getInt("Sockets"), 1, MAX_SOCKETS),
                Mth.clamp(tag.getInt("Points"), 2, MAX_POINTS), SimIO.finite(tag.getFloat("Tolerance"), 0.02F, 0.4F),
                SimIO.finite(tag.getFloat("Gain"), 0.3F, 3F), SimIO.finite(tag.getFloat("Resistance"), 0F, 0.95F),
                SimIO.finite(tag.getFloat("SpringBack"), 0F, 0.5F), SimIO.finite(tag.getFloat("ChipAbove"), 1F, 3F),
                SimIO.finite(tag.getFloat("PushRate"), 0.1F, 6F), SimIO.finite(tag.getFloat("Kernel"), 0.05F, Mth.PI),
                SimIO.finite(tag.getFloat("PryLimit"), 0.05F, 0.6F));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static SettingParams read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return load(tag == null ? new CompoundTag() : tag);
    }
}
