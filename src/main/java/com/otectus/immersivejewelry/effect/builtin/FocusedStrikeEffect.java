package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import java.util.Set;

/**
 * Patience rewarded (sapphire ring, "Precise Focus"): a fully charged melee attack deals more damage. The charge is
 * read when the attack starts, because vanilla resets it before the damage is dealt.
 */
final class FocusedStrikeEffect extends JewelryEffect {
    private final float bonus;
    private final float minCharge;

    private static final class State {
        long chargedTick = -1;
    }

    private FocusedStrikeEffect(float bonus, float minCharge) {
        this.bonus = bonus;
        this.minCharge = minCharge;
    }

    static JewelryEffect parse(JsonObject o) {
        return new FocusedStrikeEffect(Json.num(o, "bonus", 0.12F, 0F, 5F), Json.num(o, "min_charge", 0.95F, 0.1F, 1F));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.MELEE_ATTACK, JewelryHook.OUTGOING_DAMAGE);
    }

    @Override
    public void onMeleeAttack(JewelryContext ctx, AttackEntityEvent event) {
        State s = ctx.state(State::new);
        s.chargedTick = ctx.player().getAttackStrengthScale(0.5F) >= minCharge ? ctx.gameTime() : -1;
    }

    @Override
    public void onOutgoingDamage(JewelryContext ctx, LivingHurtEvent event) {
        State s = ctx.state(State::new);
        if (event.getSource().getDirectEntity() == ctx.player() && s.chargedTick == ctx.gameTime()) {
            event.setAmount(event.getAmount() * (1F + bonus * ctx.power()));
            s.chargedTick = -1;
        }
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("focused_strike", "desc"), out.percent(bonus * out.power()));
    }
}
