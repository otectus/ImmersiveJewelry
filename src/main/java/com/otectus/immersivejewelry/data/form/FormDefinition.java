package com.otectus.immersivejewelry.data.form;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A jewelry form (spec 29): the equipment slot it is worn in, the finished item and its blank, how many stones it
 * takes, and how Immersive Smithing makes its blank. {@code expression} is the key traits use for this form.
 */
public record FormDefinition(ResourceLocation id, String slot, ResourceLocation item, ResourceLocation blank, int maxGems,
                             float baseIntegrity, int enchantability, int metalUnits, ResourceLocation anvilPattern,
                             int sortOrder, Component name) {

    public static FormDefinition parse(ResourceLocation id, JsonObject o) {
        String path = id.getPath();
        return new FormDefinition(id,
                Json.string(o, "slot", path),
                o.has("item") ? Json.id(o, "item") : id,
                o.has("smithing_form") ? Json.id(o, "smithing_form") : Json.id(o, "blank"),
                Json.integer(o, "max_gems", 1, 1, 8),
                Json.num(o, "base_integrity", 1F, 0.05F, 5F),
                Json.integer(o, "enchantability", 12, 0, 100),
                Json.integer(o, "metal_units", 9, 1, 81),
                o.has("anvil_pattern") ? Json.id(o, "anvil_pattern") : ImmersiveJewelry.id(path + "_blank"),
                Json.integer(o, "sort_order", 100, -1000, 1000),
                Json.name(o, "name", "jewelry_form." + id.getNamespace() + "." + path.replace('/', '.'), Json.titleCase(id)));
    }

    /** Defaults for a built-in form whose definition a datapack removed. */
    public static FormDefinition fallback(ResourceLocation id, ResourceLocation blank) {
        String path = id.getPath();
        String slot = switch (path) {
            case "amulet" -> "necklace";
            case "circlet" -> "head";
            default -> path;
        };
        return new FormDefinition(id, slot, id, blank, 1, 1F, 12, 9, ImmersiveJewelry.id(path + "_blank"), 100,
                Component.translatableWithFallback("jewelry_form." + id.getNamespace() + "." + path, Json.titleCase(id)));
    }
}
