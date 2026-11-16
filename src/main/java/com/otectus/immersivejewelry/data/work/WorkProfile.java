package com.otectus.immersivejewelry.data.work;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Optional, bounded tuning for one hands-on interaction ({@code data/<ns>/immersive_jewelry/work_profiles/<path>.json}).
 * It only changes how fast and how forgiving the work is; it can never change what an operation produces or make the
 * server accept what a client claims. Every field is validated: an unknown field, a wrong type or a number out of range
 * rejects the whole profile with its id and the field named, and anything that used it falls back to the defaults.
 *
 * <pre>{@code
 * {
 *   "schema_version": 1,
 *   "interaction": "prong",          // facet, dome, prong, bezel, polish, burnish, patina, repair
 *   "target_active_seconds": 14,     // work rate: seconds of steady work the whole operation takes (never a deadline)
 *   "tolerance_scale": 1.0,          // 0.25..4: scales depth windows (facet, dome) or the secure band (prong, bezel)
 *   "force_window": {"minimum": 0.28, "maximum": 0.72},  // facet, dome: comfortable quill force (0..1 of its travel)
 *   "sweep_tolerance": 1.6           // facet, dome: sideways handle speed (lap widths per second) before the stone rocks
 * }
 * }</pre>
 *
 * Designs ({@code "work_profile"} on a {@code jewelry_design} recipe), cuts and finishes ({@code "work_profile"} in
 * their definitions) may name one; without, {@code immersive_jewelry:defaults/<interaction>} applies if it exists, else
 * the built-in numbers (which the shipped default profiles repeat).
 */
public record WorkProfile(ResourceLocation id, Interaction interaction, float targetActiveSeconds, float toleranceScale,
                          float forceMinimum, float forceMaximum, float sweepTolerance) {
    public static final int SCHEMA = 1;
    private static final Set<String> FIELDS = Set.of("schema_version", "interaction", "target_active_seconds", "tolerance_scale",
            "force_window", "sweep_tolerance");

    public enum Interaction {
        FACET, DOME, PRONG, BEZEL, POLISH, BURNISH, PATINA, REPAIR;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable
        public static Interaction byId(String id) {
            for (Interaction i : values()) if (i.id().equals(id)) return i;
            return null;
        }

        public boolean grinds() {
            return this == FACET || this == DOME;
        }
    }

    public static ResourceLocation defaultId(Interaction interaction) {
        return new ResourceLocation("immersive_jewelry", "defaults/" + interaction.id());
    }

    public static WorkProfile parse(ResourceLocation id, JsonObject o) {
        for (String key : o.keySet()) {
            if (!FIELDS.contains(key) && !key.startsWith("$")) throw new IllegalArgumentException("unknown field \"" + key + "\"");
        }
        int schema = integer(o, "schema_version");
        if (schema != SCHEMA) throw new IllegalArgumentException("field \"schema_version\": unsupported version " + schema);
        if (!o.has("interaction") || !o.get("interaction").isJsonPrimitive()) {
            throw new IllegalArgumentException("field \"interaction\": required string");
        }
        Interaction interaction = Interaction.byId(o.get("interaction").getAsString());
        if (interaction == null) throw new IllegalArgumentException("field \"interaction\": unknown interaction \"" + o.get("interaction").getAsString() + "\"");
        float seconds = optional(o, "target_active_seconds", 0F, 1F, 600F);
        float tolerance = optional(o, "tolerance_scale", 1F, 0.25F, 4F);
        float min = 0.28F;
        float max = 0.72F;
        float sweep = 1.6F;
        if (o.has("force_window")) {
            if (!interaction.grinds()) throw new IllegalArgumentException("field \"force_window\": only for facet and dome profiles");
            if (!o.get("force_window").isJsonObject()) throw new IllegalArgumentException("field \"force_window\": expected an object");
            JsonObject w = o.getAsJsonObject("force_window");
            for (String key : w.keySet()) {
                if (!key.equals("minimum") && !key.equals("maximum")) throw new IllegalArgumentException("field \"force_window." + key + "\": unknown");
            }
            min = required(w, "minimum", "force_window.minimum", 0.05F, 0.9F);
            max = required(w, "maximum", "force_window.maximum", 0.1F, 0.95F);
            if (max - min < 0.1F) throw new IllegalArgumentException("field \"force_window\": maximum must exceed minimum by at least 0.1");
        }
        if (o.has("sweep_tolerance")) {
            if (!interaction.grinds()) throw new IllegalArgumentException("field \"sweep_tolerance\": only for facet and dome profiles");
            sweep = optional(o, "sweep_tolerance", 1.6F, 0.2F, 8F);
        }
        return new WorkProfile(id, interaction, seconds, tolerance, min, max, sweep);
    }

    private static int integer(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("field \"" + key + "\": required number");
        }
        return e.getAsInt();
    }

    private static float optional(JsonObject o, String key, float def, float min, float max) {
        if (!o.has(key)) return def;
        return required(o, key, key, min, max);
    }

    private static float required(JsonObject o, String key, String name, float min, float max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("field \"" + name + "\": required number");
        }
        double v = e.getAsDouble();
        if (!Double.isFinite(v) || v < min || v > max) {
            throw new IllegalArgumentException("field \"" + name + "\": " + v + " is outside " + min + ".." + max);
        }
        return (float) v;
    }
}
