package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.function.UnaryOperator;

/**
 * One cut as this server defines it, for comparison with the others: faces or regions and their pattern, the share of the
 * stone it keeps, its strength and steadiness factors, how demanding it is, and which gems take it. A recut starts from the
 * stone as it is now: it keeps the cut's share of the current size, never the original one. Page JSON:
 * {@code "cut": "immersive_jewelry:brilliant"}.
 */
public class CutCard extends GuideCard {
    String cut;

    @Override
    protected void resolve(UnaryOperator<IVariable> lookup) {
        cut = variable(lookup, cut);
    }

    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        CutDefinition def = defs.cut(id(cut));
        if (def == null) {
            line(g, context, Component.translatable("book.immersive_jewelry.card.cut_missing", String.valueOf(cut)), y);
            return;
        }
        int top = header(g, context, def.name(), y);
        top = fact(g, context, "faces", Component.translatable("book.immersive_jewelry.card.pattern." + def.pattern().name().toLowerCase(java.util.Locale.ROOT),
                def.facets()), top);
        top = fact(g, context, "keeps", percent(def.sizeRetention()), top);
        top = fact(g, context, "strength", times(def.qualityModifier()), top);
        top = fact(g, context, "steadiness", times(def.stability()), top);
        top = fact(g, context, "demand", times(def.difficulty()), top);
        MutableComponent gems = Component.empty();
        int n = 0;
        for (GemDefinition gem : defs.gems()) {
            if (gem.auto() || !gem.supports(def.id())) continue;
            if (n++ > 0) gems.append(Component.literal(", "));
            gems.append(gem.name());
        }
        top = fact(g, context, "taken_by", n == 0 ? Component.translatable("book.immersive_jewelry.card.none") : gems, top);
        if (!def.description().getString().isEmpty()) top = text(g, context, def.description().copy().withStyle(ChatFormatting.ITALIC), x, top + 2, PAGE_WIDTH, MUTED);
        provenance(g, context, false, top + 2);
    }
}
