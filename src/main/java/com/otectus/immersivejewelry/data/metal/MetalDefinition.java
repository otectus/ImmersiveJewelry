package com.otectus.immersivejewelry.data.metal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectCategory;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.data.ExpressionDefinition;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a metal family does in jewelry (spec 18). The id is an Immersive Smithing material family id. Metals bias a
 * piece rather than define it: overall potency, per-category affinity, frame integrity, enchantability,
 * responsiveness (cooldowns), and optionally a small effect of their own (silver against the undead). Gold frames
 * worn as curios keep piglins calm ({@code piglin_neutral}).
 */
public record MetalDefinition(ResourceLocation id, Component name, int color, float potency, float integrity, float enchantability,
                              float responsiveness, Map<EffectCategory, Float> affinities, List<ExpressionDefinition> bonus,
                              @Nullable Ingredient repair, boolean piglinNeutral, boolean generic) {

    public static MetalDefinition parse(ResourceLocation id, JsonObject o) {
        List<ExpressionDefinition> bonus = new ArrayList<>();
        if (o.has("bonus")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(o, "bonus")) {
                bonus.add(ExpressionDefinition.parse(GsonHelper.convertToJsonObject(e, "bonus"), EffectCategory.DEFENSE));
            }
        }
        return new MetalDefinition(id,
                Json.name(o, "name", "metal." + id.getNamespace() + "." + id.getPath().replace('/', '.'), Json.titleCase(id)),
                Json.color(o, "color", 0xC8CCD0),
                Json.num(o, "potency", 1F, 0F, 5F),
                Json.num(o, "integrity", 1F, 0.05F, 5F),
                Json.num(o, "enchantability", 1F, 0F, 5F),
                Json.num(o, "responsiveness", 1F, 0.1F, 5F),
                Map.copyOf(Json.categories(o, "affinities")),
                List.copyOf(bonus),
                Json.ingredient(o, "repair"),
                Json.bool(o, "piglin_neutral", false),
                false);
    }

    /** The neutral profile for a family without a definition, named and tinted from what the piece recorded. */
    public static MetalDefinition generic(MetalRef ref) {
        Component name = ref.storedName();
        if (name == null) {
            name = Component.translatableWithFallback("material." + ref.id().getNamespace() + "." + ref.id().getPath(), Json.titleCase(ref.id()));
        }
        return new MetalDefinition(ref.id(), name, ref.color() >= 0 ? ref.color() : 0xB8BCC0, 1F, 1F, 1F, 1F, Map.of(), List.of(), null, false, true);
    }

    public float affinity(EffectCategory category) {
        return affinities.getOrDefault(category, 1F);
    }
}
