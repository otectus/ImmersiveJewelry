package com.otectus.immersivejewelry.workstation;

import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.cut.CutPattern;
import com.otectus.immersivejewelry.data.gem.Appraisal;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.work.WorkProfile;
import com.otectus.immersivejewelry.item.LapItem;
import com.otectus.immersivejewelry.quality.CutGrade;
import com.otectus.immersivejewelry.workstation.sim.FacetParams;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * The rules around a cut that are not the grinding itself: appraisal, what a cut costs in size, the parameters a cut is
 * planned with, the outcome of a finished cut and of changing the plan after work started. The size, quality-cap and
 * damage rules are those of 1.1 unchanged; only how the player produces the cut quality changed.
 */
public final class CutPlanner {

    /** Rolls the natural character of a raw stone (spec 9). Done once; the result is kept on the stone. */
    public static GemData appraise(GemDefinition gem, RandomSource random) {
        Appraisal.Roll roll = gem.appraisal().roll(random);
        return GemData.rough(gem.id(), roll.size(), roll.natural(), roll.inclusions());
    }

    /** Size after cutting: first cuts lose the stone's skin and the cut's waste, a recut only part of the cut's waste. */
    public static float retention(GemDefinition gem, CutDefinition cut, boolean recut) {
        return recut ? (float) Math.sqrt(cut.sizeRetention()) : gem.sizeRetention() * cut.sizeRetention();
    }

    /** 0..1 for display: how demanding this cut is on this gem at this server's difficulty. */
    public static float displayDifficulty(GemDefinition gem, CutDefinition cut) {
        return Mth.clamp(gem.cuttingDifficulty() * cut.difficulty() * (float) ServerConfig.get(ServerConfig.CUTTING_DIFFICULTY) / 1.6F, 0F, 1F);
    }

    /** The interaction a cut pattern is worked with. */
    public static WorkProfile.Interaction interaction(CutDefinition cut) {
        return cut.pattern() == CutPattern.DOME ? WorkProfile.Interaction.DOME : WorkProfile.Interaction.FACET;
    }

    /**
     * The parameters of a new cut.
     *
     * @param assistDifficulty the per-player difficulty relief (1 = none) from {@code ImmersiveJewelryAPI.assistDifficulty}
     */
    public static FacetParams params(JewelryDefinitions defs, GemDefinition gem, GemData stone, CutDefinition cut, LapItem.Tier tier,
                                     WorkSessionManager.Preferences prefs, float assistDifficulty, long seed) {
        boolean assist = ServerConfig.get(ServerConfig.ALLOW_ASSIST);
        WorkProfile profile = defs.workProfile(cut.workProfile(), interaction(cut));
        FacetParams.Tuning tuning = profile == null ? FacetParams.Tuning.DEFAULT
                : new FacetParams.Tuning(profile.targetActiveSeconds(), profile.toleranceScale(), profile.forceMinimum(),
                profile.forceMaximum(), profile.sweepTolerance());
        FacetParams.Inputs in = new FacetParams.Inputs(cut.facets(), cut.pattern(), cut.difficulty(), gem.cuttingDifficulty(),
                gem.hardness(), gem.brittleness(), stone.inclusions(), stone.size(), tier.precision, tier.stability, tier.speed,
                tier.cooling, (float) ServerConfig.get(ServerConfig.CUTTING_DIFFICULTY) * assistDifficulty,
                assist && prefs.widerWindows(), assist && prefs.gentlerRate(), tuning);
        return FacetParams.create(in, seed);
    }

    /** The result of finishing a cut. {@code after} is null when the stone shattered. */
    public record Outcome(@Nullable GemData after, float quality, boolean shattered) {}

    public static Outcome outcome(GemDefinition gem, CutDefinition cut, GemData stone, boolean recut, FacetSim sim) {
        float severity = (float) ServerConfig.get(ServerConfig.GEM_FAILURE_SEVERITY);
        float quality = Math.min(sim.quality(), stone.qualityCap());
        float ruined = CutGrade.thresholds()[0];
        if (ServerConfig.get(ServerConfig.CATASTROPHIC_GEM_FAILURE) && quality < ruined * 0.5F && sim.chips() >= 3) {
            return new Outcome(null, quality, true);
        }
        float loss = Math.min(0.6F, sim.sizeLoss() * severity);
        float size = stone.size() * retention(gem, cut, recut) * (1F - loss);
        float cap = Math.max(0.5F, stone.qualityCap() - 0.03F * sim.chips() * severity);
        GemDamage damage = GemDamage.NONE;
        if (quality < ruined) {
            damage = GemDamage.FRACTURED;
            cap = Math.min(cap, 0.85F);
        }
        return new Outcome(stone.withCut(cut.id(), quality, size, damage, cap), quality, false);
    }

    /**
     * Changing the plan after material was removed: the stone keeps what the grinding took (a share of the planned
     * waste for the work done, plus chips and overcutting) and the chips lower its quality cap. Nothing is restored.
     */
    public static GemData afterAbandonedWork(GemDefinition gem, CutDefinition cut, GemData stone, boolean recut, FacetSim sim) {
        float severity = (float) ServerConfig.get(ServerConfig.GEM_FAILURE_SEVERITY);
        float planned = 1F - retention(gem, cut, recut);
        float done = sim.overallProgress();
        float loss = Math.min(0.6F, sim.sizeLoss() * severity);
        float size = stone.size() * (1F - planned * done) * (1F - loss);
        float cap = Math.max(0.5F, stone.qualityCap() - 0.03F * sim.chips() * severity);
        GemDamage damage = sim.chips() >= 2 ? stone.damage().worse() : stone.damage();
        return new GemData(stone.gem(), size, stone.originalSize(), stone.naturalQuality(), stone.inclusions(), stone.cut(),
                Math.min(stone.cutQuality(), cap), damage, cap);
    }

    /** Experience at the configured amount for a Fine cut, less for poor work, more for flawless or difficult cuts. */
    public static int experience(float quality, GemDefinition gem, CutDefinition cut) {
        int base = ServerConfig.get(ServerConfig.CUTTING_EXPERIENCE);
        if (base <= 0) return 0;
        return Math.max(1, Math.round(base * (0.4F + quality) * (0.7F + 0.3F * gem.cuttingDifficulty() * cut.difficulty())));
    }

    private CutPlanner() {}
}
