package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.jewelry.JewelryResolver;
import com.otectus.immersivejewelry.jewelry.JewelryTooltips;
import com.otectus.immersivejewelry.jewelry.ResolvedJewelry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.IComponentRenderContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Inspects the real piece in the reader's hand (main hand, else off hand): its stones, maker, workmanship, integrity,
 * defects, reworks and Smithing history as its tooltip states them, then the effect it resolves to now (the same resolver
 * and descriptions the game uses, never ticking or equipping anything). The full text is the hover tooltip; the page shows
 * what fits. Without a piece in hand the card says how to use it.
 */
public class PieceCard extends GuideCard {
    @Nullable
    static ItemStack held() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof JewelryItem && JewelryData.get(stack).isPresent()) return stack;
        }
        return null;
    }

    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        ItemStack stack = held();
        int top = header(g, context, Component.translatable("book.immersive_jewelry.card.inspect"), y);
        if (stack == null) {
            line(g, context, Component.translatable("book.immersive_jewelry.card.inspect_hold"), top);
            return;
        }
        context.renderItemStack(g, x, top, mouseX, mouseY, stack);
        top = text(g, context, stack.getHoverName(), x + 20, top + 4, PAGE_WIDTH - 20, context.getTextColor()) + 6;
        JewelryDefinitions defs = JewelryDefinitions.client();
        JewelryData data = JewelryData.get(stack).orElseThrow();
        List<Component> lines = new ArrayList<>();
        JewelryTooltips.jewelry(data, defs, lines);
        ResolvedJewelry resolved = JewelryResolver.resolve(data, defs);
        if (lines.stream().noneMatch(l -> !l.getString().isEmpty() && resolved.trait() != null && l.getString().startsWith(resolved.trait().name().getString()))) {
            JewelryTooltips.effects(resolved, lines);
        }
        int bottom = y + PAGE_HEIGHT - 12;
        for (Component line : lines) {
            if (top > bottom - 9) {
                text(g, context, Component.translatable("book.immersive_jewelry.card.more"), x, bottom, PAGE_WIDTH, MUTED);
                break;
            }
            if (line.getString().isEmpty()) {
                top += 3;
                continue;
            }
            top = text(g, context, line, x, top, PAGE_WIDTH, context.getTextColor());
        }
        if (context.isAreaHovered(mouseX, mouseY, x, y, PAGE_WIDTH, PAGE_HEIGHT)) context.setHoverTooltipComponents(lines);
    }
}
