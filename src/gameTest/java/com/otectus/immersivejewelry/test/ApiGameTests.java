package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.event.GemCutEvent;
import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.integration.IJewelryAssistSource;
import com.otectus.immersivejewelry.api.integration.IJewelryCraftGate;
import com.otectus.immersivejewelry.api.integration.IJewelryWearGate;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The 1.1.0 progression hooks: craft gates, wear gates, assistance and the settable cut event. */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApiGameTests {

    private static JewelryDesignRecipe design() {
        return new JewelryDesignRecipe(ImmersiveJewelry.id("test/api_ring"), ImmersiveJewelry.id("ring"), 1, SettingStyle.PRONG, 4,
                1F, 0F, List.of(), List.of(), List.of(), false, 0, Component.literal("API ring"), null);
    }

    @GameTest(template = "empty")
    public static void craftGatesFirstDenialWinsAndThrowingGatesAreSkipped(GameTestHelper h) {
        FakePlayer jeweler = TestSupport.player(h);
        JewelryDesignRecipe design = design();
        BlankData blank = new BlankData(MetalRef.UNKNOWN, 0.5F, 1F, 0, false, null);
        List<GemData> stones = List.of(GemData.rough(ImmersiveJewelry.id("amethyst"), 1F, 0.5F, 0.2F));
        IJewelryCraftGate broken = (p, d, b, s) -> { throw new IllegalStateException("broken gate"); };
        IJewelryCraftGate allow = (p, d, b, s) -> Optional.empty();
        IJewelryCraftGate first = (p, d, b, s) -> Optional.of(Component.literal("first"));
        IJewelryCraftGate second = (p, d, b, s) -> Optional.of(Component.literal("second"));
        try {
            h.assertTrue(ImmersiveJewelryAPI.craftDenial(jeweler, design, blank, stones).isEmpty(), "No gates: allowed");
            ImmersiveJewelryAPI.registerCraftGate(broken);
            ImmersiveJewelryAPI.registerCraftGate(allow);
            h.assertTrue(ImmersiveJewelryAPI.craftDenial(jeweler, design, blank, stones).isEmpty(), "A throwing gate is skipped, an allowing one allows");
            ImmersiveJewelryAPI.registerCraftGate(first);
            ImmersiveJewelryAPI.registerCraftGate(second);
            Optional<Component> reason = ImmersiveJewelryAPI.craftDenial(jeweler, design, blank, stones);
            h.assertTrue(reason.isPresent() && "first".equals(reason.get().getString()), "The first registered denial is the reason");
        } finally {
            for (IJewelryCraftGate g : List.of(broken, allow, first, second)) ImmersiveJewelryAPI.unregisterCraftGate(g);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wearGatesRefuseOnlyWhenOneSaysNo(GameTestHelper h) {
        FakePlayer wearer = TestSupport.player(h);
        ItemStack notJewelry = new ItemStack(Items.STICK);
        IJewelryWearGate broken = (p, s, d) -> { throw new IllegalStateException("broken gate"); };
        IJewelryWearGate no = (p, s, d) -> false;
        try {
            h.assertTrue(ImmersiveJewelryAPI.mayWear(wearer, notJewelry), "Non-jewelry is always allowed");
            ImmersiveJewelryAPI.registerWearGate(broken);
            h.assertTrue(ImmersiveJewelryAPI.mayWear(wearer, notJewelry), "A throwing gate allows");
            ImmersiveJewelryAPI.registerWearGate(no);
            h.assertTrue(ImmersiveJewelryAPI.mayWear(wearer, notJewelry), "Gates are only asked about jewelry");
            ItemStack ring = JewelryFactory.stack(new JewelryData(ImmersiveJewelry.id("ring"), MetalRef.UNKNOWN, List.of(), null, null,
                    0.5F, 0.5F, 0.5F, 1F, 0, Set.of(), null, false, null));
            h.assertTrue(!ImmersiveJewelryAPI.mayWear(wearer, ring), "A refusing gate makes a worn piece inert");
            ImmersiveJewelryAPI.unregisterWearGate(no);
            h.assertTrue(ImmersiveJewelryAPI.mayWear(wearer, ring), "Without the refusing gate the piece is allowed");
        } finally {
            ImmersiveJewelryAPI.unregisterWearGate(broken);
            ImmersiveJewelryAPI.unregisterWearGate(no);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void assistanceOnlyEasesAndStopsAtTheFloor(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        float floor = (float) (double) ServerConfig.get(ServerConfig.MIN_ASSIST_DIFFICULTY);
        IJewelryAssistSource harder = (p, k) -> 2.0;
        IJewelryAssistSource easier = (p, k) -> 0.8;
        IJewelryAssistSource trivial = (p, k) -> 0.01;
        IJewelryAssistSource broken = (p, k) -> { throw new IllegalStateException("broken source"); };
        try {
            h.assertTrue(ImmersiveJewelryAPI.assistDifficulty(player, IJewelryAssistSource.Kind.CUTTING) == 1F, "No sources: 1.0");
            ImmersiveJewelryAPI.registerAssistSource(harder);
            h.assertTrue(ImmersiveJewelryAPI.assistDifficulty(player, IJewelryAssistSource.Kind.CUTTING) == 1F, "Assistance never makes it harder");
            ImmersiveJewelryAPI.unregisterAssistSource(harder);
            ImmersiveJewelryAPI.registerAssistSource(easier);
            h.assertTrue(Math.abs(ImmersiveJewelryAPI.assistDifficulty(player, IJewelryAssistSource.Kind.SETTING) - 0.8F) < 1e-6, "0.8 eases by a fifth");
            ImmersiveJewelryAPI.registerAssistSource(trivial);
            ImmersiveJewelryAPI.registerAssistSource(broken);
            h.assertTrue(Math.abs(ImmersiveJewelryAPI.assistDifficulty(player, IJewelryAssistSource.Kind.SETTING) - floor) < 1e-6,
                    "The product stops at minAssistDifficulty and a throwing source counts as 1");
        } finally {
            for (IJewelryAssistSource s : List.of(harder, easier, trivial, broken)) ImmersiveJewelryAPI.unregisterAssistSource(s);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cutEventOnlyAcceptsTheSameStoneAtAnotherQuality(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        GemData rough = GemData.rough(ImmersiveJewelry.id("amethyst"), 1F, 0.5F, 0.2F);
        GemData cut = rough.withCut(ImmersiveJewelry.id("round"), 0.6F, 0.9F, GemDamage.NONE, 1F);
        GemCutEvent event = new GemCutEvent(player, rough, cut, BlockPos.ZERO);
        h.assertTrue(event.setCutQuality(0.75F) && event.getAfter() != null && Math.abs(event.getAfter().cutQuality() - 0.75F) < 1e-6,
                "The same stone at another quality is accepted");
        h.assertTrue(!event.setAfter(cut.withSize(0.5F)), "A different size is refused");
        h.assertTrue(!event.setAfter(cut.withDamage(GemDamage.CHIPPED)), "Different damage is refused");
        h.assertTrue(!event.setAfter(rough), "An uncut stone is refused");
        h.assertTrue(Math.abs(event.getAfter().cutQuality() - 0.75F) < 1e-6, "Refused replacements leave the result alone");
        GemCutEvent shattered = new GemCutEvent(player, rough, null, BlockPos.ZERO);
        h.assertTrue(!shattered.setAfter(cut), "A shattered stone stays shattered");
        shattered.addExtraDust(3);
        shattered.addExtraDust(100);
        h.assertTrue(shattered.getExtraDust() == 62, "Extra dust is clamped to fit a stack with the wheel's own");
        h.succeed();
    }
}
