package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.world.food.FoodData;

import java.util.Set;

/** Resource control (emerald circlet, "Verdant Mind"): in the right conditions, hunger is slowly restored. */
final class RestoreFoodEffect extends JewelryEffect {
    private final Condition condition;
    private final int period;
    private final int food;
    private final float saturation;

    private static final class State {
        long last;
    }

    private RestoreFoodEffect(Condition condition, int period, int food, float saturation) {
        this.condition = condition;
        this.period = period;
        this.food = food;
        this.saturation = saturation;
    }

    static JewelryEffect parse(JsonObject o) {
        return new RestoreFoodEffect(Condition.parse(o, "condition"), Json.integer(o, "period", 300, 20, 72000),
                Json.integer(o, "food", 1, 0, 20), Json.num(o, "saturation", 0.2F, 0F, 5F));
    }

    private int periodFor(float power) {
        return Math.max(20, Math.round(period / Math.max(0.25F, power)));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.TICK);
    }

    @Override
    public void tick(JewelryContext ctx) {
        FoodData data = ctx.player().getFoodData();
        State s = ctx.state(State::new);
        long now = ctx.gameTime();
        if (!data.needsFood() || now - s.last < periodFor(ctx.power()) || !condition.test(ctx.player(), 0)) return;
        s.last = now;
        data.eat(food, saturation);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("restore_food", "desc"), condition.describe(), out.number(food / 2F), out.seconds(periodFor(out.power())));
    }
}
