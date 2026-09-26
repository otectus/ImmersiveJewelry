package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Perception made offensive (amethyst ring, "Resonant Mark"): a melee hit marks the foe (it glows) and the wearer's
 * later hits on the marked foe, melee or ranged, deal more damage until the mark fades.
 */
final class MarkTargetEffect extends JewelryEffect {
    private final int duration;
    private final float bonus;

    private static final class State {
        UUID marked;
        long until;
    }

    private MarkTargetEffect(int duration, float bonus) {
        this.duration = duration;
        this.bonus = bonus;
    }

    static JewelryEffect parse(JsonObject o) {
        return new MarkTargetEffect(Json.integer(o, "duration", 100, 10, 1200), Json.num(o, "bonus", 0.15F, 0F, 5F));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.OUTGOING_DAMAGE);
    }

    @Override
    public void onOutgoingDamage(JewelryContext ctx, LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        State s = ctx.state(State::new);
        long now = ctx.gameTime();
        if (target.getUUID().equals(s.marked) && now < s.until) {
            event.setAmount(event.getAmount() * (1F + bonus * ctx.power()));
        } else if (event.getSource().getDirectEntity() == ctx.player()) {
            int ticks = ctx.duration(duration);
            s.marked = target.getUUID();
            s.until = now + ticks;
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
        }
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("mark_target", "desc"), out.seconds(out.duration(duration)), out.percent(bonus * out.power()));
    }
}
