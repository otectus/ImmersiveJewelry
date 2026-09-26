package com.otectus.immersivejewelry.data.gem;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A gemstone kind (spec 26). {@code ingredient} lists the raw items that are rough stones of this gem; an empty or
 * absent ingredient means the gem only exists as appraised rough stones (loot, trades).
 */
public record GemDefinition(ResourceLocation id, @Nullable Ingredient ingredient, float hardness, float brittleness,
                            ResourceLocation trait, int color, int sparkleColor, float cuttingDifficulty, float sizeRetention,
                            List<ResourceLocation> supportedCuts, List<ResourceLocation> favoredCuts, boolean opaque,
                            Appraisal appraisal, GemRarity rarity, Component name, boolean auto) {
    public static final ResourceLocation GENERIC_TRAIT = ImmersiveJewelry.id("resonance");

    public static GemDefinition parse(ResourceLocation id, JsonObject o) {
        int color = Json.color(o, "base_color", 0xB0B8C0);
        JsonObject cutting = o.has("cutting") ? GsonHelper.getAsJsonObject(o, "cutting") : new JsonObject();
        return new GemDefinition(id, Json.ingredient(o, "ingredient"),
                Json.num(o, "hardness", 7F, 1F, 10F),
                Json.num(o, "brittleness", 0.4F, 0F, 1F),
                o.has("default_trait") ? Json.id(o, "default_trait") : GENERIC_TRAIT,
                color,
                Json.color(o, "sparkle_color", lighten(color)),
                Json.num(cutting, "difficulty", 0.7F, 0.05F, 3F),
                Json.num(cutting, "size_retention", 0.9F, 0.1F, 1F),
                List.copyOf(Json.ids(o, "supported_cuts")),
                List.copyOf(Json.ids(o, "favored_cuts")),
                Json.bool(o, "opaque", false),
                Appraisal.parse(o),
                GemRarity.byId(Json.string(o, "rarity", "common")),
                Json.name(o, "name", "gem." + id.getNamespace() + "." + id.getPath().replace('/', '.'), Json.titleCase(id)),
                Json.bool(o, "auto", false));
    }

    /** Neutral stand-in for an unknown gem id: generic trait, every cut. */
    public static GemDefinition generic(ResourceLocation id) {
        return new GemDefinition(id, null, 6.5F, 0.4F, GENERIC_TRAIT, 0xB8C4CC, lighten(0xB8C4CC), 0.7F, 0.9F, List.of(), List.of(),
                false, Appraisal.DEFAULT, GemRarity.UNCOMMON,
                Component.translatableWithFallback("gem." + id.getNamespace() + "." + id.getPath().replace('/', '.'), Json.titleCase(id)), true);
    }

    public boolean supports(ResourceLocation cut) {
        return supportedCuts.isEmpty() || supportedCuts.contains(cut);
    }

    public boolean favors(ResourceLocation cut) {
        return favoredCuts.contains(cut);
    }

    /** The gem only exists as appraised rough stones. */
    public boolean hasSources() {
        return ingredient != null && !ingredient.isEmpty();
    }

    private static int lighten(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 255) + 90);
        int g = Math.min(255, ((rgb >> 8) & 255) + 90);
        int b = Math.min(255, (rgb & 255) + 90);
        return (r << 16) | (g << 8) | b;
    }
}
