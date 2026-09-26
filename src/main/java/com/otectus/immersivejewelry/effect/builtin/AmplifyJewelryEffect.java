package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;

import java.util.Set;

/** Amplification (diamond circlet, "Prism Crown"): the wearer's other jewelry is stronger. Capped at 50%. */
final class AmplifyJewelryEffect extends JewelryEffect {
    private final float bonus;

    private AmplifyJewelryEffect(float bonus) {
        this.bonus = bonus;
    }

    static JewelryEffect parse(JsonObject o) {
        return new AmplifyJewelryEffect(Json.num(o, "bonus", 0.1F, 0F, 1F));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of();
    }

    @Override
    public float amplifyOthers(float power) {
        return Math.min(0.5F, bonus * power);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("amplify_jewelry", "desc"), out.percent(amplifyOthers(out.power())));
    }
}
