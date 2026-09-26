package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Set;

/**
 * Resilience (diamond amulet, "Adamant Ward"): a shell of absorption that re-forms after a while without being hurt.
 * It only ever raises absorption up to its own size, so it never stacks with a golden apple's.
 */
final class AbsorptionShellEffect extends JewelryEffect {
    private final float amount;
    private final int recharge;

    private static final class State {
        long lastHurt = Long.MIN_VALUE / 2;
    }

    private AbsorptionShellEffect(float amount, int recharge) {
        this.amount = amount;
        this.recharge = recharge;
    }

    static JewelryEffect parse(JsonObject o) {
        return new AbsorptionShellEffect(Json.num(o, "amount", 4F, 0.5F, 40F), Json.integer(o, "recharge", 200, 20, 12000));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.TICK, JewelryHook.INCOMING_DAMAGE);
    }

    @Override
    public void onIncomingDamage(JewelryContext ctx, LivingHurtEvent event) {
        ctx.state(State::new).lastHurt = ctx.gameTime();
    }

    @Override
    public void tick(JewelryContext ctx) {
        State s = ctx.state(State::new);
        ServerPlayer player = ctx.player();
        float shell = amount * ctx.power();
        if (ctx.gameTime() - s.lastHurt >= ctx.cooldown(recharge) && player.getAbsorptionAmount() < shell) {
            boolean empty = player.getAbsorptionAmount() <= 0F;
            player.setAbsorptionAmount(shell);
            if (empty) ctx.announce();
        }
    }

    @Override
    public void onUnequip(JewelryContext ctx) {
        ServerPlayer player = ctx.player();
        if (player.getAbsorptionAmount() <= amount * ctx.power() + 0.01F) player.setAbsorptionAmount(0F);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("absorption_shell", "desc"), out.hearts(amount * out.power()), out.seconds(out.cooldown(recharge)));
    }
}
