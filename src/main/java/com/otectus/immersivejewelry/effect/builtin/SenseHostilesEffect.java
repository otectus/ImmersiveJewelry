package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Perception (amethyst circlet "Scrying"): when a hostile creature sets its sights on the wearer, the wearer hears a
 * soft chime and the creature is outlined for a few seconds. Each creature is announced at most once per cooldown.
 */
final class SenseHostilesEffect extends JewelryEffect {
    private final int glow;
    private final int cooldown;

    private SenseHostilesEffect(int glow, int cooldown) {
        this.glow = glow;
        this.cooldown = cooldown;
    }

    static JewelryEffect parse(JsonObject o) {
        return new SenseHostilesEffect(Json.integer(o, "glow", 80, 0, 1200), Json.integer(o, "cooldown", 300, 20, 12000));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.TARGETED);
    }

    @Override
    public void onTargeted(JewelryContext ctx, LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(mob instanceof Enemy)) return;
        Map<UUID, Long> seen = ctx.state(HashMap::new);
        long now = ctx.gameTime();
        Long until = seen.get(mob.getUUID());
        if (until != null && now < until) return;
        if (seen.size() > 32) seen.values().removeIf(t -> t < now);
        seen.put(mob.getUUID(), now + cooldown);
        ctx.player().playNotifySound(ModSounds.EFFECT_SENSE.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
        int ticks = Math.round(glow * Math.min(2F, ctx.power()) * ctx.stability());
        if (ticks > 0) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("sense_hostiles", "desc"), out.seconds(Math.round(glow * Math.min(2F, out.power()) * out.stability())));
    }
}
