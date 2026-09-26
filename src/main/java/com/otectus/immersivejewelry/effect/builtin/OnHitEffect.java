package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.List;
import java.util.Set;

/** Status effects on the foe the wearer strikes in melee, at most once per cooldown. */
final class OnHitEffect extends JewelryEffect {
    private final List<Params.Grant> grants;
    private final int cooldown;

    private OnHitEffect(List<Params.Grant> grants, int cooldown) {
        this.grants = grants;
        this.cooldown = cooldown;
    }

    static JewelryEffect parse(JsonObject o) {
        return new OnHitEffect(Params.grants(o, "effects"), Json.integer(o, "cooldown", 60, 0, 72000));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.OUTGOING_DAMAGE);
    }

    @Override
    public void onOutgoingDamage(JewelryContext ctx, LivingHurtEvent event) {
        if (event.getSource().getDirectEntity() != ctx.player() || (cooldown > 0 && !ctx.ready("hit"))) return;
        Params.grant(ctx, event.getEntity(), grants);
        if (cooldown > 0) ctx.startCooldown("hit", cooldown);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("on_hit", "desc"), Params.grantList(out, grants));
        if (cooldown > 0) out.line(Params.key("common", "cooldown"), out.seconds(out.cooldown(cooldown)));
    }
}
