package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.metal.MetalDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Metal and blanks as this server has them. {@code "show": "routes"}: how blanks are made here, read from the recipes the
 * server actually sent (Immersive Smithing blank recipes present or not, fallback crafting recipes enabled or not: a missing
 * Smithing alone does not promise a fallback). {@code "show": "metals"}: every metal with a jewelry definition, tinted and
 * named, with its repair material; families without one still work with a neutral profile.
 */
public class MetalCard extends GuideCard {
    String show;

    @Override
    protected void resolve(UnaryOperator<IVariable> lookup) {
        show = variable(lookup, show);
    }

    /** Recipes of this mod whose path starts with {@code prefix}, as synced to the client. */
    static long recipes(String prefix) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 0;
        return level.getRecipeManager().getRecipeIds().filter(id -> id.getNamespace().equals("immersive_jewelry") && id.getPath().startsWith(prefix)).count();
    }

    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        if ("metals".equals(show)) {
            metals(g, context);
            return;
        }
        int top = header(g, context, Component.translatable("book.immersive_jewelry.card.routes"), y);
        boolean smithingLoaded = net.minecraftforge.fml.ModList.get().isLoaded("immersive_smithing");
        long smithing = recipes("smithing/");
        long fallback = recipes("fallback/");
        top = fact(g, context, "route_smithing", Component.translatable(!smithingLoaded ? "book.immersive_jewelry.card.route.absent"
                : smithing > 0 ? "book.immersive_jewelry.card.route.active" : "book.immersive_jewelry.card.route.off")
                .withStyle(smithing > 0 ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY), top);
        top = fact(g, context, "route_fallback", Component.translatable(fallback > 0 ? "book.immersive_jewelry.card.route.active"
                : "book.immersive_jewelry.card.route.off").withStyle(fallback > 0 ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY), top);
        if (smithing == 0 && fallback == 0) top = text(g, context, Component.translatable("book.immersive_jewelry.card.route.none")
                .withStyle(ChatFormatting.DARK_RED), x, top + 1, PAGE_WIDTH, context.getTextColor());
        else top = text(g, context, Component.translatable(fallback > 0 ? "book.immersive_jewelry.card.route.fallback_quality"
                : "book.immersive_jewelry.card.route.smithing_quality"), x, top + 1, PAGE_WIDTH, MUTED);
        provenance(g, context, false, top + 2);
    }

    private void metals(GuiGraphics g, IComponentRenderContext context) {
        int top = header(g, context, Component.translatable("book.immersive_jewelry.card.metals"), y);
        List<MetalDefinition> metals = JewelryDefinitions.client().metals().stream().filter(m -> !m.generic())
                .sorted(Comparator.comparing(m -> m.name().getString())).toList();
        int column = 0, rowTop = top;
        for (int i = 0; i < metals.size() && i < 26; i++) {
            MetalDefinition metal = metals.get(i);
            int left = x + column * 58;
            swatch(g, left, rowTop, metal.color());
            String name = font().plainSubstrByWidth(metal.name().getString(), 44);
            g.drawString(font(), Component.literal(name).withStyle(context.getFont()), left + 11, rowTop + 1, context.getTextColor(), false);
            column++;
            if (column == 2) {
                column = 0;
                rowTop += 10;
            }
        }
        top = rowTop + (column == 0 ? 0 : 10);
        top = text(g, context, Component.translatable("book.immersive_jewelry.card.metals_generic"), x, top + 2, PAGE_WIDTH, MUTED);
        provenance(g, context, false, top + 1);
    }
}
