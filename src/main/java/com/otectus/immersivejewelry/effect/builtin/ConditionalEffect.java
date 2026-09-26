package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Set;

/**
 * A status effect held while a situation lasts (quartz circlet night sight in the dark, sapphire amulet breath under
 * water, topaz bracelet speed after a sprint). Refreshed quietly while the condition holds; lapses soon after it ends.
 * {@code strong_power}: at or above this power the amplifier rises by one.
 */
final class ConditionalEffect extends JewelryEffect {
    private static final int INTERVAL = 5;

    private final Condition condition;
    private final List<Params.Grant> effects;
    private final float strongPower;

    private static final class State {
        int sprintTicks;
    }

    private ConditionalEffect(Condition condition, List<Params.Grant> effects, float strongPower) {
        this.condition = condition;
        this.effects = effects;
        this.strongPower = strongPower;
    }

    static JewelryEffect parse(JsonObject o) {
        return new ConditionalEffect(Condition.parse(o, "condition"), Params.grants(o, "effects"), Json.num(o, "strong_power", 99F, 0F, 99F));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.TICK);
    }

    @Override
    public int tickInterval() {
        return INTERVAL;
    }

    @Override
    public void tick(JewelryContext ctx) {
        ServerPlayer player = ctx.player();
        State s = ctx.state(State::new);
        s.sprintTicks = player.isSprinting() ? s.sprintTicks + INTERVAL : 0;
        if (!condition.test(player, s.sprintTicks)) return;
        int bonus = ctx.power() >= strongPower ? 1 : 0;
        for (Params.Grant g : effects) {
            // Night vision flickers under ten seconds; everything else is refreshed a little ahead of expiry.
            int duration = g.effect() == MobEffects.NIGHT_VISION ? 260 : Math.max(40, g.duration());
            MobEffectInstance current = player.getEffect(g.effect());
            int amplifier = g.amplifier() + bonus;
            if (current != null && current.getAmplifier() > amplifier) continue;
            if (current == null || current.getDuration() < duration - 20 || current.getAmplifier() < amplifier) {
                player.addEffect(new MobEffectInstance(g.effect(), duration, amplifier, true, false, true));
            }
        }
    }

    @Override
    public void describe(EffectDescription out) {
        int bonus = out.power() >= strongPower ? 1 : 0;
        net.minecraft.network.chat.MutableComponent list = net.minecraft.network.chat.Component.empty();
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) list.append(", ");
            list.append(Params.effectName(effects.get(i).effect(), effects.get(i).amplifier() + bonus));
        }
        out.line(Params.key("conditional", "desc"), condition.describe(), list);
    }
}
