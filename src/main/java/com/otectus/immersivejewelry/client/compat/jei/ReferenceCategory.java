package com.otectus.immersivejewelry.client.compat.jei;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.client.screen.VanillaGui;
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
        super(helper, type, icon, key, WIDTH, textHeight());
    }

    private static int textHeight() {
        int lines = 5;
        for (String key : List.of("finish.immersive_jewelry.polished.description", "finish.immersive_jewelry.burnished.description",
                "finish.immersive_jewelry.antique.description", "jei.immersive_jewelry.remove_text",
                "jei.immersive_jewelry.repair_text", "jei.immersive_jewelry.recut_text")) {
            lines = Math.max(lines, Minecraft.getInstance().font.split(Component.translatable(key), WIDTH - 28).size());
        }
        return Math.max(80, 34 + lines * 9);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        int x = 1;
        for (List<ItemStack> in : entry.inputs()) {
            builder.addSlot(RecipeIngredientRole.INPUT, x, 1).addItemStacks(in).setBackground(slot, -1, -1);
            x += 20;
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, 22).addItemStack(entry.catalyst()).setBackground(slot, -1, -1);
        int ox = WIDTH - 19 * entry.outputs().size();
        for (List<ItemStack> out : entry.outputs()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, ox, 1).addItemStacks(out).setBackground(slot, -1, -1);
            ox += 19;
        }
    }

    @Override
    public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        arrow.draw(g, 70, 1);
        var font = Minecraft.getInstance().font;
        g.drawString(font, entry.title(), 24, 22, VanillaGui.LABEL, false);
        int y = 34;
        for (var line : font.split(entry.text(), WIDTH - 28)) {
            g.drawString(font, line, 24, y, VanillaGui.LABEL_DIM, false);
            y += 9;
        }
    }

    @Override
    protected com.otectus.immersivejewelry.client.guide.HelpTopics.Topic helpTopic(Entry recipe) {
        return topic("start/welcome", com.otectus.immersivejewelry.client.guide.Lessons.FIRST_PIECE);
    }
}
