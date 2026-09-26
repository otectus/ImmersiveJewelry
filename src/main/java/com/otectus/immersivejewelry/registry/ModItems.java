package com.otectus.immersivejewelry.registry;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.DesignSketchItem;
import com.otectus.immersivejewelry.item.HandbookItem;
import com.otectus.immersivejewelry.item.HintItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.item.LapItem;
import com.otectus.immersivejewelry.item.PliersItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryForms;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ImmersiveJewelry.MOD_ID);

    // Finished jewelry: one item per form, identity in NBT.
    public static final RegistryObject<JewelryItem> RING = ITEMS.register("ring", () -> new JewelryItem(JewelryForms.RING, jewelry()));
    public static final RegistryObject<JewelryItem> AMULET = ITEMS.register("amulet", () -> new JewelryItem(JewelryForms.AMULET, jewelry()));
    public static final RegistryObject<JewelryItem> BRACELET = ITEMS.register("bracelet", () -> new JewelryItem(JewelryForms.BRACELET, jewelry()));
    public static final RegistryObject<JewelryItem> CIRCLET = ITEMS.register("circlet", () -> new JewelryItem(JewelryForms.CIRCLET, jewelry()));

    // Metal blanks and frames: smithed with Immersive Smithing, or crafted as a fallback.
    public static final RegistryObject<JewelryBlankItem> RING_BLANK = ITEMS.register("ring_blank",
            () -> new JewelryBlankItem(JewelryForms.RING, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<JewelryBlankItem> AMULET_FRAME = ITEMS.register("amulet_frame",
            () -> new JewelryBlankItem(JewelryForms.AMULET, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<JewelryBlankItem> BRACELET_BLANK = ITEMS.register("bracelet_blank",
            () -> new JewelryBlankItem(JewelryForms.BRACELET, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<JewelryBlankItem> CIRCLET_FRAME = ITEMS.register("circlet_frame",
            () -> new JewelryBlankItem(JewelryForms.CIRCLET, new Item.Properties().stacksTo(16)));

    // Gemstones: an appraised rough stone and a cut stone, both carrying GemData.
    public static final RegistryObject<RoughGemItem> ROUGH_GEM = ITEMS.register("rough_gem", () -> new RoughGemItem(new Item.Properties()));
    public static final RegistryObject<CutGemItem> CUT_GEM = ITEMS.register("cut_gem", () -> new CutGemItem(new Item.Properties()));

    // Tools and consumables. Pliers precision widens pressure windows; laps are the wheel's upgradeable abrasive.
    public static final RegistryObject<PliersItem> JEWELERS_PLIERS = ITEMS.register("jewelers_pliers",
            () -> new PliersItem(1.0F, new Item.Properties().durability(192)));
    public static final RegistryObject<PliersItem> FINE_PLIERS = ITEMS.register("fine_pliers",
            () -> new PliersItem(1.3F, new Item.Properties().durability(640).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<LapItem> EMERY_LAP = ITEMS.register("emery_lap",
            () -> new LapItem(LapItem.Tier.EMERY, new Item.Properties().durability(48)));
    public static final RegistryObject<LapItem> DIAMOND_LAP = ITEMS.register("diamond_lap",
            () -> new LapItem(LapItem.Tier.DIAMOND, new Item.Properties().durability(256).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> POLISHING_COMPOUND = ITEMS.register("polishing_compound",
            () -> new HintItem(new Item.Properties(), "polishing_compound"));
    public static final RegistryObject<Item> GEM_DUST = ITEMS.register("gem_dust",
            () -> new HintItem(new Item.Properties(), "gem_dust"));
    public static final RegistryObject<DesignSketchItem> DESIGN_SKETCH = ITEMS.register("design_sketch",
            () -> new DesignSketchItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<HandbookItem> JEWELERS_HANDBOOK = ITEMS.register("jewelers_handbook",
            () -> new HandbookItem(new Item.Properties().stacksTo(1)));

    // Stations.
    public static final RegistryObject<Item> LAPIDARY_WHEEL = ITEMS.register("lapidary_wheel",
            () -> new BlockItem(ModBlocks.LAPIDARY_WHEEL.get(), new Item.Properties()));
    public static final RegistryObject<Item> JEWELERS_BENCH = ITEMS.register("jewelers_bench",
            () -> new BlockItem(ModBlocks.JEWELERS_BENCH.get(), new Item.Properties()));

    private static Item.Properties jewelry() {
        return new Item.Properties().stacksTo(1);
    }

    private ModItems() {}
}
