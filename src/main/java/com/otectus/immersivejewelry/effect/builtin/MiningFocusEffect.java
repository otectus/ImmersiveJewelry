package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.api.effect.JewelryProfile;
import com.otectus.immersivejewelry.data.Json;

import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Practical hands (diamond bracelet "Diamond Grip", lapis bracelet underwater). Mining is predicted by the client, so
 * this adds to the wearer's profile instead of hooking an event. Modes: {@code correct_tool}, {@code any},
 * {@code underwater} (share of the underwater penalty removed).
 */
final class MiningFocusEffect extends JewelryEffect {
    private final String mode;
    private final float bonus;

    private MiningFocusEffect(String mode, float bonus) {
        this.mode = mode;
        this.bonus = bonus;
    }

    static JewelryEffect parse(JsonObject o) {
        String mode = Json.string(o, "mode", "correct_tool");
        if (!mode.equals("correct_tool") && !mode.equals("any") && !mode.equals("underwater")) {
            throw new com.google.gson.JsonParseException("Unknown mining mode " + mode);
        }
        return new MiningFocusEffect(mode, Json.num(o, "bonus", 0.15F, 0F, 5F));
    }

    private float amount(float power) {
        return mode.equals("underwater") ? Math.min(1F, bonus * power) : bonus * power;
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of();
    }

    @Override
    public void contributeProfile(float power, float stability, BiConsumer<String, Float> out) {
        String key = switch (mode) {
            case "any" -> JewelryProfile.MINING_SPEED;
            case "underwater" -> JewelryProfile.UNDERWATER_MINING;
            default -> JewelryProfile.MINING_SPEED_CORRECT_TOOL;
        };
        out.accept(key, amount(power));
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("mining_focus", mode), out.percent(amount(out.power())));
    }
}
