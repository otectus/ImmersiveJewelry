package com.otectus.immersivejewelry.registry;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    /** Tools that operate the Jeweler's Bench. */
    public static final TagKey<Item> PLIERS = ItemTags.create(ImmersiveJewelry.id("pliers"));
    /** Abrasive laps the Lapidary Wheel accepts. */
    public static final TagKey<Item> LAPS = ItemTags.create(ImmersiveJewelry.id("laps"));
    /** Items whose use (drawing a bow) Quickdraw effects speed up. */
    public static final TagKey<Item> QUICKDRAW = ItemTags.create(ImmersiveJewelry.id("quickdraw"));
    /** Every rough-gem source item; used by the root advancement. */
    public static final TagKey<Item> GEM_SOURCES = ItemTags.create(ImmersiveJewelry.id("gem_sources"));
    /** Every finished jewelry item. */
    public static final TagKey<Item> JEWELRY = ItemTags.create(ImmersiveJewelry.id("jewelry"));
    /** Every blank and frame. */
    public static final TagKey<Item> BLANKS = ItemTags.create(ImmersiveJewelry.id("blanks"));

    private ModTags() {}
}
