package com.otectus.immersivejewelry.registry;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, ImmersiveJewelry.MOD_ID);

    public static final RegistryObject<RecipeType<JewelryDesignRecipe>> JEWELRY_DESIGN = RECIPE_TYPES.register("jewelry_design",
            () -> RecipeType.simple(ImmersiveJewelry.id("jewelry_design")));

    private ModRecipeTypes() {}
}
