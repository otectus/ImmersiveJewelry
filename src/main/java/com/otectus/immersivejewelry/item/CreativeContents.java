package com.otectus.immersivejewelry.item;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.metal.MetalDefinition;
import com.otectus.immersivejewelry.jewelry.ClientHooks;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.jewelry.JewelryForms;
import com.otectus.immersivejewelry.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** Creative tab contents, built from the synced definitions so datapack gems and metals appear too. */
public final class CreativeContents {
    private static final ResourceLocation GOLD = new ResourceLocation("minecraft", "gold");

    public static ItemStack icon() {
        GemData ruby = new GemData(ImmersiveJewelry.id("ruby"), 1F, 1.2F, 0.8F, 0.1F, ImmersiveJewelry.id("brilliant"), 0.9F,
                com.otectus.immersivejewelry.api.gem.GemDamage.NONE, 1F);
        return JewelryFactory.jewelry(JewelryForms.RING, MetalRef.of(GOLD, null, 0xF2C541), List.of(ruby), null,
                ImmersiveJewelry.id("polished"), 0.9F, 0.9F, null);
    }

    public static void fill(JewelryDefinitions defs, CreativeModeTab.Output out) {
        out.accept(ModItems.JEWELERS_HANDBOOK.get());
        out.accept(ModItems.LAPIDARY_WHEEL.get());
        out.accept(ModItems.JEWELERS_BENCH.get());
        out.accept(ModItems.JEWELERS_PLIERS.get());
        out.accept(ModItems.FINE_PLIERS.get());
        out.accept(ModItems.EMERY_LAP.get());
        out.accept(ModItems.DIAMOND_LAP.get());
        out.accept(ModItems.POLISHING_COMPOUND.get());
        out.accept(ModItems.GEM_DUST.get());
        for (ResourceLocation design : ClientHooks.sketchDesigns.get()) out.accept(DesignSketchItem.create(design));

        List<MetalDefinition> metals = defs.metals().stream().filter(m -> !m.generic()).toList();
        for (FormDefinition form : defs.forms()) {
            Item blank = ForgeRegistries.ITEMS.getValue(form.blank());
            if (blank == null) continue;
            for (MetalDefinition metal : metals) {
                out.accept(JewelryFactory.blank(blank, MetalRef.of(metal.id(), metal.name(), metal.color()), 0.75F));
            }
        }
        for (GemDefinition gem : defs.gems()) {
            if (gem.auto()) continue;
            var a = gem.appraisal();
            out.accept(RoughGemItem.create(GemData.rough(gem.id(), (a.sizeMin() + a.sizeMax()) / 2F, a.qualityMean(), a.inclusionsMean())));
            for (CutDefinition cut : defs.cutsFor(gem)) out.accept(CutGemItem.create(JewelryFactory.sampleCut(gem, cut, 0.85F)));
        }
        MetalRef gold = JewelryFactory.metal(defs, GOLD);
        for (FormDefinition form : defs.forms()) {
            for (GemDefinition gem : defs.gems()) {
                if (gem.auto()) continue;
                CutDefinition cut = JewelryFactory.preferredCut(defs, gem);
                if (cut == null) continue;
                ItemStack piece = JewelryFactory.jewelry(form.id(), gold, List.of(JewelryFactory.sampleCut(gem, cut, 0.85F)), null,
                        ImmersiveJewelry.id("polished"), 0.8F, 0.85F, null);
                if (!piece.isEmpty()) out.accept(piece);
            }
        }
    }

    private CreativeContents() {}
}
