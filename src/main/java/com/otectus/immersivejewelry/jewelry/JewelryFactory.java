package com.otectus.immersivejewelry.jewelry;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.metal.MetalDefinition;
import com.otectus.immersivejewelry.quality.Workmanship;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds item stacks from data: finished pieces, blanks and stones (commands, trades, loot, migration, creative tab). */
public final class JewelryFactory {

    /** A finished piece; workmanship is computed from the three stage qualities. */
    public static ItemStack jewelry(ResourceLocation form, MetalRef metal, List<GemData> gems, @Nullable ResourceLocation design,
                                    @Nullable ResourceLocation finish, float smithing, float setting, @Nullable String maker) {
        float cut = 0F;
        for (GemData g : gems) cut += g.cutQuality();
        cut = gems.isEmpty() ? 0F : cut / gems.size();
        JewelryData data = new JewelryData(form, metal, gems, design, finish, smithing, setting,
                Workmanship.compute(smithing, cut, setting), 1F, 0, Set.<SettingDefect>of(), maker, false, null);
        return stack(data);
    }

    public static ItemStack stack(JewelryData data) {
        Item item = itemFor(data.form());
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        data.apply(stack);
        return stack;
    }

    @Nullable
    public static Item itemFor(ResourceLocation form) {
        FormDefinition def = JewelryDefinitions.current().form(form);
        Item item = ForgeRegistries.ITEMS.getValue(def != null ? def.item() : form);
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    @Nullable
    public static Item blankItemFor(ResourceLocation form) {
        FormDefinition def = JewelryDefinitions.current().form(form);
        if (def == null) return null;
        Item item = ForgeRegistries.ITEMS.getValue(def.blank());
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    public static ItemStack blank(Item blankItem, MetalRef metal, float smithing) {
        ItemStack stack = new ItemStack(blankItem);
        BlankData.of(metal, smithing).apply(stack);
        return stack;
    }

    /** A metal reference carrying the definition's name and colour (for pieces made outside Immersive Smithing). */
    public static MetalRef metal(JewelryDefinitions defs, ResourceLocation id) {
        MetalDefinition def = defs.metalDefinition(id);
        return def == null ? MetalRef.of(id) : MetalRef.of(id, def.name(), def.color());
    }

    /** A stone cut to {@code quality}: typical size and character for the gem. */
    public static GemData sampleCut(GemDefinition gem, CutDefinition cut, float quality) {
        var a = gem.appraisal();
        float size = (a.sizeMin() + a.sizeMax()) / 2F;
        GemData rough = GemData.rough(gem.id(), size, a.qualityMean(), a.inclusionsMean());
        return rough.withCut(cut.id(), quality, size * gem.sizeRetention() * cut.sizeRetention(),
                com.otectus.immersivejewelry.api.gem.GemDamage.NONE, 1F);
    }

    /** The first cut a gem supports (by cut sort order), for examples. */
    @Nullable
    public static CutDefinition preferredCut(JewelryDefinitions defs, GemDefinition gem) {
        for (ResourceLocation id : gem.favoredCuts()) {
            CutDefinition c = defs.cut(id);
            if (c != null) return c;
        }
        List<CutDefinition> cuts = new ArrayList<>(defs.cutsFor(gem));
        return cuts.isEmpty() ? null : cuts.get(0);
    }

    private JewelryFactory() {}
}
