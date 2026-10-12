package com.otectus.immersivejewelry.client;

import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.client.screen.JewelryGui;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.util.Inventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item tints (spec 36-38). Jewelry: layer 0 frame (metal), 1 recesses (finish shade or patina), 2 raised edges (finish
 * highlight), 3 gem body (gem colour), 4 sparkle (brighter for better cuts, dimmer for damaged stones, scaled by the
 * client's sparkle setting). Blanks: metal and highlight. Stones: body and sheen. Nothing per combination is authored.
 */
public final class JewelryColors {
    private static final int FALLBACK_METAL = 0xB8BCC0;
    private static final Map<ResourceLocation, Integer> SAMPLED = new HashMap<>();
    private static RenderColors rendering;

    /** One item draw can ask for the same tint hundreds of times. Decode its NBT once for that draw only.
     * No stack, definition, config or resource data survives between draws; nested renderers restore their caller. */
    public static RenderColors cacheForRender(ItemStack stack) {
        RenderColors colors = new RenderColors(stack, rendering);
        rendering = colors;
        return colors;
    }

    public static final class RenderColors implements AutoCloseable {
        private final ItemStack stack;
        private final RenderColors previous;
        private final int[] colors;

        private RenderColors(ItemStack stack, RenderColors previous) {
            this.stack = stack;
            this.previous = previous;
            if (stack.getItem() instanceof JewelryItem) {
                JewelryData data = JewelryData.get(stack).orElse(null);
                colors = new int[5];
                for (int i = 0; i < colors.length; i++) colors[i] = jewelry(data, i);
            } else if (stack.getItem() instanceof JewelryBlankItem) {
                MetalRef metal = BlankData.get(stack).map(BlankData::metal).orElse(null);
                int color = metal == null ? FALLBACK_METAL : metalColor(JewelryDefinitions.client(), metal);
                colors = new int[]{color, JewelryGui.mix(color, 0xFFFFFF, 0.45F)};
            } else if (stack.getItem() instanceof RoughGemItem || stack.getItem() instanceof CutGemItem) {
                GemData data = GemData.load(JewelryNbt.root(stack)).orElse(null);
                colors = new int[]{stone(data, 0), stone(data, 1)};
            } else colors = new int[0];
        }

        @Override
        public void close() { rendering = previous; }
    }

    private static boolean cached(ItemStack stack, int layer) {
        return rendering != null && rendering.stack == stack && layer >= 0 && layer < rendering.colors.length;
    }

    public static void register(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> jewelry(stack, layer), ModItems.RING.get(), ModItems.AMULET.get(), ModItems.BRACELET.get(), ModItems.CIRCLET.get());
        event.register((stack, layer) -> blank(stack, layer), ModItems.RING_BLANK.get(), ModItems.AMULET_FRAME.get(),
                ModItems.BRACELET_BLANK.get(), ModItems.CIRCLET_FRAME.get());
        event.register((stack, layer) -> stone(stack, layer), ModItems.ROUGH_GEM.get(), ModItems.CUT_GEM.get());
        // The wheel item shows its disc in the plain sandstone of a wheel without a lap.
        event.register((stack, layer) -> layer == 0 ? com.otectus.immersivejewelry.item.LapItem.Tier.SANDSTONE.color : 0xFFFFFF, ModItems.LAPIDARY_WHEEL.get());
    }

    public static int jewelry(ItemStack stack, int layer) {
        if (cached(stack, layer)) return rendering.colors[layer];
        return jewelry(JewelryData.get(stack).orElse(null), layer);
    }

    private static int jewelry(@Nullable JewelryData data, int layer) {
        if (data == null) return layer <= 2 ? FALLBACK_METAL : 0xFFFFFF;
        JewelryDefinitions defs = JewelryDefinitions.client();
        int metal = metalColor(defs, data.metal());
        FinishDefinition finish = defs.finish(data.finish());
        return switch (layer) {
            case 0 -> metal;
            case 1 -> recess(metal, finish);
            case 2 -> highlight(metal, finish);
            case 3 -> data.principal() == null ? 0xFFFFFF : gemBody(defs, data.principal());
            case 4 -> data.principal() == null ? 0xFFFFFF : sparkle(defs, data.principal(), data.cutQuality());
            default -> 0xFFFFFF;
        };
    }

    public static int blank(ItemStack stack, int layer) {
        if (cached(stack, layer)) return rendering.colors[layer];
        MetalRef metal = BlankData.get(stack).map(BlankData::metal).orElse(null);
        int color = metal == null ? FALLBACK_METAL : metalColor(JewelryDefinitions.client(), metal);
        return layer == 0 ? color : JewelryGui.mix(color, 0xFFFFFF, 0.45F);
    }

    public static int stone(ItemStack stack, int layer) {
        if (cached(stack, layer)) return rendering.colors[layer];
        return stone(GemData.load(JewelryNbt.root(stack)).orElse(null), layer);
    }

    private static int stone(@Nullable GemData data, int layer) {
        if (data == null) return 0xFFFFFF;
        JewelryDefinitions defs = JewelryDefinitions.client();
        return layer == 0 ? gemBody(defs, data) : sparkle(defs, data, data.isCut() ? data.cutQuality() : 0.25F);
    }

    public static int metalColor(JewelryDefinitions defs, MetalRef ref) {
        var def = defs.metalDefinition(ref.id());
        if (def != null) return def.color();
        return ref.color() >= 0 ? ref.color() : FALLBACK_METAL;
    }

    public static int recess(int metal, @Nullable FinishDefinition finish) {
        if (finish != null && finish.recessColor() >= 0) return JewelryGui.mix(metal, finish.recessColor(), 0.7F);
        float shade = finish != null ? finish.shade() : 0.4F;
        return JewelryGui.scale(0xFF000000 | metal, 1F - shade) & 0xFFFFFF;
    }

    public static int highlight(int metal, @Nullable FinishDefinition finish) {
        if (finish != null && finish.highlightColor() >= 0) return finish.highlightColor();
        return JewelryGui.mix(metal, 0xFFFFFF, finish != null ? finish.highlightMix() : 0.5F);
    }

    public static int gemBody(JewelryDefinitions defs, GemData data) {
        int color = gemColor(defs, data.gem());
        if (data.damage() == GemDamage.CHIPPED || data.damage() == GemDamage.FRACTURED) color = JewelryGui.mix(color, 0x9A9A9A, 0.25F);
        return color;
    }

    public static int sparkle(JewelryDefinitions defs, GemData data, float quality) {
        GemDefinition gem = defs.gem(data.gem());
        int body = gemColor(defs, data.gem());
        int bright = gem != null ? gem.sparkleColor() : JewelryGui.mix(body, 0xFFFFFF, 0.6F);
        float intensity = (float) ClientConfig.get(ClientConfig.GEM_SPARKLE_INTENSITY) * (0.25F + 0.75F * quality) * data.damage().potency();
        return JewelryGui.mix(body, JewelryGui.mix(bright, 0xFFFFFF, 0.35F * quality), intensity);
    }

    /** The gem's colour; for an auto-detected gem, the average colour of its source item's sprite (sampled once). */
    public static int gemColor(JewelryDefinitions defs, ResourceLocation id) {
        GemDefinition gem = defs.gem(id);
        if (gem == null) return 0xB8C4CC;
        if (!gem.auto()) return gem.color();
        return SAMPLED.computeIfAbsent(id, k -> sample(gem));
    }

    private static int sample(GemDefinition gem) {
        try {
            List<ItemStack> sources = Inventories.items(gem.ingredient());
            if (sources.isEmpty()) return gem.color();
            ItemStack source = sources.get(0);
            TextureAtlasSprite sprite = Minecraft.getInstance().getItemRenderer().getModel(source, null, null, 0).getParticleIcon();
            var image = sprite.contents();
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < image.height(); y++) {
                for (int x = 0; x < image.width(); x++) {
                    if (image.isTransparent(0, x, y)) continue;
                    int abgr = image.getOriginalImage().getPixelRGBA(x, y);
                    r += abgr & 255;
                    g += (abgr >> 8) & 255;
                    b += (abgr >> 16) & 255;
                    n++;
                }
            }
            if (n == 0) return gem.color();
            return (int) ((r / n) << 16 | (g / n) << 8 | (b / n));
        } catch (RuntimeException e) {
            return gem.color();
        }
    }

    public static void clearSamples() {
        SAMPLED.clear();
    }

    private JewelryColors() {}
}
