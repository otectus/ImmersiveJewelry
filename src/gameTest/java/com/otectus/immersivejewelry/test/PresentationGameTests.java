package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.client.presentation.anim.JewelerClip;
import com.otectus.immersivejewelry.client.presentation.anim.PoseRules;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.network.packet.WornAppearancePacket;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import software.bernie.geckolib.animatable.GeoBlockEntity;

import java.util.List;
import java.util.Set;

/**
 * The 1.2.2 presentation contracts that a server can check: each station is its own GeckoLib animatable (one rig instance
 * per station), the worn-appearance snapshot carries the look and nothing private, and the body-pose rules read a real
 * station's synced state (the same answer every watcher's client computes).
 */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PresentationGameTests {

    @GameTest(template = "empty")
    public static void eachStationIsItsOwnRigInstance(GameTestHelper h) {
        h.setBlock(TestSupport.STATION, ModBlocks.LAPIDARY_WHEEL.get());
        h.setBlock(TestSupport.STATION.east(2), ModBlocks.LAPIDARY_WHEEL.get());
        var a = h.getBlockEntity(TestSupport.STATION);
        var b = h.getBlockEntity(TestSupport.STATION.east(2));
        h.assertTrue(a instanceof GeoBlockEntity && b instanceof GeoBlockEntity, "Stations are GeckoLib animatables");
        h.assertTrue(((GeoBlockEntity) a).getAnimatableInstanceCache() != ((GeoBlockEntity) b).getAnimatableInstanceCache(),
                "Two stations never share one animation cache");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wornSnapshotCarriesTheLookAndNothingPrivate(GameTestHelper h) {
        GemData ruby = TestSupport.cutStone(TestSupport.RUBY, ImmersiveJewelry.id("round"), 0.8F);
        ItemStack ring = JewelryFactory.jewelry(ImmersiveJewelry.id("ring"), JewelryFactory.metal(com.otectus.immersivejewelry.data.JewelryDefinitions.server(),
                new net.minecraft.resources.ResourceLocation("minecraft", "gold")), List.of(ruby), null, ImmersiveJewelry.id("polished"), 0.7F, 0.8F, "Secret Maker");
        JewelryData data = JewelryData.get(ring).orElseThrow();
        new JewelryData(data.form(), data.metal(), data.gems(), data.design(), data.finish(), data.smithingQuality(), data.settingQuality(),
                data.workmanship(), 0.6F, 3, Set.of(SettingDefect.LOOSE), "Secret Maker", false, null).apply(ring);
        ring.getOrCreateTag().putString("ForeignAffix", "private");
        ItemStack look = WornAppearancePacket.sanitize(ring);
        JewelryData shown = JewelryData.get(look).orElseThrow();
        h.assertTrue(look.is(ModItems.RING.get()), "The same item");
        h.assertTrue(shown.gems().equals(data.gems()) && shown.metal().equals(data.metal()) && ImmersiveJewelry.id("polished").equals(shown.finish()),
                "The look: stones, metal and finish");
        h.assertTrue(shown.maker() == null && shown.defects().isEmpty() && shown.reworks() == 0, "No maker, defects or history");
        h.assertTrue(!look.getOrCreateTag().contains("ForeignAffix"), "No foreign data");
        h.assertTrue(WornAppearancePacket.sanitize(new ItemStack(ModItems.ROUGH_GEM.get())).isEmpty(), "Only jewelry is drawn");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bodyPosesReadTheStationsSyncedState(GameTestHelper h) {
        h.setBlock(TestSupport.STATION, ModBlocks.LAPIDARY_WHEEL.get());
        LapidaryWheelBlockEntity wheel = (LapidaryWheelBlockEntity) h.getBlockEntity(TestSupport.STATION);
        PoseRules.Snapshot empty = PoseRules.of(wheel);
        h.assertTrue(PoseRules.held(empty) == JewelerClip.WHEEL_READY, "An empty wheel: ready");
        wheel.setStone(RoughGemItem.create(GemData.rough(TestSupport.DIAMOND, 1F, 0.6F, 0.2F)));
        PoseRules.Snapshot mounted = PoseRules.of(wheel);
        h.assertTrue(PoseRules.change(empty, mounted) == JewelerClip.WHEEL_MOUNT, "Mounting a stone is a mount");
        h.assertTrue(PoseRules.change(null, mounted) == null, "A late watcher's first sight replays nothing");
        h.setBlock(TestSupport.STATION.east(2), ModBlocks.JEWELERS_BENCH.get());
        JewelersBenchBlockEntity bench = (JewelersBenchBlockEntity) h.getBlockEntity(TestSupport.STATION.east(2));
        PoseRules.Snapshot bare = PoseRules.of(bench);
        bench.setClamp(new ItemStack(ModItems.RING_BLANK.get()));
        h.assertTrue(PoseRules.change(bare, PoseRules.of(bench)) == JewelerClip.BENCH_PLACE, "A frame in the clamp is a place");
        h.succeed();
    }
}
