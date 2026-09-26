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

/** Recovery (emerald amulet, "Evergreen"): out of combat, the wearer heals a little at a steady pulse. */
final class RegenerationPulseEffect extends JewelryEffect {
    private final float heal;
    private final int period;
    private final int calm;

    private static final class State {
        long lastHurt = Long.MIN_VALUE / 2;
        long lastPulse;
    }

    private RegenerationPulseEffect(float heal, int period, int calm) {
        this.heal = heal;
        this.period = period;
        this.calm = calm;
    }

    static JewelryEffect parse(JsonObject o) {
        return new RegenerationPulseEffect(Json.num(o, "heal", 1F, 0.1F, 20F), Json.integer(o, "period", 80, 10, 2400),
                Json.integer(o, "out_of_combat", 100, 0, 2400));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.TICK, JewelryHook.INCOMING_DAMAGE);
    }

    @Override
    public int tickInterval() {
        return 10;
    }

    @Override
    public void onIncomingDamage(JewelryContext ctx, LivingHurtEvent event) {
        ctx.state(State::new).lastHurt = ctx.gameTime();
    }

    @Override
    public void tick(JewelryContext ctx) {
        State s = ctx.state(State::new);
        ServerPlayer player = ctx.player();
        long now = ctx.gameTime();
        if (player.getHealth() >= player.getMaxHealth() || player.getFoodData().getFoodLevel() <= 0) return;
        if (now - s.lastHurt < calm || now - s.lastPulse < ctx.cooldown(period)) return;
        s.lastPulse = now;
        player.heal(heal * ctx.power());
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("regeneration_pulse", "desc"), out.hearts(heal * out.power()), out.seconds(out.cooldown(period)));
    }
}
