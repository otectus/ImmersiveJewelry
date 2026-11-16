package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.trait.TraitDefinition;
import com.otectus.immersivejewelry.util.Inventories;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A gem as this server defines it: name and colour, its trait and theme, hardness and brittleness, opacity, the cuts it
 * takes (favoured ones marked) and the items that are this stone rough. Natural quality, size and inclusions are rolled
 * once when a stone is first mounted on a wheel; the card shows the ranges as words and never rolls. Page JSON:
 * {@code "gem": "immersive_jewelry:ruby"}; {@code "gem": "generic"} describes auto-detected modded gems.
 */
public class GemCard extends GuideCard {
    String gem;

    @Override
    protected void resolve(UnaryOperator<IVariable> lookup) {
        gem = variable(lookup, gem);
    }

    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        if ("generic".equals(gem)) {
            drawGeneric(g, context, defs);
            return;
        }
        ResourceLocation id = id(gem);
        GemDefinition def = defs.gem(id);
        if (def == null) {
            line(g, context, Component.translatable("book.immersive_jewelry.card.gem_missing", String.valueOf(gem)), y);
            return;
        }
        swatch(g, x, y, def.color());
        int top = text(g, context, def.name().copy().withStyle(net.minecraft.ChatFormatting.BOLD), x + 12, y, PAGE_WIDTH - 12, context.getHeaderColor()) + 2;
        TraitDefinition trait = defs.traitFor(def);
        if (trait != null) {
            MutableComponent value = trait.name().copy();
            if (!trait.theme().getString().isEmpty()) value.append(Component.literal(": ")).append(trait.theme());
            top = fact(g, context, "trait", value, top);
        }
        top = fact(g, context, "hardness", Component.literal(String.format(java.util.Locale.ROOT, "%.1f / 10", def.hardness())), top);
        top = fact(g, context, "brittleness", scale(def.brittleness()), top);
        top = fact(g, context, "body", Component.translatable(def.opaque() ? "book.immersive_jewelry.card.opaque" : "book.immersive_jewelry.card.transparent"), top);
        top = fact(g, context, "cuts", cuts(defs, def), top);
        top = fact(g, context, "clarity", scale(1F - def.appraisal().inclusionsMean()), top);
        List<ItemStack> sources = def.ingredient() == null ? List.of() : Inventories.items(def.ingredient());
        if (!sources.isEmpty()) {
            top = fact(g, context, "sources", Component.empty(), top);
            int shown = Math.min(6, sources.size());
            for (int i = 0; i < shown; i++) context.renderItemStack(g, x + i * 18, top, mouseX, mouseY, sources.get(i));
            top += 18;
        } else {
            top = fact(g, context, "sources", Component.translatable("book.immersive_jewelry.card.sources_none"), top);
        }
        provenance(g, context, false, top + 2);
    }

    static Component cuts(JewelryDefinitions defs, GemDefinition gem) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (CutDefinition cut : defs.cutsFor(gem)) {
            if (!first) out.append(Component.literal(", "));
            out.append(cut.name());
            if (gem.favors(cut.id())) out.append(Component.literal(" ★"));
            first = false;
        }
        return first ? Component.translatable("book.immersive_jewelry.card.none") : out;
    }

    private void drawGeneric(GuiGraphics g, IComponentRenderContext context, JewelryDefinitions defs) {
        int top = header(g, context, Component.translatable("book.immersive_jewelry.card.generic_gems"), y);
        int auto = 0;
        for (GemDefinition def : defs.gems()) if (def.auto()) auto++;
        top = fact(g, context, "detected", Component.literal(String.valueOf(auto)), top);
        int shown = 0;
        for (GemDefinition def : defs.gems()) {
            if (!def.auto() || shown >= 8) continue;
            swatch(g, x, top, def.color());
            top = text(g, context, def.name(), x + 12, top, PAGE_WIDTH - 12, context.getTextColor());
            shown++;
        }
        if (auto == 0) top = line(g, context, Component.translatable("book.immersive_jewelry.card.generic_none"), top);
        provenance(g, context, false, top + 2);
    }
}
