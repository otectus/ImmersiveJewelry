package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.util.Inventories;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * One finish as this server defines it: what it costs (or that it costs nothing: the burnisher needs no reagent), and how
 * it tunes a piece (stone strength, frame integrity, enchanting, steadiness). The material is used once, the first time
 * the finishing tool works the piece; pausing or packing the work neither charges it again nor gives it back. Page JSON:
 * {@code "finish": "immersive_jewelry:antique"}.
 */
public class FinishCard extends GuideCard {
    String finish;

    @Override
    protected void resolve(UnaryOperator<IVariable> lookup) {
        finish = variable(lookup, finish);
    }

    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        FinishDefinition def = JewelryDefinitions.client().finish(id(finish));
        if (def == null) {
            line(g, context, Component.translatable("book.immersive_jewelry.card.finish_missing", String.valueOf(finish)), y);
            return;
        }
        swatch(g, x, y, def.highlightColor());
        int top = text(g, context, def.name().copy().withStyle(ChatFormatting.BOLD), x + 12, y, PAGE_WIDTH - 12, context.getHeaderColor()) + 2;
        List<ItemStack> cost = def.cost() == null ? List.of() : Inventories.items(def.cost());
        if (cost.isEmpty()) {
            top = fact(g, context, "cost", Component.translatable("book.immersive_jewelry.card.cost_none"), top);
        } else {
            top = fact(g, context, "cost", Component.literal(def.costCount() + " ×"), top);
            for (int i = 0; i < Math.min(6, cost.size()); i++) context.renderItemStack(g, x + i * 18, top, mouseX, mouseY, cost.get(i));
            top += 18;
        }
        top = fact(g, context, "stone_strength", times(def.gemPotency()), top);
        top = fact(g, context, "frame", times(def.integrity()), top);
        top = fact(g, context, "enchanting", times(def.enchantability()), top);
        top = fact(g, context, "steadiness", times(def.stability()), top);
        if (!def.description().getString().isEmpty()) top = text(g, context, def.description().copy().withStyle(ChatFormatting.ITALIC), x, top + 2, PAGE_WIDTH, MUTED);
        provenance(g, context, false, top + 2);
    }
}
