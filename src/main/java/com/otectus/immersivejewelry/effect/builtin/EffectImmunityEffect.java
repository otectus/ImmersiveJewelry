package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;
import java.util.Set;

/** Purity and clarity (diamond and sapphire circlets): the listed status effects cannot take hold of the wearer. */
final class EffectImmunityEffect extends JewelryEffect {
    private final List<MobEffect> effects;

    private EffectImmunityEffect(List<MobEffect> effects) {
        this.effects = effects;
    }

    static JewelryEffect parse(JsonObject o) {
        return new EffectImmunityEffect(Params.effects(o, "effects"));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.EFFECT_APPLICABLE);
    }

    @Override
    public void onEquip(JewelryContext ctx) {
        for (MobEffect e : effects) ctx.player().removeEffect(e);
    }

    @Override
    public void onEffectApplicable(JewelryContext ctx, MobEffectEvent.Applicable event) {
        if (effects.contains(event.getEffectInstance().getEffect())) event.setResult(Event.Result.DENY);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("effect_immunity", "desc"), Params.effectList(effects));
    }
}
