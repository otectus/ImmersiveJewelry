package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.Set;

/** Concealment (onyx circlet, "Shade Circlet"): creatures notice the wearer only from closer. Capped at 65%. */
final class StealthEffect extends JewelryEffect {
    private final float reduction;

    private StealthEffect(float reduction) {
        this.reduction = reduction;
    }

    static JewelryEffect parse(JsonObject o) {
        return new StealthEffect(Json.num(o, "reduction", 0.25F, 0F, 1F));
    }

    private float share(float power) {
        return Math.min(0.65F, reduction * power);
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.VISIBILITY);
    }

    @Override
    public void onVisibility(JewelryContext ctx, LivingEvent.LivingVisibilityEvent event) {
        if (event.getLookingEntity() instanceof Mob) event.modifyVisibility(1F - share(ctx.power()));
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("stealth", "desc"), out.percent(share(out.power())));
    }
}
