package com.otectus.immersivejewelry.data.trait;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivejewelry.api.effect.EffectCategory;
import com.otectus.immersivejewelry.data.ExpressionDefinition;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A gem's theme (spec 16-17): what it does, expressed differently by each form. {@code expressions} maps a form id
 * (a bare name means {@code immersive_jewelry:<name>}) to one expression or a list; {@code default} covers forms
 * without their own entry.
 */
public record TraitDefinition(ResourceLocation id, Component name, Component theme, int color, EffectCategory category,
                              Map<ResourceLocation, List<ExpressionDefinition>> expressions, List<ExpressionDefinition> fallback) {

    public static TraitDefinition parse(ResourceLocation id, JsonObject o) {
        String key = id.getNamespace() + "." + id.getPath().replace('/', '.');
        EffectCategory category = EffectCategory.byId(Json.string(o, "category", "utility"));
        if (category == null) throw new JsonParseException("Unknown category " + o.get("category"));
        Map<ResourceLocation, List<ExpressionDefinition>> expressions = new HashMap<>();
        if (o.has("expressions")) {
            for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(o, "expressions").entrySet()) {
                expressions.put(Json.parseId(e.getKey(), "expressions"), list(e.getValue(), category));
            }
        }
        List<ExpressionDefinition> fallback = o.has("default") ? list(o.get("default"), category) : List.of();
        return new TraitDefinition(id,
                Json.name(o, "name", "trait." + key, Json.titleCase(id)),
                Json.name(o, "theme", "trait." + key + ".theme", ""),
                Json.color(o, "color", 0xFFFFFF),
                category, Map.copyOf(expressions), fallback);
    }

    public List<ExpressionDefinition> forForm(ResourceLocation form) {
        return expressions.getOrDefault(form, fallback);
    }

    private static List<ExpressionDefinition> list(JsonElement element, EffectCategory category) {
        List<ExpressionDefinition> out = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) out.add(ExpressionDefinition.parse(GsonHelper.convertToJsonObject(e, "expression"), category));
        } else {
            out.add(ExpressionDefinition.parse(GsonHelper.convertToJsonObject(element, "expression"), category));
        }
        return List.copyOf(out);
    }
}
