package com.otectus.immersivejewelry.integration.smithing;

import com.otectus.immersivesmithing.api.ISmithingRecipeProvider;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.material.MaterialRegistry;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.metal.MetalDefinition;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Blank recipes for the material families that no datapack recipe covers (modded and auto-detected metals):
 * {@code immersive_jewelry:smithing/<form>/<ns>/<family>}. Families with a jewelry metal definition get real datapack
 * recipes under the same ids ({@code data/immersive_jewelry/recipes/smithing/}), which reach clients and JEI; provider
 * recipes are used by the forge but, in Immersive Smithing 1.0.0, are not inserted into the recipe manager.
 * Small workpieces (spec 5.3): the forge pattern {@code immersive_jewelry:small_workpiece} and one anvil pattern per
 * form. The result already names its metal (with the jewelry metal definition's tint and name when there is one, else
 * Smithing's), so the anvil and JEI show a gold ring blank as gold.
 */
final class SmithingBlankRecipes implements ISmithingRecipeProvider {
    static final ResourceLocation FORGE_PATTERN = ImmersiveJewelry.id("small_workpiece");

    @Override
    public Collection<SmithingRecipe> recipes(MaterialRegistry materials, RegistryAccess access) {
        List<SmithingRecipe> out = new ArrayList<>();
        JewelryDefinitions defs = JewelryDefinitions.server();
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        var existing = server == null ? null : server.getRecipeManager();
        for (MaterialFamily family : materials.families()) {
            if (SmithingIntegration.blacklisted(family.id().toString())) continue;
            MetalDefinition def = defs.metalDefinition(family.id());
            MetalRef metal = def != null ? MetalRef.of(family.id(), def.name(), def.color()) : MetalRef.of(family.id(), family.displayName(), family.tint());
            for (FormDefinition form : defs.forms()) {
                Item blank = ForgeRegistries.ITEMS.getValue(form.blank());
                if (blank == null || blank == net.minecraft.world.item.Items.AIR) continue;
                ItemStack result = new ItemStack(blank);
                BlankData.of(metal, 0.5F).apply(result);
                ResourceLocation id = ImmersiveJewelry.id("smithing/" + form.id().getPath() + "/" + family.id().getNamespace() + "/" + family.id().getPath());
                if (existing != null && existing.byKey(id).isPresent()) continue; // a datapack recipe already makes it
                out.add(new SmithingRecipe(id, family.id(), form.metalUnits(), List.of(), result, FORGE_PATTERN, form.anvilPattern(), false, null));
            }
        }
        return out;
    }
}
