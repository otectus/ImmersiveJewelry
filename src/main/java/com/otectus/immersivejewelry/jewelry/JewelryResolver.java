package com.otectus.immersivejewelry.jewelry;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.ExpressionDefinition;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.metal.MetalDefinition;
import com.otectus.immersivejewelry.data.trait.TraitDefinition;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a piece's composition into behaviour (spec 15, 31):
 * <pre>
 *   stone     = (0.75 + 0.45 natural) x (1 - 0.35 inclusions) x (0.7 + 0.3 min(size, 1.6)) x damage
 *               x cut modifier x (0.6 + 0.5 cut quality) x (1.1 if the gem favours the cut)
 *   gems      = mean(stone) x (1 + 0.12 per extra stone)
 *   power     = gems x (0.7 + 0.5 workmanship) x finish potency x metal potency x integrity factor x config
 *               (soft-capped above 1), then x metal affinity and finish multiplier for the effect's category
 *   stability = cut stability x finish stability;   responsiveness = metal responsiveness
 * </pre>
 * The same code runs on both sides (tooltips on the client, effects on the server).
 */
public final class JewelryResolver {

    public static ResolvedJewelry resolve(JewelryData data, JewelryDefinitions defs) {
        FormDefinition form = defs.form(data.form());
        if (form == null) form = FormDefinition.fallback(data.form(), data.form());
        MetalDefinition metal = defs.metal(data.metal());
        GemData principal = data.principal();
        GemDefinition gem = principal == null ? null : defs.gemOrGeneric(principal.gem());
        CutDefinition cut = principal == null ? null : defs.cut(principal.cut());
        FinishDefinition finish = defs.finish(data.finish());
        TraitDefinition trait = gem == null ? null : defs.traitFor(gem);

        float basePower = basePower(data, defs, metal, finish);
        float stability = (cut != null ? cut.stability() : 1F) * (finish != null ? finish.stability() : 1F);
        float responsiveness = metal.responsiveness();

        String inert = null;
        if (data.isBroken()) inert = "broken";
        else if (principal == null || gem == null) inert = "no_stone";
        else if (trait == null) inert = "no_trait";

        List<ResolvedJewelry.Expression> expressions = new ArrayList<>();
        if (trait != null) {
            for (ExpressionDefinition e : trait.forForm(form.id())) {
                expressions.add(new ResolvedJewelry.Expression(e, categoryPower(basePower, e, metal, finish), false));
            }
        }
        for (ExpressionDefinition e : metal.bonus()) {
            // A metal's own effect does not depend on the stone, only on the frame and the work.
            float frame = (0.7F + 0.5F * data.workmanship()) * integrityFactor(data.integrity()) * configMultiplier();
            expressions.add(new ResolvedJewelry.Expression(e, categoryPower(frame, e, metal, finish), true));
        }
        return new ResolvedJewelry(data, form, metal, gem, cut, finish, trait, basePower, stability, responsiveness,
                List.copyOf(expressions), inert);
    }

    public static float basePower(JewelryData data, JewelryDefinitions defs, MetalDefinition metal, @Nullable FinishDefinition finish) {
        if (data.gems().isEmpty()) return 0F;
        float sum = 0F;
        for (GemData g : data.gems()) sum += stonePotency(g, defs.gemOrGeneric(g.gem()), defs.cut(g.cut()));
        float gems = sum / data.gems().size() * (1F + 0.12F * (data.gems().size() - 1));
        float power = gems * (0.7F + 0.5F * data.workmanship()) * (finish != null ? finish.gemPotency() : 1F) * metal.potency()
                * integrityFactor(data.integrity()) * configMultiplier();
        return softCap(power);
    }

    /** One stone's contribution: its natural character, size, damage and cut. */
    public static float stonePotency(GemData g, @Nullable GemDefinition gem, @Nullable CutDefinition cut) {
        float natural = (0.75F + 0.45F * g.naturalQuality()) * (1F - 0.35F * g.inclusions());
        float size = 0.7F + 0.3F * Math.min(g.size(), 1.6F);
        float cutFactor = cut == null ? 0.6F : cut.qualityModifier() * (0.6F + 0.5F * g.cutQuality());
        if (gem != null && cut != null && gem.favors(cut.id())) cutFactor *= 1.1F;
        return natural * size * g.damage().potency() * cutFactor;
    }

    private static float categoryPower(float base, ExpressionDefinition e, MetalDefinition metal, @Nullable FinishDefinition finish) {
        float p = base * metal.affinity(e.category()) * (finish != null ? finish.category(e.category()) : 1F);
        return Mth.clamp(p, 0F, 4F);
    }

    /** Full strength down to a quarter of integrity, then fading; a broken piece is inert. */
    public static float integrityFactor(float integrity) {
        if (integrity <= 0F) return 0F;
        return Math.min(1F, 0.4F + integrity * 2.4F);
    }

    private static float softCap(float power) {
        return power <= 1F ? power : 1F + (power - 1F) * 0.7F;
    }

    private static float configMultiplier() {
        return (float) ServerConfig.get(ServerConfig.POWER_MULTIPLIER);
    }

    private JewelryResolver() {}
}
