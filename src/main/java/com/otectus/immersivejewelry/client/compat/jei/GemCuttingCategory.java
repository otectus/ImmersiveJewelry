package com.otectus.immersivejewelry.client.compat.jei;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.client.screen.VanillaGui;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.DescriptionBuilder;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.util.Inventories;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Cutting one gem at the Lapidary Wheel: its raw sources and appraised stone in, one cut stone per supported cut out. */
public class GemCuttingCategory extends JeiCategory<GemDefinition> {
    public static final RecipeType<GemDefinition> TYPE = RecipeType.create(ImmersiveJewelry.MOD_ID, "gem_cutting", GemDefinition.class);

    public GemCuttingCategory(IGuiHelper helper) {
        super(helper, TYPE, new ItemStack(ModItems.LAPIDARY_WHEEL.get()), "gem_cutting", 150, 58);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GemDefinition gem, IFocusGroup focuses) {
        List<ItemStack> inputs = new ArrayList<>();
        inputs.addAll(Inventories.items(gem.ingredient()));
        var a = gem.appraisal();
        inputs.add(RoughGemItem.create(GemData.rough(gem.id(), (a.sizeMin() + a.sizeMax()) / 2F, a.qualityMean(), a.inclusionsMean())));
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 6).addItemStacks(inputs).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, 30).addItemStack(new ItemStack(ModItems.LAPIDARY_WHEEL.get())).setBackground(slot, -1, -1);
        List<ItemStack> cuts = new ArrayList<>();
        for (CutDefinition cut : JewelryDefinitions.client().cutsFor(gem)) cuts.add(CutGemItem.create(JewelryFactory.sampleCut(gem, cut, 0.8F)));
        int x = 50;
        for (ItemStack cut : cuts) {
            if (x > 132) break;
            builder.addSlot(RecipeIngredientRole.OUTPUT, x, 6).addItemStack(cut).setBackground(slot, -1, -1);
            x += 20;
        }
    }

    @Override
    public void draw(GemDefinition gem, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        arrow.draw(g, 22, 6);
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable("jei.immersive_jewelry.gem_hardness", DescriptionBuilder.trim(gem.hardness())), 24, 28, VanillaGui.LABEL, false);
        g.drawString(font, Component.translatable("jei.immersive_jewelry.gem_brittleness", Math.round(gem.brittleness() * 100)), 24, 38, VanillaGui.LABEL, false);
        var trait = JewelryDefinitions.client().traitFor(gem);
        if (trait != null) {
            g.drawString(font, VanillaGui.clipped(font, Component.translatable("jei.immersive_jewelry.gem_trait", trait.name()), 124), 24, 48, VanillaGui.LABEL, false);
        }
    }

    @Override
    protected com.otectus.immersivejewelry.client.guide.HelpTopics.Topic helpTopic(GemDefinition recipe) {
        return topic("stones/gems", com.otectus.immersivejewelry.client.guide.Lessons.APPRAISAL_AND_SOURCES);
    }
}
