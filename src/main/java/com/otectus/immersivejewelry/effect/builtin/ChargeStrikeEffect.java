package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Set;

/**
 * A running start (ruby bracelet, "Fervor"): after sprinting long enough, the next melee hit within a short window
 * deals extra damage and knocks the foe back.
 */
final class ChargeStrikeEffect extends JewelryEffect {
    private static final int INTERVAL = 2;

    private final int sprintTicks;
    private final int window;
    private final float damage;
    private final float knockback;

    private static final class State {
        int sprinted;
        long chargedUntil = -1;
    }

    private ChargeStrikeEffect(int sprintTicks, int window, float damage, float knockback) {
        this.sprintTicks = sprintTicks;
        this.window = window;
        this.damage = damage;
        this.knockback = knockback;
    }

    static JewelryEffect parse(JsonObject o) {
        return new ChargeStrikeEffect(Json.integer(o, "sprint_ticks", 30, 2, 400), Json.integer(o, "window", 40, 2, 400),
                Json.num(o, "damage", 2F, 0F, 40F), Json.num(o, "knockback", 0.6F, 0F, 3F));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.TICK, JewelryHook.OUTGOING_DAMAGE);
    }

    @Override
    public int tickInterval() {
        return INTERVAL;
    }

    @Override
    public void tick(JewelryContext ctx) {
        State s = ctx.state(State::new);
        if (ctx.player().isSprinting()) {
            s.sprinted += INTERVAL;
            if (s.sprinted >= sprintTicks) s.chargedUntil = ctx.gameTime() + ctx.duration(window);
        } else {
            s.sprinted = 0;
        }
    }

    @Override
    public void onOutgoingDamage(JewelryContext ctx, LivingHurtEvent event) {
        ServerPlayer player = ctx.player();
        State s = ctx.state(State::new);
        if (event.getSource().getDirectEntity() != player || ctx.gameTime() > s.chargedUntil) return;
        s.chargedUntil = -1;
        s.sprinted = 0;
        event.setAmount(event.getAmount() + damage * ctx.power());
        if (knockback > 0F) {
            double dx = player.getX() - event.getEntity().getX();
            double dz = player.getZ() - event.getEntity().getZ();
            event.getEntity().knockback(knockback * Math.min(1.5F, ctx.power()), dx, dz);
        }
        ctx.announce();
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("charge_strike", "desc"), out.seconds(sprintTicks), out.hearts(damage * out.power()));
    }
}
