package com.otectus.immersivejewelry;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.migration.LegacyJewelcraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Jewelcraft Reforged conversion mapping (spec 50.2). */
class MigrationTest {

    @Test
    void legacyPiecesGetSensibleDefaults() {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("metal", "gold");
        legacy.putString("gem", "diamond");
        JewelryData d = LegacyJewelcraft.convert(new ResourceLocation("immersive_jewelry", "ring"), legacy);
        assertEquals(new ResourceLocation("minecraft", "gold"), d.metal().id());
        assertEquals(new ResourceLocation("immersive_jewelry", "diamond"), d.principal().gem());
        assertEquals(new ResourceLocation("immersive_jewelry", "round"), d.principal().cut());
        assertEquals(LegacyJewelcraft.DEFAULT_QUALITY, d.smithingQuality(), 1e-6);
        assertEquals(LegacyJewelcraft.DEFAULT_QUALITY, d.settingQuality(), 1e-6);
        assertEquals(LegacyJewelcraft.DEFAULT_QUALITY, d.workmanship(), 1e-4);
        assertTrue(d.legacyConverted());
        assertEquals(1F, d.integrity());
    }

    @Test
    void legacyMetalsMapToSmithingFamilies() {
        assertEquals(new ResourceLocation("minecraft", "netherite"), LegacyJewelcraft.metalId("netherite"));
        assertEquals(new ResourceLocation("cataclysm", "ignitium"), LegacyJewelcraft.metalId("ignitium"));
        assertEquals(new ResourceLocation("forge", "manasteel"), LegacyJewelcraft.metalId("manasteel"));
        assertEquals(new ResourceLocation("forge", "rose_gold"), LegacyJewelcraft.metalId("rose_gold"));
    }

    @Test
    void unknownLegacyGemsKeepTheirName() {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("metal", "iron");
        legacy.putString("gem", "void_crystal");
        assertEquals(new ResourceLocation("forge", "void_crystal"), LegacyJewelcraft.gemId(legacy));
    }
}
