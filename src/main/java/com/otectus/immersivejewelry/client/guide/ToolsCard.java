package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.item.LapItem;
import com.otectus.immersivejewelry.item.PliersItem;
import com.otectus.immersivejewelry.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import vazkii.patchouli.api.IComponentRenderContext;

/**
 * The real tool tiers, read from the registered items (durability, precision, lap speed and steadiness), so the card always
 * matches the game: Jeweler's and Fine Pliers, Emery and Diamond Laps. Pliers wear once per setting (at the first closure),
 * a lap once per cut (at the first contact); pausing does not avoid or repeat the wear.
 */
public class ToolsCard extends GuideCard {
    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        int top = header(g, context, Component.translatable("book.immersive_jewelry.card.tools"), y);
        for (Item item : new Item[]{ModItems.JEWELERS_PLIERS.get(), ModItems.FINE_PLIERS.get(), ModItems.EMERY_LAP.get(), ModItems.DIAMOND_LAP.get()}) {
            ItemStack stack = new ItemStack(item);
            context.renderItemStack(g, x, top, mouseX, mouseY, stack);
            int row = text(g, context, stack.getHoverName(), x + 20, top, PAGE_WIDTH - 20, context.getTextColor());
            Component detail;
            if (item instanceof PliersItem pliers) {
                detail = Component.translatable("book.immersive_jewelry.card.pliers_stats", stack.getMaxDamage(), Math.round(pliers.precision() * 100));
            } else {
                LapItem.Tier tier = ((LapItem) item).tier();
                detail = Component.translatable("book.immersive_jewelry.card.lap_stats", stack.getMaxDamage(), Math.round(tier.speed * 100),
                        Math.round(tier.stability * 100));
            }
            row = text(g, context, detail, x + 20, row, PAGE_WIDTH - 20, MUTED);
            top = Math.max(top + 20, row + 3);
        }
        text(g, context, Component.translatable("book.immersive_jewelry.card.tools_wear"), x, top, PAGE_WIDTH, MUTED);
    }
}
