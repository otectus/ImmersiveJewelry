package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.effect.JewelryWearers;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.jewelry.JewelryResolver;
import com.otectus.immersivejewelry.jewelry.ResolvedJewelry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Worn jewelry (spec 15-19, 31-32, 40): behaviour resolved from construction, applied on equip, removed on unequip,
 * capped per form, amplified by a prism circlet, and inert when broken. Worn through the equipment-provider API.
 */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EffectGameTests {
    private static final ResourceLocation GOLD = new ResourceLocation("minecraft", "gold");

    private static ItemStack piece(String form, ResourceLocation gem, String cut, float quality) {
        GemData stone = TestSupport.cutStone(gem, ImmersiveJewelry.id(cut), quality);
        return JewelryFactory.jewelry(ImmersiveJewelry.id(form), JewelryFactory.metal(JewelryDefinitions.server(), GOLD), List.of(stone), null,
                ImmersiveJewelry.id("polished"), quality, quality, null);
    }

    private static double extraReach(FakePlayer player) {
        return player.getAttributeValue(ForgeMod.BLOCK_REACH.get()) - player.getAttribute(ForgeMod.BLOCK_REACH.get()).getBaseValue();
    }

    @GameTest(template = "empty")
    public static void attributesComeAndGoWithThePiece(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        ItemStack bracelet = piece("bracelet", ImmersiveJewelry.id("quartz"), "round", 0.8F);
        TestSupport.wear(player, bracelet);
        JewelryWearers.refreshNow(player);
        double reach = extraReach(player);
        h.assertTrue(reach > 0.3, "A quartz bracelet lengthens reach (Mason's Reach), got " + reach);
        TestSupport.wear(player);
        JewelryWearers.refreshNow(player);
        h.assertTrue(Math.abs(extraReach(player)) < 1e-6, "and the bonus leaves with it");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void betterWorkIsStronger(GameTestHelper h) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        float poor = JewelryResolver.resolve(JewelryData.get(piece("ring", TestSupport.RUBY, "round", 0.3F)).orElseThrow(), defs).basePower();
        float fine = JewelryResolver.resolve(JewelryData.get(piece("ring", TestSupport.RUBY, "round", 0.9F)).orElseThrow(), defs).basePower();
        float brilliant = JewelryResolver.resolve(JewelryData.get(piece("ring", TestSupport.RUBY, "brilliant", 0.9F)).orElseThrow(), defs).basePower();
        h.assertTrue(fine > poor * 1.3F, "Fine work is clearly stronger: " + poor + " -> " + fine);
        h.assertTrue(brilliant > fine, "A brilliant cut expresses the trait more strongly: " + fine + " -> " + brilliant);
        h.assertTrue(fine > 0.8F && fine < 1.6F, "Good pieces sit near power 1, got " + fine);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theSameGemBehavesDifferentlyPerForm(GameTestHelper h) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        java.util.Set<ResourceLocation> types = new java.util.HashSet<>();
        for (String form : List.of("ring", "amulet", "bracelet", "circlet")) {
            ResolvedJewelry r = JewelryResolver.resolve(JewelryData.get(piece(form, TestSupport.RUBY, "round", 0.8F)).orElseThrow(), defs);
            h.assertTrue(r.isActive(), "A ruby " + form + " does something");
            types.add(r.expressions().get(0).definition().type());
        }
        h.assertTrue(types.size() == 4, "Ruby is expressed four different ways, got " + types);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyTwoRingsAreActive(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        TestSupport.wear(player, piece("ring", TestSupport.RUBY, "round", 0.8F), piece("ring", TestSupport.RUBY, "round", 0.8F),
                piece("ring", TestSupport.RUBY, "round", 0.8F));
        JewelryWearers.refreshNow(player);
        h.assertTrue(JewelryWearers.activePieces(player) == 2, "The third ring is inert (maxRingSlots = 2), got " + JewelryWearers.activePieces(player));
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aPrismCircletAmplifiesTheOthers(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        ItemStack bracelet = piece("bracelet", ImmersiveJewelry.id("quartz"), "round", 0.8F);
        TestSupport.wear(player, bracelet);
        JewelryWearers.refreshNow(player);
        double alone = extraReach(player);
        TestSupport.wear(player, bracelet, piece("circlet", TestSupport.DIAMOND, "brilliant", 0.8F));
        JewelryWearers.refreshNow(player);
        double amplified = extraReach(player);
        h.assertTrue(amplified > alone * 1.05, "A diamond circlet strengthens the bracelet: " + alone + " -> " + amplified);
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aBrokenPieceIsInertNotDeleted(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        ItemStack amulet = piece("amulet", ImmersiveJewelry.id("amethyst"), "cabochon", 0.8F);
        JewelryData data = JewelryData.get(amulet).orElseThrow();
        data.withIntegrity(0F).apply(amulet);
        TestSupport.wear(player, amulet);
        JewelryWearers.refreshNow(player);
        h.assertTrue(JewelryWearers.activePieces(player) == 0, "A broken piece does nothing");
        h.assertTrue(!amulet.isEmpty() && JewelryData.get(amulet).isPresent(), "but it is still there to repair");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void onlySevereHitsWearJewelry(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        ItemStack amulet = piece("amulet", ImmersiveJewelry.id("onyx"), "cabochon", 0.8F);
        TestSupport.wear(player, amulet);
        com.otectus.immersivejewelry.effect.JewelryWear.onDamage(player, 2F);
        h.assertTrue(JewelryData.get(amulet).orElseThrow().integrity() == 1F, "An ordinary hit leaves it untouched");
        com.otectus.immersivejewelry.effect.JewelryWear.onDamage(player, 12F);
        float after = JewelryData.get(amulet).orElseThrow().integrity();
        h.assertTrue(after < 1F && after > 0.9F, "A severe hit wears it a little, got " + after);
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silverFramesWardAgainstTheUndead(GameTestHelper h) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        GemData stone = TestSupport.cutStone(TestSupport.RUBY, ImmersiveJewelry.id("round"), 0.8F);
        ItemStack silver = JewelryFactory.jewelry(ImmersiveJewelry.id("ring"), MetalRef.of(new ResourceLocation("forge", "silver")), List.of(stone), null, null, 0.8F, 0.8F, null);
        ResolvedJewelry r = JewelryResolver.resolve(JewelryData.get(silver).orElseThrow(), defs);
        h.assertTrue(r.expressions().stream().anyMatch(ResolvedJewelry.Expression::fromMetal), "Silver adds its own small effect");
        ItemStack gold = JewelryFactory.jewelry(ImmersiveJewelry.id("ring"), MetalRef.of(GOLD), List.of(stone), null, null, 0.8F, 0.8F, null);
        h.assertTrue(JewelryResolver.resolve(JewelryData.get(gold).orElseThrow(), defs).expressions().stream().noneMatch(ResolvedJewelry.Expression::fromMetal),
                "Gold does not");
        h.assertTrue(Attributes.MAX_HEALTH != null, "vanilla attributes present");
        h.succeed();
    }
}
