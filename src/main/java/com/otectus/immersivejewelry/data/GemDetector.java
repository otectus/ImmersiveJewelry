package com.otectus.immersivejewelry.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.config.CommonConfig;
import com.otectus.immersivejewelry.util.Inventories;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Automatic gem detection (spec 45). Every {@code forge:gems/<name>} tag whose items no definition covers becomes the
 * generic gem {@code forge:<name>}: moderate hardness, every cut, and the neutral resonance trait. A pack author
 * overrides it with a file at {@code data/forge/immersive_jewelry/gems/<name>.json}.
 */
public final class GemDetector {

    public static void addAutomaticGems(Map<ResourceLocation, JsonObject> gems) {
        if (!CommonConfig.get(CommonConfig.AUTO_DETECT_GEM_TAGS)) return;
        Set<String> exclusions = new HashSet<>(CommonConfig.list(CommonConfig.AUTO_GEM_EXCLUSIONS));
        Set<Item> covered = new HashSet<>();
        for (JsonObject def : gems.values()) {
            if (!def.has("ingredient")) continue;
            try {
                for (var stack : Inventories.items(Ingredient.fromJson(def.get("ingredient"), false))) covered.add(stack.getItem());
            } catch (RuntimeException ignored) {
                // Reported when the definition itself is parsed.
            }
        }
        List<TagKey<Item>> tags = BuiltInRegistries.ITEM.getTagNames()
                .filter(t -> t.location().getNamespace().equals("forge"))
                .filter(t -> t.location().getPath().startsWith("gems/"))
                .filter(t -> t.location().getPath().indexOf('/', 5) < 0)
                .sorted(Comparator.comparing(t -> t.location().toString()))
                .toList();
        int added = 0;
        for (TagKey<Item> tag : tags) {
            String name = tag.location().getPath().substring("gems/".length());
            ResourceLocation id = new ResourceLocation("forge", name);
            if (name.isEmpty() || exclusions.contains(name) || gems.containsKey(id)) continue;
            List<Item> items = BuiltInRegistries.ITEM.getTag(tag).map(set -> set.stream().map(Holder::value).toList()).orElse(List.of());
            if (items.isEmpty() || items.stream().allMatch(covered::contains)) continue;
            gems.put(id, generic(tag));
            added++;
        }
        if (added > 0 && CommonConfig.get(CommonConfig.DEBUG_LOGGING)) {
            ImmersiveJewelry.LOGGER.info("Detected {} gem tag(s) without a definition; they use the generic gem profile", added);
        }
    }

    private static JsonObject generic(TagKey<Item> tag) {
        JsonObject o = new JsonObject();
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("tag", tag.location().toString());
        o.add("ingredient", ingredient);
        o.addProperty("hardness", 6.5F);
        o.addProperty("brittleness", 0.4F);
        o.addProperty("default_trait", ImmersiveJewelry.id("resonance").toString());
        o.addProperty("base_color", "#B8C4CC");
        JsonObject cutting = new JsonObject();
        cutting.addProperty("difficulty", 0.7F);
        cutting.addProperty("size_retention", 0.9F);
        o.add("cutting", cutting);
        o.add("supported_cuts", new JsonArray());
        o.addProperty("rarity", "uncommon");
        o.addProperty("auto", true);
        return o;
    }

    private GemDetector() {}
}
