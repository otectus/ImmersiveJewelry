package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.api.effect.JewelryProfile;
import com.otectus.immersivejewelry.data.Json;
import net.minecraftforge.event.entity.living.LivingFallEvent;

import java.util.Set;
import java.util.function.BiConsumer;

/** Light feet: less fall damage, and optionally a higher jump (predicted by the client, so it goes through the profile). */
final class FallCushionEffect extends JewelryEffect {
    private final float reduction;
    private final float jump;

    private FallCushionEffect(float reduction, float jump) {
        this.reduction = reduction;
        this.jump = jump;
    }

    static JewelryEffect parse(JsonObject o) {
        return new FallCushionEffect(Json.num(o, "reduction", 0.3F, 0F, 1F), Json.num(o, "jump_boost", 0F, 0F, 0.3F));
    }

    private float share(float power) {
        return Math.min(0.8F, reduction * power);
    }

    @Override
    public Set<JewelryHook> hooks() {
        return reduction > 0F ? Set.of(JewelryHook.FALL) : Set.of();
    }

    @Override
    public void onFall(JewelryContext ctx, LivingFallEvent event) {
        event.setDamageMultiplier(event.getDamageMultiplier() * (1F - share(ctx.power())));
    }

    @Override
    public void contributeProfile(float power, float stability, BiConsumer<String, Float> out) {
        if (jump > 0F) out.accept(JewelryProfile.JUMP_BOOST, Math.min(0.3F, jump * power));
    }

    @Override
    public void describe(EffectDescription out) {
        if (reduction > 0F) out.line(Params.key("fall_cushion", "desc"), out.percent(share(out.power())));
        if (jump > 0F) out.line(Params.key("fall_cushion", "jump"));
    }
}
