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

    /**
     * The handbook key over a recipe of this category opens the handbook entry about it (no lookup, no crafting: JEI
     * stays a reference). Each category names its topic.
     */
    @Override
    public boolean handleInput(T recipe, double mouseX, double mouseY, com.mojang.blaze3d.platform.InputConstants.Key input) {
        var key = com.otectus.immersivejewelry.client.workstation.WorkstationKeys.HANDBOOK;
        if (!key.getKey().equals(input)) return false;
        com.otectus.immersivejewelry.client.guide.HandbookNavigator.open(helpTopic(recipe), false);
        return true;
    }

    /** The handbook topic for a recipe of this category. */
    protected abstract com.otectus.immersivejewelry.client.guide.HelpTopics.Topic helpTopic(T recipe);

    protected static com.otectus.immersivejewelry.client.guide.HelpTopics.Topic topic(String entry,
                                                                                       @org.jetbrains.annotations.Nullable com.otectus.immersivejewelry.client.guide.Lessons lesson) {
        return new com.otectus.immersivejewelry.client.guide.HelpTopics.Topic(com.otectus.immersivejewelry.ImmersiveJewelry.id(entry), 0, lesson);
    }

    @Override public RecipeType<T> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable("jei.immersive_jewelry." + key); }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public IDrawable getBackground() { return background; }
}
