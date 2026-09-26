package com.otectus.immersivejewelry;

import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Item data serialisation and validation (spec 22, 63): round trips, clamping, no raw keys outside the records. */
class DataTest {
    private static final ResourceLocation RUBY = new ResourceLocation("immersive_jewelry", "ruby");
    private static final ResourceLocation BRILLIANT = new ResourceLocation("immersive_jewelry", "brilliant");

    @Test
    void gemDataRoundTripsThroughNbtAndNetwork() {
        GemData g = new GemData(RUBY, 0.91F, 1.0F, 0.82F, 0.14F, BRILLIANT, 0.93F, GemDamage.SCRATCHED, 0.9F);
        assertEquals(g, GemData.load(g.save()).orElseThrow());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        g.write(buf);
        assertEquals(g, GemData.read(buf));
    }

    @Test
    void gemDataIsClamped() {
        GemData g = new GemData(RUBY, -3F, Float.NaN, 7F, -1F, BRILLIANT, 2F, null, 5F);
        assertEquals(GemData.MIN_SIZE, g.size());
        assertTrue(g.originalSize() >= g.size());
        assertEquals(1F, g.naturalQuality());
        assertEquals(0F, g.inclusions());
        assertEquals(1F, g.cutQuality());
        assertEquals(GemDamage.NONE, g.damage());
        GemData rough = GemData.rough(RUBY, 1F, 0.5F, 0.2F);
        assertFalse(rough.isCut());
        assertEquals(0F, rough.withCutQuality(0.9F).cutQuality(), "A rough stone has no cut quality");
    }

    @Test
    void cutQualityNeverExceedsTheStonesCap() {
        GemData g = GemData.rough(RUBY, 1F, 0.5F, 0.2F).withCut(BRILLIANT, 0.99F, 0.6F, GemDamage.NONE, 0.8F);
        assertEquals(0.8F, g.cutQuality(), 1e-6);
    }

    @Test
    void jewelryDataRoundTripsWithDefectsAndSmithingData() {
        CompoundTag smithing = new CompoundTag();
        smithing.putInt("ForgeScore", 88);
        GemData stone = GemData.rough(RUBY, 1F, 0.8F, 0.1F).withCut(BRILLIANT, 0.94F, 0.7F, GemDamage.NONE, 1F);
        JewelryData d = new JewelryData(new ResourceLocation("immersive_jewelry", "ring"), MetalRef.of(new ResourceLocation("minecraft", "gold")),
                List.of(stone, stone), new ResourceLocation("immersive_jewelry", "trilogy_ring"), new ResourceLocation("immersive_jewelry", "polished"),
                0.88F, 0.9F, 0.907F, 0.75F, 2, EnumSet.of(SettingDefect.LOOSE, SettingDefect.CROOKED), "Otectus", false, smithing);
        JewelryData back = JewelryData.load(d.save()).orElseThrow();
        assertEquals(d.form(), back.form());
        assertEquals(d.gems(), back.gems());
        assertEquals(d.defects(), back.defects());
        assertEquals(d.workmanship(), back.workmanship(), 1e-6);
        assertEquals(d.integrity(), back.integrity(), 1e-6);
        assertEquals(88, back.smithing().getInt("ForgeScore"));
        assertEquals(0.94F, back.cutQuality(), 1e-6);
        assertEquals("Otectus", back.maker());
    }

    @Test
    void removingStonesLeavesAWornerFrame() {
        JewelryData d = new JewelryData(new ResourceLocation("immersive_jewelry", "ring"), MetalRef.UNKNOWN, List.of(), null, null,
                0.7F, 0.8F, 0.75F, 0.9F, 1, EnumSet.noneOf(SettingDefect.class), null, false, null);
        BlankData blank = d.toBlank(0.1F);
        assertEquals(0.8F, blank.integrity(), 1e-6);
        assertEquals(2, blank.reworks());
        assertEquals(0.7F, blank.smithingQuality(), 1e-6);
        assertEquals(blank, BlankData.load(blank.save()));
    }

    @Test
    void jewelryDataRejectsOutOfRangeValues() {
        JewelryData d = new JewelryData(new ResourceLocation("immersive_jewelry", "ring"), null, null, null, null,
                9F, -2F, Float.NaN, 3F, -5, null, "x".repeat(300), false, null);
        assertEquals(1F, d.smithingQuality());
        assertEquals(0F, d.settingQuality());
        assertEquals(0F, d.workmanship());
        assertEquals(1F, d.integrity());
        assertEquals(0, d.reworks());
        assertEquals(64, d.maker().length());
        assertEquals(MetalRef.UNKNOWN, d.metal());
        assertNull(d.principal());
    }
}
