package com.otectus.immersivejewelry.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.effect.EffectCategory;
import com.otectus.immersivejewelry.api.effect.EffectUnavailableException;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryEffectType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * One behaviour a trait (or a metal) gives a form: an effect type, its category, an optional ability name and the
 * parsed effect. {@code {"type": "immersive_jewelry:momentum", "category": "offense", "name": "...", ...params}}. An optional
 * {@code fallback} expression replaces it when its type reports that it cannot exist in this pack.
 */
public record ExpressionDefinition(ResourceLocation type, EffectCategory category, @Nullable Component name, JewelryEffect effect) {

    public static ExpressionDefinition parse(JsonObject json, EffectCategory defaultCategory) {
        try {
            return parseOnly(json, defaultCategory);
        } catch (EffectUnavailableException e) {
            if (!json.has("fallback")) throw e;
            return parse(GsonHelper.getAsJsonObject(json, "fallback"), defaultCategory);
        }
    }

    private static ExpressionDefinition parseOnly(JsonObject json, EffectCategory defaultCategory) {
        ResourceLocation type = Json.id(json, "type");
        JewelryEffectType effectType = ImmersiveJewelryAPI.effectType(type);
        if (effectType == null) throw new JsonParseException("Unknown jewelry effect type " + type);
        EffectCategory category = defaultCategory;
        if (json.has("category")) {
            category = EffectCategory.byId(GsonHelper.getAsString(json, "category"));
            if (category == null) throw new JsonParseException("Unknown effect category " + json.get("category"));
        }
        Component name = json.has("name") ? Json.name(json, "name", GsonHelper.getAsString(json, "name", ""), "") : null;
        JewelryEffect effect = effectType.parse(json);
        return new ExpressionDefinition(type, category, name, effect);
    }
}
