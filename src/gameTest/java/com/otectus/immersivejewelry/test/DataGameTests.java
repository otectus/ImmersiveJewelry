package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.data.ExpressionDefinition;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.trait.TraitDefinition;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.workstation.BenchPlanner;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import com.otectus.immersivejewelry.util.Inventories;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Datapack definitions (spec 25-29, 45, 73): they load, they agree with each other, and a pack can add a gem without Java. */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DataGameTests {

    @GameTest(template = "empty")
    public static void builtInDefinitionsLoad(GameTestHelper h) {
        JewelryDefinitions d = JewelryDefinitions.server();
        h.assertTrue(d.forms().size() == 4, "Four forms, got " + d.forms().size());
        h.assertTrue(d.cuts().size() == 4, "Four cuts, got " + d.cuts().size());
        h.assertTrue(d.finishes().size() >= 3, "At least three finishes");
        h.assertTrue(d.traits().size() >= 10, "Ten traits, got " + d.traits().size());
        for (String gem : List.of("diamond", "emerald", "amethyst", "lapis", "quartz", "ruby", "sapphire", "topaz", "onyx")) {
            h.assertTrue(d.gem(ImmersiveJewelry.id(gem)) != null, "Gem " + gem + " is defined");
        }
        h.assertTrue(d.metalDefinition(new ResourceLocation("minecraft", "gold")) != null, "Gold has a jewelry profile");
        h.assertTrue(d.gemForSource(new ItemStack(Items.DIAMOND)) == d.gem(ImmersiveJewelry.id("diamond")), "A diamond is a rough diamond");
        h.assertTrue(d.gemForSource(new ItemStack(Items.AMETHYST_SHARD)) == d.gem(ImmersiveJewelry.id("amethyst")), "An amethyst shard is a rough amethyst");
        h.assertTrue(d.gemForSource(new ItemStack(Items.DIRT)) == null, "Dirt is not a gem");
        h.succeed();
    }

    /** Forge fills an empty tag's ingredient with a Barrier placeholder; nothing may treat that as a real item. */
    @GameTest(template = "empty")
    public static void emptyTagsHaveNoItems(GameTestHelper h) {
        JewelryDefinitions d = JewelryDefinitions.server();
        h.assertTrue(Inventories.isEmpty(Ingredient.of(ItemTags.create(new ResourceLocation("forge", "nuggets/no_such_metal")))), "An empty tag has no items");
        h.assertTrue(d.gemForSource(new ItemStack(Items.BARRIER)) == null, "A barrier is not the rough stone of a gem whose tag is empty");
        Ingredient copper = BenchPlanner.repairIngredient(d, JewelryFactory.metal(d, new ResourceLocation("minecraft", "copper")));
        h.assertTrue(copper != null && copper.test(new ItemStack(Items.COPPER_INGOT)), "Copper frames repair with copper ingots");
        Ingredient netherite = BenchPlanner.repairIngredient(d, JewelryFactory.metal(d, new ResourceLocation("minecraft", "netherite")));
        h.assertTrue(netherite != null && netherite.test(new ItemStack(Items.NETHERITE_SCRAP)), "Netherite frames repair with scrap, as defined");
        // An undefined metal falls back from its nugget tag (empty: vanilla has no netherite nuggets) to its ingot tag.
        Ingredient fallback = BenchPlanner.repairIngredient(d, MetalRef.of(new ResourceLocation("gametest", "netherite")));
        h.assertTrue(fallback != null && fallback.test(new ItemStack(Items.NETHERITE_INGOT)), "An empty nugget tag falls back to ingots");
        // Silver's nuggets and ingots come from other mods; without them there is no repair, rather than a barrier.
        h.assertTrue(BenchPlanner.repairIngredient(d, JewelryFactory.metal(d, new ResourceLocation("forge", "silver"))) == null
                || !ForgeRegistries.ITEMS.tags().getTag(ItemTags.create(new ResourceLocation("forge", "ingots/silver"))).isEmpty(), "No silver, no repair");
        Ingredient gold = BenchPlanner.repairIngredient(d, JewelryFactory.metal(d, new ResourceLocation("minecraft", "gold")));
        h.assertTrue(gold != null && gold.test(new ItemStack(Items.GOLD_NUGGET)), "Gold frames repair with gold nuggets");
        h.assertTrue(Inventories.display(Ingredient.of(ItemTags.create(new ResourceLocation("forge", "gems/no_such_gem"))), 1).isEmpty(),
                "An empty tag displays as nothing, not a barrier");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyGemExpressesDifferentlyOnEachForm(GameTestHelper h) {
        JewelryDefinitions d = JewelryDefinitions.server();
        for (GemDefinition gem : d.gems()) {
            if (gem.auto()) continue;
            TraitDefinition trait = d.traitFor(gem);
            h.assertTrue(trait != null, gem.id() + " has a trait");
            if (!gem.id().getNamespace().equals(ImmersiveJewelry.MOD_ID)) continue;
            Set<Class<?>> kinds = new HashSet<>();
            for (FormDefinition form : d.forms()) {
                List<ExpressionDefinition> e = trait.forForm(form.id());
                h.assertTrue(!e.isEmpty(), gem.id() + " does something as a " + form.id());
                kinds.add(e.get(0).effect().getClass());
            }
            // Spec 17: the form reinterprets the theme instead of repeating one bonus (resonance is the neutral profile).
            if (!trait.id().equals(GemDefinition.GENERIC_TRAIT)) {
                h.assertTrue(kinds.size() >= 3, gem.id() + " behaves differently across forms, got " + kinds);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aDatapackAddsAGemWithoutJava(GameTestHelper h) {
        JewelryDefinitions d = JewelryDefinitions.server();
        GemDefinition moon = d.gem(new ResourceLocation("gametest_pack", "moonstone"));
        h.assertTrue(moon != null, "The datapack moonstone is loaded");
        h.assertTrue(moon.supports(ImmersiveJewelry.id("cabochon")) && !moon.supports(ImmersiveJewelry.id("brilliant")), "Its cuts come from data");
        h.assertTrue(d.traitFor(moon).id().equals(new ResourceLocation("gametest_pack", "lunar")), "and its trait too");
        h.assertTrue(d.gemForSource(new ItemStack(Items.PRISMARINE_CRYSTALS)) == moon, "Prismarine crystals are rough moonstones");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void designsLoadAsRecipes(GameTestHelper h) {
        List<JewelryDesignRecipe> designs = h.getLevel().getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get());
        h.assertTrue(designs.size() >= 10, "Ten designs, got " + designs.size());
        long sketches = designs.stream().filter(JewelryDesignRecipe::sketch).count();
        h.assertTrue(sketches >= 4, "Advanced designs are learned from sketches, got " + sketches);
        Set<ResourceLocation> forms = new HashSet<>();
        designs.stream().filter(r -> !r.sketch()).forEach(r -> forms.add(r.form()));
        h.assertTrue(forms.size() == 4, "Every form has a basic design");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gemSourcesAreHinted(GameTestHelper h) {
        // The definition's ingredient resolves through the forge:gems tags, so modded gems join automatically.
        GemDefinition diamond = JewelryDefinitions.server().gem(ImmersiveJewelry.id("diamond"));
        h.assertTrue(diamond.hasSources(), "Diamonds have raw sources");
        GemDefinition ruby = JewelryDefinitions.server().gem(ImmersiveJewelry.id("ruby"));
        h.assertTrue(ruby != null && ruby.rarity().tradeLevel() >= 3, "Rubies are rare");
        h.succeed();
    }
}
