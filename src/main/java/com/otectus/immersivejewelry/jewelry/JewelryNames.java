package com.otectus.immersivejewelry.jewelry;

import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.Json;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Display names of definition ids, with readable fallbacks when a definition is missing. */
public final class JewelryNames {

    /** A material may supply a whole naming template, so languages can inflect it around any jewelry form.
     * Unknown or pack-defined names retain the generic translated template. Resolution stays on the client. */
    public static Component named(String fallbackKey, Component material, Component form) {
        Component fallback = Component.translatable(fallbackKey, material);
        if (material.getContents() instanceof TranslatableContents name && material.getSiblings().isEmpty()) {
            return Component.translatableWithFallback("jewelry_name." + name.getKey(), "%2$s", form, fallback);
        }
        return fallback;
    }

    public static Component gem(JewelryDefinitions defs, @Nullable ResourceLocation id) {
        if (id == null) return Component.translatable("gem.immersive_jewelry.unknown");
        GemDefinition gem = defs.gem(id);
        return gem != null ? gem.name() : Component.translatableWithFallback("gem." + id.getNamespace() + "." + id.getPath(), Json.titleCase(id));
    }

    public static Component cut(JewelryDefinitions defs, @Nullable ResourceLocation id) {
        if (id == null) return Component.translatable("gem_cut.immersive_jewelry.uncut");
        CutDefinition cut = defs.cut(id);
        return cut != null ? cut.name() : Component.literal(Json.titleCase(id));
    }

    public static Component finish(JewelryDefinitions defs, @Nullable ResourceLocation id) {
        if (id == null) return Component.translatable("finish.immersive_jewelry.none");
        FinishDefinition finish = defs.finish(id);
        return finish != null ? finish.name() : Component.literal(Json.titleCase(id));
    }

    public static Component metal(JewelryDefinitions defs, MetalRef ref) {
        return defs.metal(ref).name();
    }

    /** A form's name: its finished item's name ("Ring"), or a readable fallback. */
    public static Component formName(JewelryDefinitions defs, ResourceLocation form) {
        var def = defs.form(form);
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(def != null ? def.item() : form);
        if (item != null && item != net.minecraft.world.item.Items.AIR) return item.getDescription();
        return Component.literal(Json.titleCase(form));
    }

    public static int gemColor(JewelryDefinitions defs, @Nullable ResourceLocation id) {
        GemDefinition gem = id == null ? null : defs.gem(id);
        return gem != null ? gem.color() : 0xB8C4CC;
    }

    public static int metalColor(JewelryDefinitions defs, MetalRef ref) {
        return defs.metal(ref).color();
    }

    private JewelryNames() {}
}
