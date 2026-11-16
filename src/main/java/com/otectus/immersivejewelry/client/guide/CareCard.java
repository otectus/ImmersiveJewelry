package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.util.Inventories;
import com.otectus.immersivejewelry.workstation.BenchPlanner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import vazkii.patchouli.api.IComponentRenderContext;

import java.util.List;

/**
 * What the bench's rework page can do for the piece in the reader's hand, by the same rules the bench applies: repair
 * (only below the repair cap, which falls with every rework, and only with the frame's metal), retighten (when stones sit
 * loose, crooked or a prong is bent), take apart (costs frame integrity; every stone comes back as it is), refinish (a
 * different finish), and which stones need the wheel (a chipped or fractured stone is recut, smaller). Advisory only: the
 * bench checks again when the work starts.
 */
public class CareCard extends GuideCard {
    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        ItemStack stack = PieceCard.held();
        int top = header(g, context, Component.translatable("book.immersive_jewelry.card.care"), y);
        if (stack == null) {
            line(g, context, Component.translatable("book.immersive_jewelry.card.inspect_hold"), top);
            return;
        }
        JewelryData data = JewelryData.get(stack).orElseThrow();
        JewelryDefinitions defs = JewelryDefinitions.client();
        context.renderItemStack(g, x, top, mouseX, mouseY, stack);
        top = text(g, context, stack.getHoverName(), x + 20, top + 4, PAGE_WIDTH - 20, context.getTextColor()) + 6;
        float cap = BenchPlanner.repairCap(data.reworks());
        Ingredient repair = BenchPlanner.repairIngredient(defs, data.metal());
        boolean needsRepair = data.integrity() < cap - 0.001F;
        top = row(g, context, "repair", needsRepair && repair != null, needsRepair
                ? (repair == null ? Component.translatable("book.immersive_jewelry.card.care.no_material")
                : Component.translatable("book.immersive_jewelry.card.care.repair_to", percent(cap), repairName(repair)))
                : Component.translatable("book.immersive_jewelry.card.care.repair_full", percent(cap)), top);
        boolean loose = !data.defects().isEmpty();
        top = row(g, context, "retighten", data.hasGem(), Component.translatable(loose ? "book.immersive_jewelry.card.care.defects"
                : "book.immersive_jewelry.card.care.secure"), top);
        top = row(g, context, "remove", data.hasGem(), Component.translatable("book.immersive_jewelry.card.care.remove_cost",
                percent((float) ServerConfig.get(ServerConfig.REWORK_INTEGRITY_COST))), top);
        top = row(g, context, "refinish", true, Component.translatable("book.immersive_jewelry.card.care.refinish"), top);
        int damaged = 0;
        for (GemData gem : data.gems()) if (gem.damage() == GemDamage.CHIPPED || gem.damage() == GemDamage.FRACTURED) damaged++;
        top = row(g, context, "recut", damaged > 0, Component.translatable("book.immersive_jewelry.card.care.recut", damaged), top);
        top = fact(g, context, "reworks", Component.literal(String.valueOf(data.reworks())), top);
        text(g, context, Component.translatable("book.immersive_jewelry.card.care.advisory"), x, top + 2, PAGE_WIDTH, MUTED);
    }

    private int row(GuiGraphics g, IComponentRenderContext context, String kind, boolean possible, Component detail, int top) {
        Component mark = Component.literal(possible ? "✔ " : "✘ ").withStyle(possible ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY);
        Component head = Component.empty().append(mark).append(Component.translatable("book.immersive_jewelry.card.op." + kind)
                .withStyle(ChatFormatting.BOLD));
        top = line(g, context, head, top);
        return text(g, context, detail, x + 8, top, PAGE_WIDTH - 8, MUTED);
    }

    private static Component repairName(Ingredient ingredient) {
        List<ItemStack> items = Inventories.items(ingredient);
        return items.isEmpty() ? Component.literal("?") : items.get(0).getHoverName();
    }
}
