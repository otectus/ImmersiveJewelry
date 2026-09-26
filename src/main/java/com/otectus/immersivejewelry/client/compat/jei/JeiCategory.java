package com.otectus.immersivejewelry.client.compat.jei;

import com.otectus.immersivejewelry.client.screen.JewelryGui;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Common plumbing of the jewelry JEI categories. */
abstract class JeiCategory<T> implements IRecipeCategory<T> {
    protected final RecipeType<T> type;
    protected final IDrawable icon;
    protected final IDrawable slot;
    protected final IDrawable arrow;
    private final String key;
    private final int width;
    private final int height;

    JeiCategory(IGuiHelper helper, RecipeType<T> type, ItemStack icon, String key, int width, int height) {
        this.type = type;
        this.icon = helper.createDrawableItemStack(icon);
        this.slot = new IDrawable() {
            @Override public int getWidth() { return 18; }
            @Override public int getHeight() { return 18; }
            @Override public void draw(GuiGraphics g, int x, int y) { JewelryGui.slot(g, x, y); }
        };
        this.arrow = new IDrawable() {
            @Override public int getWidth() { return 22; }
            @Override public int getHeight() { return 16; }
            @Override public void draw(GuiGraphics g, int x, int y) {
                JewelryGui.line(g, x + 2, y + 8, x + 16, y + 8, 2, JewelryGui.EDGE_LIGHT);
                JewelryGui.line(g, x + 12, y + 3, x + 18, y + 9, 1, JewelryGui.BRASS_LIGHT);
                JewelryGui.line(g, x + 18, y + 9, x + 12, y + 15, 1, JewelryGui.BRASS_LIGHT);
            }
        };
        this.key = key;
        this.width = width + 12;
        this.height = height + 12;
    }

    @Override public RecipeType<T> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable("jei.immersive_jewelry." + key); }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public IDrawable getBackground() {
        return new IDrawable() {
            @Override public int getWidth() { return width; }
            @Override public int getHeight() { return height; }
            @Override public void draw(GuiGraphics g, int x, int y) { JewelryGui.dialog(g, x, y, width, height); }
        };
    }
}
