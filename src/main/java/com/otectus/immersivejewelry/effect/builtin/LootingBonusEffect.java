package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraftforge.event.entity.living.LootingLevelEvent;

import java.util.Set;

/** Fortune (topaz ring, "Fortune's Favor"): one Looting level for a good piece, two for a superb one. */
final class LootingBonusEffect extends JewelryEffect {
    private final float first;
    private final float second;

    private LootingBonusEffect(float first, float second) {
        this.first = first;
        this.second = second;
    }

    static JewelryEffect parse(JsonObject o) {
        float first = Json.num(o, "first_level_power", 0.55F, 0F, 10F);
        return new LootingBonusEffect(first, Json.num(o, "second_level_power", 1.35F, first, 10F));
    }

    private int levels(float power) {
        return power >= second ? 2 : power >= first ? 1 : 0;
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.LOOTING);
    }

    @Override
    public void onLooting(JewelryContext ctx, LootingLevelEvent event) {
        event.setLootingLevel(event.getLootingLevel() + levels(ctx.power()));
    }

    @Override
    public void describe(EffectDescription out) {
        int levels = levels(out.power());
        if (levels > 0) out.line(Params.key("looting_bonus", "desc"), out.number(levels));
        else out.line(Params.key("looting_bonus", "weak"));
    }
}
