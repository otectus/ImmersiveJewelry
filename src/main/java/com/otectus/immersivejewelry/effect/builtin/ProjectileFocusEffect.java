package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Set;

/** Arrows, bolts and thrown tridents shot by the wearer deal more damage (quartz ring, "Marksman's Loop"). */
final class ProjectileFocusEffect extends JewelryEffect {
    private final float bonus;

    private ProjectileFocusEffect(float bonus) {
        this.bonus = bonus;
    }

    static JewelryEffect parse(JsonObject o) {
        return new ProjectileFocusEffect(Json.num(o, "bonus", 0.15F, 0F, 5F));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.PROJECTILE_FIRED);
    }

    @Override
    public void onProjectileFired(JewelryContext ctx, Projectile projectile) {
        if (projectile instanceof AbstractArrow arrow) arrow.setBaseDamage(arrow.getBaseDamage() * (1F + bonus * ctx.power()));
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("projectile_focus", "desc"), out.percent(bonus * out.power()));
    }
}
