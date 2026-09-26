package com.otectus.immersivejewelry.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivejewelry.api.effect.EffectCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Strict JSON readers for definition files: every error names the field, every number is range-checked. */
public final class Json {

    public static float num(JsonObject o, String key, float def, float min, float max) {
        if (!o.has(key)) return def;
        float v = GsonHelper.getAsFloat(o, key);
        if (!Float.isFinite(v) || v < min || v > max) {
            throw new JsonParseException("'" + key + "' must be between " + min + " and " + max + ", was " + v);
        }
        return v;
    }

    public static int integer(JsonObject o, String key, int def, int min, int max) {
        if (!o.has(key)) return def;
        int v = GsonHelper.getAsInt(o, key);
        if (v < min || v > max) throw new JsonParseException("'" + key + "' must be between " + min + " and " + max + ", was " + v);
        return v;
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        return o.has(key) ? GsonHelper.getAsBoolean(o, key) : def;
    }

    public static String string(JsonObject o, String key, String def) {
        return o.has(key) ? GsonHelper.getAsString(o, key) : def;
    }

    /** "#RRGGBB", "RRGGBB" or an integer. */
    public static int color(JsonObject o, String key, int def) {
        if (!o.has(key)) return def;
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsInt() & 0xFFFFFF;
        String s = GsonHelper.convertToString(e, key).trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            throw new JsonParseException("'" + key + "' is not a colour: " + s);
        }
    }

    public static ResourceLocation id(JsonObject o, String key) {
        return parseId(GsonHelper.getAsString(o, key), key);
    }

    @Nullable
    public static ResourceLocation optionalId(JsonObject o, String key) {
        return o.has(key) ? id(o, key) : null;
    }

    /** An id; a bare path means the {@code immersive_jewelry} namespace. */
    public static ResourceLocation parseId(String s, String key) {
        ResourceLocation id = ResourceLocation.tryParse(s.contains(":") ? s : "immersive_jewelry:" + s);
        if (id == null) throw new JsonParseException("'" + key + "' is not a valid id: " + s);
        return id;
    }

    public static List<ResourceLocation> ids(JsonObject o, String key) {
        List<ResourceLocation> out = new ArrayList<>();
        if (!o.has(key)) return out;
        JsonArray array = GsonHelper.getAsJsonArray(o, key);
        for (JsonElement e : array) out.add(parseId(GsonHelper.convertToString(e, key), key));
        return out;
    }

    /** A translation key string or a text component object; falls back to {@code key} with {@code fallback} text. */
    public static Component name(JsonObject o, String field, String key, String fallback) {
        if (!o.has(field)) return Component.translatableWithFallback(key, fallback);
        JsonElement e = o.get(field);
        if (e.isJsonPrimitive()) return Component.translatableWithFallback(e.getAsString(), fallback);
        Component c = Component.Serializer.fromJson(e);
        return c != null ? c : Component.translatableWithFallback(key, fallback);
    }

    @Nullable
    public static Ingredient ingredient(JsonObject o, String key) {
        if (!o.has(key)) return null;
        return Ingredient.fromJson(o.get(key), false);
    }

    public static Map<EffectCategory, Float> categories(JsonObject o, String key) {
        Map<EffectCategory, Float> out = new EnumMap<>(EffectCategory.class);
        if (!o.has(key)) return out;
        JsonObject obj = GsonHelper.getAsJsonObject(o, key);
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            EffectCategory c = EffectCategory.byId(e.getKey());
            if (c == null) throw new JsonParseException("Unknown effect category '" + e.getKey() + "' in '" + key + "'");
            float v = GsonHelper.convertToFloat(e.getValue(), e.getKey());
            out.put(c, Mth.clamp(v, 0F, 10F));
        }
        return out;
    }

    /** Title-cased last path segment: {@code forge:rose_gold} becomes "Rose Gold". */
    public static String titleCase(ResourceLocation id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private Json() {}
}
