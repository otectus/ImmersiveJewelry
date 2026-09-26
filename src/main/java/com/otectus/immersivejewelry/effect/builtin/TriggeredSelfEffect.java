package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.List;
import java.util.Set;

/**
 * Status effects for the wearer when something happens: {@code on_hurt} (onyx bracelet "Shadowstep": slip away after
 * a solid hit) or {@code on_kill} (topaz circlet "Adrenal Spark"). Optional minimum damage and a cooldown.
 */
final class TriggeredSelfEffect extends JewelryEffect {
    private final boolean onKill;
    private final List<Params.Grant> grants;
    private final float minDamage;
    private final int cooldown;

    private TriggeredSelfEffect(boolean onKill, List<Params.Grant> grants, float minDamage, int cooldown) {
        this.onKill = onKill;
        this.grants = grants;
        this.minDamage = minDamage;
        this.cooldown = cooldown;
    }

    static JewelryEffect onHurt(JsonObject o) {
        return new TriggeredSelfEffect(false, Params.grants(o, "effects"), Json.num(o, "min_damage", 4F, 0F, 1000F),
                Json.integer(o, "cooldown", 900, 0, 72000));
    }

    static JewelryEffect onKill(JsonObject o) {
        return new TriggeredSelfEffect(true, Params.grants(o, "effects"), 0F, Json.integer(o, "cooldown", 0, 0, 72000));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(onKill ? JewelryHook.KILL : JewelryHook.DAMAGE_TAKEN);
    }

    @Override
    public void onDamageTaken(JewelryContext ctx, LivingDamageEvent event) {
        if (event.getAmount() >= minDamage) trigger(ctx);
    }

    @Override
    public void onKill(JewelryContext ctx, LivingDeathEvent event) {
        trigger(ctx);
    }

    private void trigger(JewelryContext ctx) {
        if (cooldown > 0 && !ctx.ready("trigger")) return;
        Params.grant(ctx, ctx.player(), grants);
        if (cooldown > 0) {
            ctx.startCooldown("trigger", cooldown);
            ctx.announce();
        }
    }

    @Override
    public void describe(EffectDescription out) {
        if (onKill) out.line(Params.key("on_kill", "desc"), Params.grantList(out, grants));
        else out.line(Params.key("on_hurt", "desc"), out.hearts(minDamage), Params.grantList(out, grants));
        if (cooldown > 0) out.line(Params.key("common", "cooldown"), out.seconds(out.cooldown(cooldown)));
    }
}
