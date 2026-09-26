package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import com.otectus.immersivejewelry.util.StationEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.BonemealEvent;

import java.util.Set;

/** Growth (emerald ring, "Verdant Touch"): bone meal the wearer uses works extra times. */
final class BonemealBoostEffect extends JewelryEffect {
    private final float extra;

    private BonemealBoostEffect(float extra) {
        this.extra = extra;
    }

    static JewelryEffect parse(JsonObject o) {
        return new BonemealBoostEffect(Json.num(o, "extra", 1F, 0F, 8F));
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.BONEMEAL);
    }

    @Override
    public void onBonemeal(JewelryContext ctx, BonemealEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        float amount = extra * ctx.power();
        int times = (int) amount;
        if (level.random.nextFloat() < amount - times) times++;
        BlockPos pos = event.getPos();
        boolean grew = false;
        for (int i = 0; i < times; i++) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BonemealableBlock block) || !block.isValidBonemealTarget(level, pos, state, false)) break;
            if (block.isBonemealSuccess(level, level.random, pos, state)) {
                block.performBonemeal(level, level.random, pos, state);
                grew = true;
            }
        }
        if (grew) StationEffects.sparkleAt(level, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 0x5CD67A, 6);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("bonemeal_boost", "desc"), out.number(extra * out.power()));
    }
}
