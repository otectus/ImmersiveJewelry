package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.migration.LegacyJewelcraft;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModVillagers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Jewelcraft Reforged worlds (spec 50): old ids resolve, old pieces convert on load, nothing is deleted. */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MigrationGameTests {

    private static CompoundTag legacyStack(String id, String metal, String gem, boolean inset) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "jewelcraft:" + id);
        stack.putByte("Count", (byte) 1);
        CompoundTag tag = new CompoundTag();
        CompoundTag data = new CompoundTag();
        data.putString("metal", metal);
        data.putString("gem", gem);
        if (inset) data.put("inset", new ItemStack(net.minecraft.world.item.Items.EMERALD).save(new CompoundTag()));
        tag.put(LegacyJewelcraft.LEGACY_TAG, data);
        CompoundTag display = new CompoundTag();
        display.putString("Name", "{\"text\":\"Grandmother's Ring\"}");
        tag.put("display", display);
        stack.put("tag", tag);
        return stack;
    }

    @GameTest(template = "empty")
    public static void oldIdsResolveToTheNewEntries(GameTestHelper h) {
        h.assertTrue(BuiltInRegistries.ITEM.get(new ResourceLocation("jewelcraft", "material_ring")) == ModItems.RING.get(), "material_ring is a ring");
        h.assertTrue(BuiltInRegistries.ITEM.get(new ResourceLocation("jewelcraft", "material_circlet")) == ModItems.CIRCLET.get(), "material_circlet is a circlet");
        h.assertTrue(BuiltInRegistries.BLOCK.get(new ResourceLocation("jewelcraft", "jeweler_bench")) == ModBlocks.JEWELERS_BENCH.get(), "the old bench is the new bench");
        h.assertTrue(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(new ResourceLocation("jewelcraft", "jeweler")) == ModVillagers.JEWELER.get(), "the old Jeweler is the new Jeweler");
        h.assertTrue(ForgeRegistries.POI_TYPES.getValue(new ResourceLocation("jewelcraft", "jeweler_bench")) == ModVillagers.JEWELERS_BENCH.get(), "and so is its workstation");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aLegacyPieceConvertsOnLoad(GameTestHelper h) {
        ItemStack stack = ItemStack.of(legacyStack("material_ring", "gold", "diamond", false));
        h.assertTrue(stack.is(ModItems.RING.get()), "The stack loads as a ring");
        JewelryData data = JewelryData.get(stack).orElse(null);
        h.assertTrue(data != null && data.legacyConverted(), "and is converted");
        h.assertTrue(data.metal().id().equals(new ResourceLocation("minecraft", "gold")) && data.principal().gem().equals(ImmersiveJewelry.id("diamond")),
                "keeping its gold and diamond");
        h.assertTrue(stack.getHoverName().getString().equals("Grandmother's Ring"), "and its custom name");
        h.assertTrue(!stack.getTag().contains(LegacyJewelcraft.LEGACY_TAG), "The legacy compound is moved aside");
        // Loading the converted stack again changes nothing.
        ItemStack again = ItemStack.of(stack.save(new CompoundTag()));
        h.assertTrue(JewelryData.get(again).orElseThrow().equals(data), "Conversion is idempotent");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theInsetDecidesTheGemAndIsKept(GameTestHelper h) {
        ItemStack stack = ItemStack.of(legacyStack("material_amulet", "iron", "unknown_gem", true));
        JewelryData data = JewelryData.get(stack).orElseThrow();
        h.assertTrue(stack.is(ModItems.AMULET.get()), "An amulet");
        h.assertTrue(data.principal().gem().equals(ImmersiveJewelry.id("emerald")), "whose emerald inset makes it an emerald piece");
        h.assertTrue(LegacyJewelcraft.legacyInset(stack).is(net.minecraft.world.item.Items.EMERALD), "and the inset is remembered");
        h.succeed();
    }
}
