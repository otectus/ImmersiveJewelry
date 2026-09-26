package com.otectus.immersivejewelry.data.finish;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectCategory;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A finish (spec 28): final tuning chosen at the bench. Its look: the frame's raised edges are the metal mixed toward
 * white by {@code highlight_mix} (or {@code highlight_color}), its recesses the metal darkened by {@code shade} (or
 * {@code recess_color}, the patina of an antique finish).
 */
public record FinishDefinition(ResourceLocation id, float gemPotency, float integrity, float enchantability, float stability,
                               Map<EffectCategory, Float> categories, String visualProfile, int highlightColor, int recessColor,
                               float highlightMix, float shade, @Nullable Ingredient cost, int costCount, int sortOrder,
                               Component name, Component description) {

    public static FinishDefinition parse(ResourceLocation id, JsonObject o) {
        Ingredient cost = null;
        int count = 0;
        if (o.has("cost")) {
            JsonObject c = GsonHelper.getAsJsonObject(o, "cost");
            cost = Ingredient.fromJson(c.get("ingredient"), false);
            count = Json.integer(c, "count", 1, 1, 64);
        }
        String key = id.getNamespace() + "." + id.getPath().replace('/', '.');
        return new FinishDefinition(id,
                Json.num(o, "gem_potency_multiplier", 1F, 0F, 5F),
                Json.num(o, "integrity_multiplier", 1F, 0.05F, 5F),
                Json.num(o, "enchantability_multiplier", 1F, 0F, 5F),
                Json.num(o, "stability_multiplier", 1F, 0.1F, 5F),
                Map.copyOf(Json.categories(o, "category_multipliers")),
                Json.string(o, "visual_profile", "polished"),
                o.has("highlight_color") ? Json.color(o, "highlight_color", -1) : -1,
                o.has("recess_color") ? Json.color(o, "recess_color", -1) : -1,
                Json.num(o, "highlight_mix", 0.5F, 0F, 1F),
                Json.num(o, "shade", 0.4F, 0F, 1F),
                cost, count,
                Json.integer(o, "sort_order", 100, -1000, 1000),
                Json.name(o, "name", "finish." + key, Json.titleCase(id)),
                Json.name(o, "description", "finish." + key + ".description", ""));
    }

    public float category(EffectCategory category) {
        return categories.getOrDefault(category, 1F);
    }
}
