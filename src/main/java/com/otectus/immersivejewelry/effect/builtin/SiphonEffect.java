package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Set;

/** Absorption (onyx ring, "Umbral Siphon"): melee hits heal the wearer for a share of the damage dealt. */
final class SiphonEffect extends JewelryEffect {
    private final float share;
    private final float maxHeal;
    private final int cooldown;

    private SiphonEffect(float share, float maxHeal, int cooldown) {
        this.share = share;
        this.maxHeal = maxHeal;
        this.cooldown = cooldown;
    }

    static JewelryEffect parse(JsonObject o) {
        return new SiphonEffect(Json.num(o, "share", 0.08F, 0F, 1F), Json.num(o, "max_heal", 3F, 0F, 40F), Json.integer(o, "cooldown", 10, 0, 1200));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.OUTGOING_DAMAGE);
    }

    @Override
    public void onOutgoingDamage(JewelryContext ctx, LivingHurtEvent event) {
        if (event.getSource().getDirectEntity() != ctx.player() || !ctx.ready("siphon")) return;
        float heal = Math.min(maxHeal, event.getAmount() * share * ctx.power());
        if (heal <= 0F) return;
        ctx.player().heal(heal);
        if (cooldown > 0) ctx.startCooldown("siphon", cooldown);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("siphon", "desc"), out.percent(share * out.power()), out.hearts(maxHeal));
    }
}
