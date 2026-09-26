package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

import java.util.List;
import java.util.Set;

/**
 * Last-moment protection (ruby amulet "Last Ember", topaz amulet "Quicksilver Flight"): when a hit leaves the wearer
 * below the health threshold, grant status effects and absorption, optionally douse flames; then wait out the
 * cooldown (shortened by responsive metals).
 */
final class EmergencyWardEffect extends JewelryEffect {
    private final float threshold;
    private final List<Params.Grant> grants;
    private final float absorption;
    private final boolean extinguish;
    private final int cooldown;

    private EmergencyWardEffect(float threshold, List<Params.Grant> grants, float absorption, boolean extinguish, int cooldown) {
        this.threshold = threshold;
        this.grants = grants;
        this.absorption = absorption;
        this.extinguish = extinguish;
        this.cooldown = cooldown;
    }

    static JewelryEffect parse(JsonObject o) {
        return new EmergencyWardEffect(Json.num(o, "health_threshold", 0.35F, 0.05F, 0.95F), Params.grants(o, "effects"),
                Json.num(o, "absorption", 0F, 0F, 40F), Json.bool(o, "extinguish", false), Json.integer(o, "cooldown", 2400, 20, 72000));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.DAMAGE_TAKEN);
    }

    @Override
    public void onDamageTaken(JewelryContext ctx, LivingDamageEvent event) {
        ServerPlayer player = ctx.player();
        float after = player.getHealth() - event.getAmount();
        if (after <= 0F || after > player.getMaxHealth() * threshold || !ctx.ready("ward")) return;
        Params.grant(ctx, player, grants);
        if (absorption > 0F) player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), absorption * ctx.power()));
        if (extinguish) player.clearFire();
        ctx.startCooldown("ward", cooldown);
        player.level().playSound(null, player.blockPosition(), ModSounds.EFFECT_WARD.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        ctx.announce();
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("emergency_ward", "desc"), out.percent(threshold), Params.grantList(out, grants));
        if (absorption > 0F) out.line(Params.key("emergency_ward", "absorption"), out.hearts(absorption * out.power()));
        out.line(Params.key("common", "cooldown"), out.seconds(out.cooldown(cooldown)));
    }
}
