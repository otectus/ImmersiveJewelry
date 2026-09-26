package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;

import java.util.Set;

/** Foes the wearer defeats drop more experience (lapis ring, "Scholar's Seal"). */
final class TrophyExperienceEffect extends JewelryEffect {
    private final float bonus;

    private TrophyExperienceEffect(float bonus) {
        this.bonus = bonus;
    }

    static JewelryEffect parse(JsonObject o) {
        return new TrophyExperienceEffect(Json.num(o, "bonus", 0.3F, 0F, 10F));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.EXPERIENCE_DROP);
    }

    @Override
    public void onExperienceDrop(JewelryContext ctx, LivingExperienceDropEvent event) {
        int xp = event.getDroppedExperience();
        if (xp <= 0) return;
        float scaled = xp * (1F + bonus * ctx.power());
        int whole = (int) scaled;
        if (ctx.player().getRandom().nextFloat() < scaled - whole) whole++;
        event.setDroppedExperience(whole);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("trophy_experience", "desc"), out.percent(bonus * out.power()));
    }
}
