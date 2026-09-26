package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.VanillaGameEvent;

import java.util.Set;

/**
 * Silence (amethyst bracelet, "Hush Band"): the wearer's footsteps, landings and splashes make no vibrations, so sculk
 * sensors, shriekers and wardens do not hear them. With {@code sneaking_only} it works only while sneaking.
 */
final class MuffleVibrationsEffect extends JewelryEffect {
    private static final Set<GameEvent> MUFFLED = Set.of(GameEvent.STEP, GameEvent.HIT_GROUND, GameEvent.SWIM, GameEvent.SPLASH,
            GameEvent.FLAP, GameEvent.ELYTRA_GLIDE);

    private final boolean sneakingOnly;

    private MuffleVibrationsEffect(boolean sneakingOnly) {
        this.sneakingOnly = sneakingOnly;
    }

    static JewelryEffect parse(JsonObject o) {
        return new MuffleVibrationsEffect(Json.bool(o, "sneaking_only", false));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.GAME_EVENT);
    }

    @Override
    public void onGameEvent(JewelryContext ctx, VanillaGameEvent event) {
        if (MUFFLED.contains(event.getVanillaEvent()) && (!sneakingOnly || ctx.player().isShiftKeyDown())) event.setCanceled(true);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("muffle_vibrations", sneakingOnly ? "sneaking" : "desc"));
    }
}
