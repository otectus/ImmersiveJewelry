package com.otectus.immersivejewelry.registry;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.recipe.JewelryDesignSerializer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ImmersiveJewelry.MOD_ID);

    public static final RegistryObject<RecipeSerializer<JewelryDesignRecipe>> JEWELRY_DESIGN = RECIPE_SERIALIZERS.register("jewelry_design",
            JewelryDesignSerializer::new);

    private ModRecipeSerializers() {}
}
