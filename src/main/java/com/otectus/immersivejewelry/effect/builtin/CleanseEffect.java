package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;
import java.util.Set;

/** Warding (amethyst amulet, "Warding Geode"): every so often, the longest harmful effect on the wearer is lifted. */
final class CleanseEffect extends JewelryEffect {
    private final int cooldown;
    private final List<MobEffect> ignored;

    private CleanseEffect(int cooldown, List<MobEffect> ignored) {
        this.cooldown = cooldown;
        this.ignored = ignored;
    }

    static JewelryEffect parse(JsonObject o) {
        return new CleanseEffect(Json.integer(o, "cooldown", 600, 20, 72000), Params.effects(o, "ignore"));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.TICK);
    }

    @Override
    public void tick(JewelryContext ctx) {
        if (!ctx.ready("cleanse")) return;
        ServerPlayer player = ctx.player();
        MobEffectInstance worst = null;
        for (MobEffectInstance e : player.getActiveEffects()) {
            if (e.getEffect().getCategory() != MobEffectCategory.HARMFUL || ignored.contains(e.getEffect())) continue;
            if (worst == null || e.getDuration() > worst.getDuration()) worst = e;
        }
        if (worst == null) return;
        player.removeEffect(worst.getEffect());
        ctx.startCooldown("cleanse", cooldown);
        ctx.announce();
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("cleanse", "desc"), out.seconds(out.cooldown(cooldown)));
    }
}
