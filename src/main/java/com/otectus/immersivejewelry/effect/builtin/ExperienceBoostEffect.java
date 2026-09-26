package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraftforge.event.entity.player.PlayerXpEvent;

import java.util.Set;

/**
 * Knowledge (lapis circlet "Insight", ruby circlet "Vital Spark"): experience orbs are worth more, and optionally
 * each point picked up heals. Fractions carry over between orbs, so small orbs are not rounded away.
 */
final class ExperienceBoostEffect extends JewelryEffect {
    private final float bonus;
    private final float healPerPoint;

    private static final class State {
        float carry;
    }

    private ExperienceBoostEffect(float bonus, float healPerPoint) {
        this.bonus = bonus;
        this.healPerPoint = healPerPoint;
    }

    static JewelryEffect parse(JsonObject o) {
        return new ExperienceBoostEffect(Json.num(o, "bonus", 0.2F, 0F, 10F), Json.num(o, "heal_per_point", 0F, 0F, 5F));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.XP_PICKUP);
    }

    @Override
    public void onXpPickup(JewelryContext ctx, PlayerXpEvent.PickupXp event) {
        int value = event.getOrb().value;
        if (value <= 0) return;
        State s = ctx.state(State::new);
        if (bonus > 0F) {
            s.carry += value * bonus * ctx.power();
            int extra = (int) s.carry;
            if (extra > 0) {
                s.carry -= extra;
                event.getOrb().value = value + extra;
            }
        }
        if (healPerPoint > 0F) ctx.player().heal(value * healPerPoint * ctx.power());
    }

    @Override
    public void describe(EffectDescription out) {
        if (bonus > 0F) out.line(Params.key("experience_boost", "desc"), out.percent(bonus * out.power()));
        if (healPerPoint > 0F) out.line(Params.key("experience_boost", "heal"), out.hearts(healPerPoint * out.power() * 10F));
    }
}
