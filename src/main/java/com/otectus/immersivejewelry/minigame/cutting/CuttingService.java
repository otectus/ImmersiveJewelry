package com.otectus.immersivejewelry.minigame.cutting;

import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.Appraisal;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.LapItem;
import com.otectus.immersivejewelry.network.packet.OpenCutSelectPacket;
import com.otectus.immersivejewelry.quality.CutGrade;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The rules around a cut that are not the minigame itself: appraisal, the numbers each cut offers, the outcome. */
public final class CuttingService {

    /** Rolls the natural character of a raw stone (spec 9). Done once; the result is kept on the stone. */
    public static GemData appraise(GemDefinition gem, RandomSource random) {
        Appraisal.Roll roll = gem.appraisal().roll(random);
        return GemData.rough(gem.id(), roll.size(), roll.natural(), roll.inclusions());
    }

    /** Size after cutting: first cuts lose the stone's skin and the cut's waste, a recut only part of the cut's waste. */
    public static float retention(GemDefinition gem, CutDefinition cut, boolean recut) {
        return recut ? (float) Math.sqrt(cut.sizeRetention()) : gem.sizeRetention() * cut.sizeRetention();
    }

    public static float displayDifficulty(GemDefinition gem, CutDefinition cut) {
        return Mth.clamp(gem.cuttingDifficulty() * cut.difficulty() * (float) ServerConfig.get(ServerConfig.CUTTING_DIFFICULTY) / 1.6F, 0F, 1F);
    }

    public static List<OpenCutSelectPacket.Option> options(JewelryDefinitions defs, GemDefinition gem, GemData stone, boolean recut) {
        List<OpenCutSelectPacket.Option> out = new ArrayList<>();
        for (CutDefinition cut : defs.cutsFor(gem)) {
            float potency = cut.qualityModifier() * (gem.favors(cut.id()) ? 1.1F : 1F);
            out.add(new OpenCutSelectPacket.Option(cut.id(), displayDifficulty(gem, cut), stone.size() * retention(gem, cut, recut),
                    potency, cut.stability(), cut.facets()));
        }
        return out;
    }

    public static CutParams params(GemDefinition gem, GemData stone, CutDefinition cut, LapItem.Tier tier, boolean larger, boolean slower, long seed) {
        return params(gem, stone, cut, tier, larger, slower, 1F, seed);
    }

    /**
     * @param assistDifficulty the per-player difficulty relief (1 = none), already clamped by
     *                         {@code ImmersiveJewelryAPI.assistDifficulty}; multiplies the server's cutting difficulty
     */
    public static CutParams params(GemDefinition gem, GemData stone, CutDefinition cut, LapItem.Tier tier, boolean larger, boolean slower,
                                   float assistDifficulty, long seed) {
        boolean assist = ServerConfig.get(ServerConfig.ALLOW_ASSIST);
        CutParams.Inputs in = new CutParams.Inputs(cut.facets(), cut.pattern(), cut.difficulty(), gem.cuttingDifficulty(), gem.hardness(),
                gem.brittleness(), stone.inclusions(), stone.size(), tier.precision, tier.stability, tier.speed, tier.cooling,
                (float) ServerConfig.get(ServerConfig.CUTTING_DIFFICULTY) * assistDifficulty, assist && larger, assist && slower,
                ServerConfig.get(ServerConfig.CUTTING_SECONDS_PER_FACET));
        return CutParams.create(in, seed);
    }

    /** The result of a finished simulation. {@code after} is null when the stone shattered. */
    public record Outcome(@Nullable GemData after, float quality, boolean shattered) {}

    public static Outcome outcome(GemDefinition gem, CutDefinition cut, GemData stone, boolean recut, CutSimulation sim) {
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

    /** Experience at the configured amount for a Fine cut, less for poor work, more for flawless or difficult cuts. */
    public static int experience(float quality, GemDefinition gem, CutDefinition cut) {
        int base = ServerConfig.get(ServerConfig.CUTTING_EXPERIENCE);
        if (base <= 0) return 0;
        return Math.max(1, Math.round(base * (0.4F + quality) * (0.7F + 0.3F * gem.cuttingDifficulty() * cut.difficulty())));
    }

    private CuttingService() {}
}
