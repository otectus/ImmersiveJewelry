package com.otectus.immersivejewelry.jewelry;

import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.Json;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Display names of definition ids, with readable fallbacks when a definition is missing. */
public final class JewelryNames {

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

    public static int gemColor(JewelryDefinitions defs, @Nullable ResourceLocation id) {
        GemDefinition gem = id == null ? null : defs.gem(id);
        return gem != null ? gem.color() : 0xB8C4CC;
    }

    public static int metalColor(JewelryDefinitions defs, MetalRef ref) {
        return defs.metal(ref).color();
    }

    private JewelryNames() {}
}
