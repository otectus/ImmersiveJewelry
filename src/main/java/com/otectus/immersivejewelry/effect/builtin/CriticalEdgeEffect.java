package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.Set;

/** Critical hits strike harder (diamond ring, "Keen Edge"). */
final class CriticalEdgeEffect extends JewelryEffect {
    private final float bonus;

    private CriticalEdgeEffect(float bonus) {
        this.bonus = bonus;
    }

    static JewelryEffect parse(JsonObject o) {
        return new CriticalEdgeEffect(Json.num(o, "bonus", 0.25F, 0F, 5F));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.CRITICAL_HIT);
    }

    @Override
    public void onCriticalHit(JewelryContext ctx, CriticalHitEvent event) {
        boolean critical = event.getResult() == Event.Result.ALLOW || (event.getResult() == Event.Result.DEFAULT && event.isVanillaCritical());
        if (critical) event.setDamageModifier(event.getDamageModifier() + bonus * ctx.power());
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("critical_edge", "desc"), out.percent(bonus * out.power()));
    }
}
