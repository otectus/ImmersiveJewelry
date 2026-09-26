package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Parameter readers shared by the built-in effect types. */
final class Params {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    /** A status effect granted by an effect: base duration in ticks, scaled by stability when applied. */
    record Grant(MobEffect effect, int amplifier, int duration) {}

    static MobEffect effect(String id, String field) {
        ResourceLocation rl = Json.parseId(id.contains(":") ? id : "minecraft:" + id, field);
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(rl);
        if (effect == null) throw new JsonParseException("Unknown status effect " + rl + " in '" + field + "'");
        return effect;
    }

    static List<Grant> grants(JsonObject o, String key) {
        List<Grant> out = new ArrayList<>();
        if (!o.has(key)) return out;
        for (JsonElement e : GsonHelper.getAsJsonArray(o, key)) {
            JsonObject g = GsonHelper.convertToJsonObject(e, key);
            out.add(new Grant(effect(GsonHelper.getAsString(g, "effect"), key), Json.integer(g, "amplifier", 0, 0, 4),
                    Json.integer(g, "duration", 100, 1, 12000)));
        }
        return List.copyOf(out);
    }

    static List<MobEffect> effects(JsonObject o, String key) {
        List<MobEffect> out = new ArrayList<>();
        if (!o.has(key)) return out;
        for (JsonElement e : GsonHelper.getAsJsonArray(o, key)) out.add(effect(GsonHelper.convertToString(e, key), key));
        return List.copyOf(out);
    }

    static void grant(JewelryContext ctx, LivingEntity target, List<Grant> grants) {
        for (Grant g : grants) target.addEffect(new MobEffectInstance(g.effect(), ctx.duration(g.duration()), g.amplifier(), false, true, true));
    }

    static Component effectName(MobEffect effect, int amplifier) {
        MutableComponent name = effect.getDisplayName().copy();
        if (amplifier > 0) name.append(" " + ROMAN[Math.min(amplifier, ROMAN.length - 1)]);
        return name;
    }

    /** "Speed II (4s), Regeneration (2.5s)". */
    static Component grantList(EffectDescription out, List<Grant> grants) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < grants.size(); i++) {
            Grant g = grants.get(i);
            if (i > 0) c.append(", ");
            c.append(effectName(g.effect(), g.amplifier())).append(" (").append(out.seconds(out.duration(g.duration()))).append(")");
        }
        return c;
    }

    static Component effectList(List<MobEffect> effects) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) c.append(", ");
            c.append(effects.get(i).getDisplayName());
        }
        return c;
    }

    static String key(String effect, String line) {
        return "effect.immersive_jewelry." + effect + "." + line;
    }

    private Params() {}
}
