package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Knowledge as protection (lapis amulet, "Lesson Learned"): once a kind of creature or hazard has hurt the wearer,
 * further harm from the same kind is reduced for a while. Remembers the three most recent lessons.
 */
final class AdaptiveWardEffect extends JewelryEffect {
    private static final int MEMORY = 3;

    private final float reduction;
    private final int duration;

    private AdaptiveWardEffect(float reduction, int duration) {
        this.reduction = reduction;
        this.duration = duration;
    }

    static JewelryEffect parse(JsonObject o) {
        return new AdaptiveWardEffect(Json.num(o, "reduction", 0.15F, 0F, 1F), Json.integer(o, "duration", 600, 20, 72000));
    }

    private float share(float power) {
        return Math.min(0.5F, reduction * power);
    }

    static String lesson(DamageSource source) {
        if (source.getEntity() instanceof LivingEntity attacker) return "entity:" + BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType());
        return "damage:" + source.getMsgId();
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.INCOMING_DAMAGE);
    }

    @Override
    public void onIncomingDamage(JewelryContext ctx, LivingHurtEvent event) {
        Map<String, Long> lessons = ctx.state(LinkedHashMap::new);
        String key = lesson(event.getSource());
        long now = ctx.gameTime();
        Long until = lessons.remove(key);
        if (until != null && now < until) event.setAmount(event.getAmount() * (1F - share(ctx.power())));
        lessons.put(key, now + ctx.duration(duration));
        while (lessons.size() > MEMORY) lessons.remove(lessons.keySet().iterator().next());
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("adaptive_ward", "desc"), out.percent(share(out.power())), out.seconds(out.duration(duration)));
    }
}
