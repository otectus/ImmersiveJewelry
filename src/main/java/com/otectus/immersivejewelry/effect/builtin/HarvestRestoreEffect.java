package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Set;

/** Endurance from the land (emerald bracelet, "Forager's Band"): harvesting ripe crops feeds the wearer a little. */
final class HarvestRestoreEffect extends JewelryEffect {
    private final int food;
    private final float saturation;
    private final int cooldown;

    private HarvestRestoreEffect(int food, float saturation, int cooldown) {
        this.food = food;
        this.saturation = saturation;
        this.cooldown = cooldown;
    }

    static JewelryEffect parse(JsonObject o) {
        return new HarvestRestoreEffect(Json.integer(o, "food", 1, 0, 20), Json.num(o, "saturation", 0.3F, 0F, 5F),
                Json.integer(o, "cooldown", 20, 0, 2400));
    }

    static boolean ripe(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        if (state.getBlock() instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= 3;
        if (state.getBlock() instanceof SweetBerryBushBlock) return state.getValue(SweetBerryBushBlock.AGE) >= 3;
        return false;
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.BLOCK_BREAK);
    }

    @Override
    public void onBlockBreak(JewelryContext ctx, BlockEvent.BreakEvent event) {
        if (!ripe(event.getState()) || !ctx.ready("harvest")) return;
        float power = ctx.power();
        ctx.player().getFoodData().eat(Math.max(food > 0 ? 1 : 0, Math.round(food * power)), saturation * power);
        ctx.startCooldown("harvest", cooldown);
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("harvest_restore", "desc"), out.number(Math.max(1, Math.round(food * out.power())) / 2F));
    }
}
