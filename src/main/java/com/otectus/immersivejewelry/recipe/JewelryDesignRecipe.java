package com.otectus.immersivejewelry.recipe;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModRecipeSerializers;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A jewelry design (spec 56): which form it makes, how many stones and which cuts it takes, how the stones are set
 * and which finishes suit it. A real recipe type so designs sync to clients, show in JEI and honour conditions; the
 * bench is the only place that uses them.
 *
 * @param stones         stones set into the piece (all of one gem)
 * @param style          prong or bezel
 * @param pointsPerStone prongs (or bezel push points) around each stone
 * @param tightness      narrows pressure windows (1 = normal)
 * @param protection     multiplies the wear resistance of the piece (a bezel shields its stone)
 * @param allowedCuts    empty = any cut
 * @param allowedGems    empty = any gem
 * @param allowedFinishes empty = every finish
 * @param sketch         must be learned from a Design Sketch
 * @param workProfile    optional hands-on tuning ({@code work_profiles}); null: the defaults for the setting style
 */
public record JewelryDesignRecipe(ResourceLocation id, ResourceLocation form, int stones, SettingStyle style, int pointsPerStone,
                                  float tightness, float protection, List<ResourceLocation> allowedCuts,
                                  List<ResourceLocation> allowedGems, List<ResourceLocation> allowedFinishes, boolean sketch,
                                  int sortOrder, Component name, @Nullable ResourceLocation workProfile) implements Recipe<Container> {

    public boolean allowsCut(@Nullable ResourceLocation cut) {
        return cut != null && (allowedCuts.isEmpty() || allowedCuts.contains(cut));
    }

    public boolean allowsGem(ResourceLocation gem) {
        return allowedGems.isEmpty() || allowedGems.contains(gem);
    }

    public boolean allowsFinish(ResourceLocation finish) {
        return allowedFinishes.isEmpty() || allowedFinishes.contains(finish);
    }

    public boolean accepts(GemData stone) {
        return stone.isCut() && allowsCut(stone.cut()) && allowsGem(stone.gem());
    }

    /** The blank item this design is built on. */
    public ItemStack blankItem(JewelryDefinitions defs) {
        FormDefinition f = defs.form(form);
        if (f == null) return ItemStack.EMPTY;
        var item = ForgeRegistries.ITEMS.getValue(f.blank());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return getResultItem(access).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        var item = ForgeRegistries.ITEMS.getValue(form);
        return item == null ? new ItemStack(ModItems.RING.get()) : new ItemStack(item);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModItems.JEWELERS_BENCH.get());
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.JEWELRY_DESIGN.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.JEWELRY_DESIGN.get();
    }

    public int totalPoints() {
        return stones * pointsPerStone;
    }
}
