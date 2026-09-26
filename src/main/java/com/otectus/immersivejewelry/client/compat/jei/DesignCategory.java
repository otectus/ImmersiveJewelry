package com.otectus.immersivejewelry.client.compat.jei;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.client.screen.JewelryGui;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.metal.MetalDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.util.Inventories;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A jewelry design at the Jeweler's Bench: blank (any metal) + cut stones + finish material, set by hand. */
public class DesignCategory extends JeiCategory<JewelryDesignRecipe> {
    public static final RecipeType<JewelryDesignRecipe> TYPE = RecipeType.create(ImmersiveJewelry.MOD_ID, "jewelry_design", JewelryDesignRecipe.class);

    public DesignCategory(IGuiHelper helper) {
        super(helper, TYPE, new ItemStack(ModItems.JEWELERS_BENCH.get()), "jewelry_design", 150, 62);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, JewelryDesignRecipe design, IFocusGroup focuses) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        Item blank = JewelryFactory.blankItemFor(design.form());
        List<ItemStack> blanks = new ArrayList<>();
        List<ItemStack> results = new ArrayList<>();
        List<ItemStack> stones = new ArrayList<>();
        for (GemDefinition gem : defs.gems()) {
            if (gem.auto() || !design.allowsGem(gem.id())) continue;
            for (CutDefinition cut : defs.cutsFor(gem)) {
                if (design.allowsCut(cut.id())) stones.add(CutGemItem.create(JewelryFactory.sampleCut(gem, cut, 0.8F)));
            }
        }
        for (MetalDefinition metal : defs.metals()) {
            MetalRef ref = MetalRef.of(metal.id(), metal.name(), metal.color());
            if (blank != null) blanks.add(JewelryFactory.blank(blank, ref, 0.75F));
            GemData stone = stones.isEmpty() ? null : CutGemItem.data(stones.get(results.size() % stones.size())).orElse(null);
            if (stone != null) {
                List<GemData> set = new ArrayList<>();
                for (int i = 0; i < design.stones(); i++) set.add(stone);
                results.add(JewelryFactory.jewelry(design.form(), ref, set, design.getId(), ImmersiveJewelry.id("polished"), 0.75F, 0.8F, null));
            }
        }
        builder.addSlot(RecipeIngredientRole.INPUT, 1 + 6, 1 + 6).addItemStacks(blanks).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 21 + 6, 1 + 6).addItemStacks(stones).setBackground(slot, -1, -1)
                .addTooltipCallback((v, t) -> t.add(Component.translatable("jei.immersive_jewelry.stones_needed", design.stones()).withStyle(ChatFormatting.GRAY)));
        List<ItemStack> costs = new ArrayList<>();
        for (FinishDefinition f : defs.finishes()) {
            if (!design.allowsFinish(f.id())) continue;
            ItemStack cost = Inventories.display(f.cost(), f.costCount());
            if (!cost.isEmpty()) costs.add(cost);
        }
        if (!costs.isEmpty()) builder.addSlot(RecipeIngredientRole.INPUT, 41 + 6, 1 + 6).addItemStacks(costs).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.CATALYST, 1 + 6, 23 + 6).addItemStack(new ItemStack(ModItems.JEWELERS_PLIERS.get())).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 130 + 6, 1 + 6).addItemStacks(results.isEmpty() ? List.of(design.getResultItem(null)) : results)
                .setBackground(slot, -1, -1);
    }

    @Override
    public void draw(JewelryDesignRecipe design, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        g.pose().pushPose();
        g.pose().translate(6, 6, 0);
        arrow.draw(g, 90, 1);
        var font = Minecraft.getInstance().font;
        // Text stays clear of the bookmark button JEI puts at the bottom right.
        g.drawString(font, JewelryGui.clipped(font, design.name(), 120), 24, 24, JewelryGui.TEXT, false);
        int y = 34;
        for (var line : font.split(Component.translatable("jei.immersive_jewelry.design_setting", design.stones(),
                Component.translatable("setting_style.immersive_jewelry." + design.style().id()), design.pointsPerStone()), 112)) {
            if (y > 43) break;
            g.drawString(font, line, 24, y, JewelryGui.TEXT_DIM, false);
            y += 9;
        }
        if (design.sketch()) g.drawString(font, JewelryGui.clipped(font, Component.translatable("jei.immersive_jewelry.design_sketch"), 112), 24, 53, JewelryGui.TEXT_WARM, false);
        g.pose().popPose();
    }
}
