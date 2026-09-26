package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.effect.EffectCategory;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.integration.IJewelryEffectContributor;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.effect.JewelryWearers;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Effects another mod contributes to a worn piece ({@link IJewelryEffectContributor}): they follow the piece's
 * activity, keep their own power, and are re-applied when the data they come from changes.
 */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContributorGameTests {
    private static final String POWER_TAG = "ij_test_probe";
    private static final String VARIANT_TAG = "ij_test_probe_variant";
    private static final ResourceLocation GOLD = new ResourceLocation("minecraft", "gold");
    /** What each player's probe effect saw on equip: its power and variant. Removed on unequip. */
    private static final Map<UUID, Probe> APPLIED = new ConcurrentHashMap<>();
    private static boolean registered;

    private record Probe(float power, int variant) {}

    /** Contributes to pieces carrying the probe tag only, so other tests are unaffected. */
    private static void register() {
        if (registered) return;
        registered = true;
        ImmersiveJewelryAPI.registerEffectContributor((stack, data) -> {
            if (stack.getTag() == null || !stack.getTag().contains(POWER_TAG)) return List.of();
            return List.of(new IJewelryEffectContributor.ContributedEffect(new ResourceLocation("ij_test", "probe"), EffectCategory.UTILITY,
                    new ProbeEffect(stack.getTag().getInt(VARIANT_TAG)), stack.getTag().getFloat(POWER_TAG)));
        });
    }

    private static final class ProbeEffect extends JewelryEffect {
        private final int variant;

        ProbeEffect(int variant) {
            this.variant = variant;
        }

        @Override
        public Set<JewelryHook> hooks() {
            return EnumSet.noneOf(JewelryHook.class);
        }

        @Override
        public void describe(EffectDescription out) {}

        @Override
        public void onEquip(JewelryContext ctx) {
            APPLIED.put(ctx.player().getUUID(), new Probe(ctx.power(), variant));
        }

        @Override
        public void onUnequip(JewelryContext ctx) {
            APPLIED.remove(ctx.player().getUUID());
        }
    }

    private static ItemStack piece(String form, ResourceLocation gem, String cut) {
        GemData stone = TestSupport.cutStone(gem, ImmersiveJewelry.id(cut), 0.8F);
        return JewelryFactory.jewelry(ImmersiveJewelry.id(form), JewelryFactory.metal(JewelryDefinitions.server(), GOLD), List.of(stone), null,
                ImmersiveJewelry.id("polished"), 0.8F, 0.8F, null);
    }

    private static ItemStack probed(float power, int variant) {
        ItemStack bracelet = piece("bracelet", ImmersiveJewelry.id("quartz"), "round");
        bracelet.getOrCreateTag().putFloat(POWER_TAG, power);
        bracelet.getOrCreateTag().putInt(VARIANT_TAG, variant);
        return bracelet;
    }

    @GameTest(template = "empty")
    public static void contributedEffectsFollowThePieceAndKeepTheirPower(GameTestHelper h) {
        register();
        FakePlayer player = TestSupport.player(h);
        // A diamond circlet amplifies the other pieces' trait effects, never a contributed one.
        TestSupport.wear(player, probed(2F, 1), piece("circlet", TestSupport.DIAMOND, "brilliant"));
        JewelryWearers.refreshNow(player);
        Probe applied = APPLIED.get(player.getUUID());
        h.assertTrue(applied != null, "the contributed effect is equipped with its piece");
        h.assertTrue(applied.power() == 2F, "contributed power is used as given, got " + applied.power());
        TestSupport.wear(player);
        JewelryWearers.refreshNow(player);
        h.assertTrue(!APPLIED.containsKey(player.getUUID()), "and unequipped with it");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aBrokenPieceContributesNothing(GameTestHelper h) {
        register();
        FakePlayer player = TestSupport.player(h);
        ItemStack bracelet = probed(1F, 1);
        JewelryData.get(bracelet).orElseThrow().withIntegrity(0F).apply(bracelet);
        TestSupport.wear(player, bracelet);
        JewelryWearers.refreshNow(player);
        h.assertTrue(!APPLIED.containsKey(player.getUUID()), "a broken piece's contributed effects stay inactive");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void changedContributorDataReappliesThePiece(GameTestHelper h) {
        register();
        FakePlayer player = TestSupport.player(h);
        ItemStack bracelet = probed(1F, 1);
        TestSupport.wear(player, bracelet);
        JewelryWearers.refreshNow(player);
        h.assertTrue(APPLIED.get(player.getUUID()) != null && APPLIED.get(player.getUUID()).variant() == 1, "variant 1 applied");
        // Same jewelry composition and same power; only the other mod's data changed (a reforge).
        bracelet.getOrCreateTag().putInt(VARIANT_TAG, 2);
        JewelryWearers.refreshNow(player);
        Probe applied = APPLIED.get(player.getUUID());
        h.assertTrue(applied != null && applied.variant() == 2, "the new contribution replaces the old, got " + applied);
        TestSupport.cleanup(player);
        h.succeed();
    }
}
