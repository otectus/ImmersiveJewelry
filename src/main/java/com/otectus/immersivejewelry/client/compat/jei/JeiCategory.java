package com.otectus.immersivejewelry.client.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Common plumbing of the jewelry JEI categories, in JEI's own vanilla style: JEI's slots and recipe arrow on a plain
 * background, with dark grey notes, as in the Immersive Smithing category.
 */
abstract class JeiCategory<T> implements IRecipeCategory<T> {
    protected final RecipeType<T> type;
    protected final IDrawable icon;
    protected final IDrawable slot;
    protected final IDrawable arrow;
    private final IDrawable background;
    private final String key;
    private final int width;
    private final int height;

    JeiCategory(IGuiHelper helper, RecipeType<T> type, ItemStack icon, String key, int width, int height) {
        this.type = type;
        this.icon = helper.createDrawableItemStack(icon);
        this.slot = helper.getSlotDrawable();
        this.arrow = helper.getRecipeArrow();
        this.background = helper.createBlankDrawable(width, height);
        this.key = key;
        this.width = width;
        this.height = height;
    }

    @Override public RecipeType<T> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable("jei.immersive_jewelry." + key); }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public IDrawable getBackground() { return background; }
}
