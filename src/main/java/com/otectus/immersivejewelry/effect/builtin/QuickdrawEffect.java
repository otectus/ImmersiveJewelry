package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.api.effect.JewelryProfile;
import com.otectus.immersivejewelry.data.Json;

import java.util.Set;
import java.util.function.BiConsumer;

/** Steady hands (sapphire bracelet, "Quickdraw"): bows and crossbows draw faster. Capped at 50%. */
final class QuickdrawEffect extends JewelryEffect {
    private final float bonus;

    private QuickdrawEffect(float bonus) {
        this.bonus = bonus;
    }

    static JewelryEffect parse(JsonObject o) {
        return new QuickdrawEffect(Json.num(o, "bonus", 0.2F, 0F, 1F));
    }

    private float amount(float power) {
        return Math.min(0.5F, bonus * power);
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of();
    }

    @Override
    public void contributeProfile(float power, float stability, BiConsumer<String, Float> out) {
        out.accept(JewelryProfile.QUICKDRAW, amount(power));
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("quickdraw", "desc"), out.percent(amount(out.power())));
    }
}
