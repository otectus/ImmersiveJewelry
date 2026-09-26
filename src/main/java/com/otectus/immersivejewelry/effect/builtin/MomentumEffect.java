package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Set;
import java.util.UUID;

/**
 * A hit streak (ruby ring, "Kindling Strikes"): consecutive melee hits on the same foe within the window build
 * stacks that add damage; reaching the maximum unleashes a payoff strike (extra damage, optionally setting the foe
 * ablaze) and starts over.
 */
final class MomentumEffect extends JewelryEffect {
    private final int maxStacks;
    private final int window;
    private final float damagePerStack;
    private final float payoffDamage;
    private final int igniteSeconds;

    private static final class State {
        UUID target;
        int stacks;
        long lastHit;
    }

    private MomentumEffect(int maxStacks, int window, float damagePerStack, float payoffDamage, int igniteSeconds) {
        this.maxStacks = maxStacks;
        this.window = window;
        this.damagePerStack = damagePerStack;
        this.payoffDamage = payoffDamage;
        this.igniteSeconds = igniteSeconds;
    }

    static JewelryEffect parse(JsonObject o) {
        return new MomentumEffect(Json.integer(o, "max_stacks", 3, 2, 10), Json.integer(o, "window", 60, 5, 400),
                Json.num(o, "damage_per_stack", 0.5F, 0F, 20F), Json.num(o, "payoff_damage", 2F, 0F, 40F),
                Json.integer(o, "ignite_seconds", 3, 0, 30));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.OUTGOING_DAMAGE);
    }

    @Override
    public void onOutgoingDamage(JewelryContext ctx, LivingHurtEvent event) {
        if (event.getSource().getDirectEntity() != ctx.player()) return; // melee only
        LivingEntity target = event.getEntity();
        State s = ctx.state(State::new);
        long now = ctx.gameTime();
        if (target.getUUID().equals(s.target) && now - s.lastHit <= window) s.stacks++;
        else s.stacks = 1;
        s.target = target.getUUID();
        s.lastHit = now;
        float power = ctx.power();
        float bonus = damagePerStack * (s.stacks - 1) * power;
        if (s.stacks >= maxStacks) {
            bonus += payoffDamage * power;
            if (igniteSeconds > 0) target.setSecondsOnFire(Math.max(1, Math.round(igniteSeconds * ctx.stability())));
            s.stacks = 0;
            ctx.announce();
        }
        if (bonus > 0F) event.setAmount(event.getAmount() + bonus);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("momentum", "desc"), out.number(maxStacks), out.hearts(damagePerStack * out.power()));
        if (igniteSeconds > 0) {
            out.line(Params.key("momentum", "payoff_fire"), out.hearts(payoffDamage * out.power()), out.seconds(out.duration(igniteSeconds * 20)));
        } else {
            out.line(Params.key("momentum", "payoff"), out.hearts(payoffDamage * out.power()));
        }
    }
}
