package com.otectus.immersivejewelry.client.compat.jei;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.client.screen.JewelryGui;
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

import java.util.List;

/**
 * Finishing and reworking at the bench, shown as simple entries: inputs, catalyst, outputs and a sentence. These are
 * explanations, not recipes JEI can fill: the bench decides at the moment of use.
 */
public class ReferenceCategory extends JeiCategory<ReferenceCategory.Entry> {
    private static final int WIDTH = 170;

    public record Entry(List<List<ItemStack>> inputs, ItemStack catalyst, List<List<ItemStack>> outputs, Component title, Component text) {}

    public static final RecipeType<Entry> FINISHING = RecipeType.create(ImmersiveJewelry.MOD_ID, "finishing", Entry.class);
    public static final RecipeType<Entry> REWORKING = RecipeType.create(ImmersiveJewelry.MOD_ID, "reworking", Entry.class);

    public ReferenceCategory(IGuiHelper helper, RecipeType<Entry> type, ItemStack icon, String key) {
        super(helper, type, icon, key, WIDTH, 80);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        int x = 1;
        for (List<ItemStack> in : entry.inputs()) {
            builder.addSlot(RecipeIngredientRole.INPUT, x + 6, 1 + 6).addItemStacks(in).setBackground(slot, -1, -1);
            x += 20;
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, 1 + 6, 22 + 6).addItemStack(entry.catalyst()).setBackground(slot, -1, -1);
        int ox = WIDTH - 19 * entry.outputs().size();
        for (List<ItemStack> out : entry.outputs()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, ox + 6, 1 + 6).addItemStacks(out).setBackground(slot, -1, -1);
            ox += 19;
        }
    }

    @Override
    public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        g.pose().pushPose();
        g.pose().translate(6, 6, 0);
        arrow.draw(g, 70, 1);
        var font = Minecraft.getInstance().font;
        g.drawString(font, entry.title(), 24, 22, JewelryGui.TEXT, false);
        int y = 34;
        for (var line : font.split(entry.text(), WIDTH - 28)) {
            if (y > 70) break;
            g.drawString(font, line, 24, y, JewelryGui.TEXT_DIM, false);
            y += 9;
        }
        g.pose().popPose();
    }
}
