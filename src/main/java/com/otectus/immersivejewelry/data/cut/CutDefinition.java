package com.otectus.immersivejewelry.data.cut;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A gem cut (spec 27). {@code quality_modifier} is how strongly the cut expresses the gem's trait, {@code stability}
 * scales effect durations, {@code visual_profile} picks the gem shape the models draw.
 */
public record CutDefinition(ResourceLocation id, float difficulty, int facets, CutPattern pattern, float qualityModifier,
                            float sizeRetention, float stability, String visualProfile, int sortOrder, Component name,
                            Component adjective, Component description, @Nullable ResourceLocation workProfile) {

    public static CutDefinition parse(ResourceLocation id, JsonObject o) {
        String key = id.getNamespace() + "." + id.getPath().replace('/', '.');
        return new CutDefinition(id,
                Json.num(o, "difficulty", 0.8F, 0.05F, 3F),
                Json.integer(o, "facets", o.has("min_facets") ? Json.integer(o, "min_facets", 6, 2, 16) : 6, 2, 16),
                CutPattern.byId(Json.string(o, "pattern", "radial")),
                Json.num(o, "quality_modifier", 1F, 0.1F, 3F),
                Json.num(o, "size_retention", 0.75F, 0.1F, 1F),
                Json.num(o, "stability", 1F, 0.1F, 3F),
                Json.string(o, "visual_profile", "round"),
                Json.integer(o, "sort_order", 100, -1000, 1000),
                Json.name(o, "name", "gem_cut." + key, Json.titleCase(id)),
                Json.name(o, "adjective", "gem_cut." + key + ".adjective", Json.titleCase(id)),
                Json.name(o, "description", "gem_cut." + key + ".description", ""),
                Json.optionalId(o, "work_profile"));
    }

    /** Item-model index of the gem shape: 1 round, 2 brilliant, 3 emerald, 4 cabochon (unknown profiles draw round). */
    public int modelIndex() {
        return modelIndex(visualProfile);
    }

    public static int modelIndex(String profile) {
        return switch (profile) {
            case "brilliant" -> 2;
            case "emerald", "step" -> 3;
            case "cabochon", "dome" -> 4;
            default -> 1;
        };
    }
}
