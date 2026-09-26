package com.otectus.immersivejewelry.client.compat.jei;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.integration.Integrations;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.DesignSketchItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import com.otectus.immersivejewelry.util.Inventories;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI (spec 57): explains every step without bypassing it. Cutting and designs come from the synced definitions and
 * recipes; blanks point at Immersive Smithing's own categories when it is installed (its recipes for our blanks are
 * real smithing recipes and appear there), and at the crafting fallback otherwise.
 */
@JeiPlugin
public class ImmersiveJewelryJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ImmersiveJewelry.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Stones of different gems (and cuts) are different entries; quality is not.
        registration.registerSubtypeInterpreter(ModItems.ROUGH_GEM.get(), (stack, ctx) -> CutGemItem.data(stack).map(d -> d.gem().toString())
                .orElse(com.otectus.immersivejewelry.item.RoughGemItem.data(stack).map(d -> d.gem().toString()).orElse("")));
        registration.registerSubtypeInterpreter(ModItems.CUT_GEM.get(), (stack, ctx) -> CutGemItem.data(stack).map(d -> d.gem() + "/" + d.cut()).orElse(""));
        // Blanks differ by metal (smithing recipes make one metal each; designs accept every metal). Finished pieces are
        // listed per metal and stone, but any piece of a form looks up every design for that form.
        for (Item blank : new Item[]{ModItems.RING_BLANK.get(), ModItems.AMULET_FRAME.get(), ModItems.BRACELET_BLANK.get(), ModItems.CIRCLET_FRAME.get()}) {
            registration.registerSubtypeInterpreter(blank, (stack, ctx) -> BlankData.get(stack).map(d -> d.metal().id().toString()).orElse(""));
        }
        for (Item piece : new Item[]{ModItems.RING.get(), ModItems.AMULET.get(), ModItems.BRACELET.get(), ModItems.CIRCLET.get()}) {
            registration.registerSubtypeInterpreter(piece, (stack, ctx) -> ctx == UidContext.Recipe ? IIngredientSubtypeInterpreter.NONE
                    : JewelryData.get(stack).map(d -> d.metal().id() + "/" + (d.principal() == null ? "" : d.principal().gem())).orElse(""));
        }
        registration.registerSubtypeInterpreter(ModItems.DESIGN_SKETCH.get(), (stack, ctx) -> {
            ResourceLocation design = DesignSketchItem.design(stack);
            return design == null ? IIngredientSubtypeInterpreter.NONE : design.toString();
        });
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new GemCuttingCategory(helper));
        registration.addRecipeCategories(new DesignCategory(helper));
        registration.addRecipeCategories(new ReferenceCategory(helper, ReferenceCategory.FINISHING, new ItemStack(ModItems.POLISHING_COMPOUND.get()), "finishing"));
        registration.addRecipeCategories(new ReferenceCategory(helper, ReferenceCategory.REWORKING, new ItemStack(ModItems.JEWELERS_PLIERS.get()), "reworking"));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        registration.addRecipes(GemCuttingCategory.TYPE, defs.gems().stream().filter(g -> !g.auto() || g.hasSources()).toList());
        if (Minecraft.getInstance().level != null) {
            registration.addRecipes(DesignCategory.TYPE, new ArrayList<>(Minecraft.getInstance().level.getRecipeManager()
                    .getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get())).stream()
                    .sorted(java.util.Comparator.comparingInt(JewelryDesignRecipe::sortOrder)).toList());
        }
        ItemStack example = example(defs);
        List<ReferenceCategory.Entry> finishing = new ArrayList<>();
        for (FinishDefinition f : defs.finishes()) {
            ItemStack cost = Inventories.display(f.cost(), f.costCount());
            ItemStack after = example.copy();
            JewelryData.get(after).ifPresent(d -> d.withFinish(f.id()).apply(after));
            List<List<ItemStack>> in = new ArrayList<>();
            in.add(List.of(example));
            if (!cost.isEmpty()) in.add(List.of(cost));
            finishing.add(new ReferenceCategory.Entry(in, new ItemStack(ModItems.JEWELERS_BENCH.get()), List.of(List.of(after)), f.name(), f.description()));
        }
        registration.addRecipes(ReferenceCategory.FINISHING, finishing);
        List<ReferenceCategory.Entry> rework = new ArrayList<>();
        if (!example.isEmpty()) {
            Item blank = JewelryFactory.blankItemFor(com.otectus.immersivejewelry.jewelry.JewelryForms.RING);
            ItemStack frame = blank == null ? ItemStack.EMPTY : JewelryFactory.blank(blank, JewelryFactory.metal(defs, new ResourceLocation("minecraft", "gold")), 0.75F);
            ItemStack stone = JewelryData.get(example).map(d -> CutGemItem.create(d.principal())).orElse(ItemStack.EMPTY);
            rework.add(new ReferenceCategory.Entry(List.of(List.of(example)), new ItemStack(ModItems.JEWELERS_PLIERS.get()), List.of(List.of(frame), List.of(stone)),
                    Component.translatable("jei.immersive_jewelry.remove_title"), Component.translatable("jei.immersive_jewelry.remove_text")));
            rework.add(new ReferenceCategory.Entry(List.of(List.of(example)), new ItemStack(ModItems.JEWELERS_BENCH.get()), List.of(List.of(example)),
                    Component.translatable("jei.immersive_jewelry.repair_title"), Component.translatable("jei.immersive_jewelry.repair_text")));
            rework.add(new ReferenceCategory.Entry(List.of(List.of(stone)), new ItemStack(ModItems.LAPIDARY_WHEEL.get()), List.of(List.of(stone)),
                    Component.translatable("jei.immersive_jewelry.recut_title"), Component.translatable("jei.immersive_jewelry.recut_text")));
        }
        registration.addRecipes(ReferenceCategory.REWORKING, rework);

        String blanks = Integrations.smithingActive() ? "blanks_smithing" : "blanks_fallback";
        for (Item item : new Item[]{ModItems.RING_BLANK.get(), ModItems.AMULET_FRAME.get(), ModItems.BRACELET_BLANK.get(), ModItems.CIRCLET_FRAME.get()}) {
            info(registration, new ItemStack(item), blanks);
        }
        info(registration, new ItemStack(ModItems.LAPIDARY_WHEEL.get()), "wheel");
        info(registration, new ItemStack(ModItems.JEWELERS_BENCH.get()), "bench");
        info(registration, new ItemStack(ModItems.JEWELERS_PLIERS.get()), "pliers");
        info(registration, new ItemStack(ModItems.FINE_PLIERS.get()), "pliers");
        info(registration, new ItemStack(ModItems.EMERY_LAP.get()), "lap");
        info(registration, new ItemStack(ModItems.DIAMOND_LAP.get()), "lap");
        info(registration, new ItemStack(ModItems.DESIGN_SKETCH.get()), "sketch");
        info(registration, new ItemStack(ModItems.GEM_DUST.get()), "gem_dust");
        for (Item item : new Item[]{ModItems.RING.get(), ModItems.AMULET.get(), ModItems.BRACELET.get(), ModItems.CIRCLET.get()}) {
            info(registration, new ItemStack(item), "jewelry");
        }
    }

    private static ItemStack example(JewelryDefinitions defs) {
        GemDefinition gem = defs.gem(ImmersiveJewelry.id("ruby"));
        if (gem == null) gem = defs.gems().stream().findFirst().orElse(null);
        if (gem == null) return ItemStack.EMPTY;
        CutDefinition cut = JewelryFactory.preferredCut(defs, gem);
        if (cut == null) return ItemStack.EMPTY;
        GemData stone = JewelryFactory.sampleCut(gem, cut, 0.85F);
        MetalRef gold = JewelryFactory.metal(defs, new ResourceLocation("minecraft", "gold"));
        FormDefinition form = defs.form(com.otectus.immersivejewelry.jewelry.JewelryForms.RING);
        return JewelryFactory.jewelry(form != null ? form.id() : com.otectus.immersivejewelry.jewelry.JewelryForms.RING, gold, List.of(stone), null,
                ImmersiveJewelry.id("polished"), 0.8F, 0.85F, null);
    }

    private static void info(IRecipeRegistration registration, ItemStack stack, String key) {
        registration.addItemStackInfo(stack, Component.translatable("jei.immersive_jewelry.info." + key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.LAPIDARY_WHEEL.get()), GemCuttingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.JEWELERS_BENCH.get()), DesignCategory.TYPE, ReferenceCategory.FINISHING,
                ReferenceCategory.REWORKING);
        registration.addRecipeCatalyst(new ItemStack(ModItems.JEWELERS_PLIERS.get()), DesignCategory.TYPE, ReferenceCategory.REWORKING);
    }
}
