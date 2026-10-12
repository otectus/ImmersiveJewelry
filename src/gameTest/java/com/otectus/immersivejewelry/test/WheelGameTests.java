package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.minigame.SessionManager;
import com.otectus.immersivejewelry.minigame.cutting.CuttingSession;
import com.otectus.immersivejewelry.network.packet.CutInputPacket;
import com.otectus.immersivejewelry.network.packet.SelectCutPacket;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.testing.CutBot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.otectus.immersivejewelry.test.TestSupport.STATION;

/** The Lapidary Wheel end to end, through the server's session code (spec 10, 34, 61-63). */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WheelGameTests {

    private static LapidaryWheelBlockEntity wheel(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LAPIDARY_WHEEL.get());
        return (LapidaryWheelBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
    }

    @GameTest(template = "empty")
    public static void animationStaysForwardAcrossWrapAndCoastsToRest(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        wheel.angle = (float) (Math.PI * 2) - .1F;
        wheel.speed = .55F;
        wheel.lock(java.util.UUID.randomUUID());
        LapidaryWheelBlockEntity.clientTick(h.getLevel(), wheel.getBlockPos(), wheel.getBlockState(), wheel);
        h.assertTrue(wheel.angle < wheel.prevAngle, "fixture crosses the modulo boundary");
        float last = wheel.renderAngle(0);
        for (int i = 1; i <= 10; i++) {
            float next = wheel.renderAngle(i / 10F);
            h.assertTrue(next > last && next - last < .06F, "interpolated motion stays forward and smooth");
            last = next;
        }
        h.assertTrue(Math.abs(last - (float) (Math.PI * 2) - wheel.angle) < 1e-5F, "endpoint agrees modulo one revolution");
        wheel.unlock();
        for (int i = 0; i < 200; i++) LapidaryWheelBlockEntity.clientTick(h.getLevel(), wheel.getBlockPos(), wheel.getBlockState(), wheel);
        h.assertTrue(wheel.speed < .002F, "idle wheel coasts down");
        h.assertTrue(wheel.getRenderBoundingBox().maxY >= wheel.getBlockPos().getY() + 19D / 16D, "rotating corners stay inside render bounds");
        h.succeed();
    }

    /** Mounts a diamond and starts a round cut; returns the session. */
    private static CuttingSession startCut(GameTestHelper h, FakePlayer player, LapidaryWheelBlockEntity wheel, String cut) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        TestSupport.use(h, player);
        SessionManager.selectCut(player, new SelectCutPacket(wheel.getBlockPos(), ImmersiveJewelry.id(cut), false, false));
        return (CuttingSession) SessionManager.byPlayer(player.getUUID()).orElseThrow();
    }

    /** Plays the bot through the server in batches of five steps, like the client does. */
    private static CutBot play(FakePlayer player, CuttingSession session, boolean careless) {
        CutBot bot = new CutBot(session.sim.params(), careless);
        int step = 0;
        while (!session.isComplete() && step < session.maxSteps()) {
            int n = Math.min(5, session.maxSteps() - step);
            short[] xs = new short[n];
            short[] ys = new short[n];
            long bits = 0;
            for (int i = 0; i < n; i++) {
                int[] in = bot.next();
                xs[i] = (short) in[0];
                ys[i] = (short) in[1];
                if (in[2] == 1) bits |= 1L << i;
            }
            SessionManager.cutInput(player, new CutInputPacket(session.id, step, xs, ys, bits), TestSupport.arrival(session, step + n));
            step += n;
        }
        return bot;
    }

    @GameTest(template = "empty")
    public static void aDiamondIsAppraisedOnceAndForAll(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
        TestSupport.use(h, player);
        h.assertTrue(player.getMainHandItem().getCount() == 1, "One diamond goes on the wheel");
        GemData appraised = RoughGemItem.data(wheel.stone()).orElseThrow();
        h.assertTrue(appraised.gem().equals(TestSupport.DIAMOND) && !appraised.isCut(), "It is appraised as a rough diamond");
        // Taking it back and remounting keeps the same character: no re-rolling.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        TestSupport.use(h, player);
        player.setShiftKeyDown(false);
        ItemStack rough = player.getInventory().items.stream().filter(s -> s.is(ModItems.ROUGH_GEM.get())).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue(!rough.isEmpty() && !wheel.hasStone(), "The rough stone comes back");
        h.assertTrue(RoughGemItem.data(rough).orElseThrow().equals(appraised), "unchanged");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aCarefulCutMakesAFineStone(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        CuttingSession session = startCut(h, player, wheel, "round");
        h.assertTrue(wheel.isLocked(), "The wheel is locked while cutting");
        GemData before = session.stone;
        CutBot bot = play(player, session, false);
        h.assertTrue(SessionManager.byPlayer(player.getUUID()).isEmpty(), "The session is over");
        GemData after = CutGemItem.data(wheel.stone()).orElse(null);
        h.assertTrue(after != null, "The wheel now holds a cut stone");
        h.assertTrue(after.cut().equals(ImmersiveJewelry.id("round")), "cut round");
        h.assertTrue(Math.abs(after.cutQuality() - bot.sim().quality()) < 1e-6, "with the quality the client's simulation showed: "
                + after.cutQuality() + " vs " + bot.sim().quality());
        h.assertTrue(after.cutQuality() >= 0.65F, "and careful work shows, got " + after.cutQuality());
        h.assertTrue(after.size() < before.size() && after.naturalQuality() == before.naturalQuality(), "Cutting costs size, not character");
        h.assertTrue(!wheel.isLocked(), "The wheel is free again");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aCarelessCutDegradesButKeepsTheStone(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        CuttingSession session = startCut(h, player, wheel, "brilliant");
        play(player, session, true);
        GemData after = CutGemItem.data(wheel.stone()).orElse(null);
        h.assertTrue(after != null, "The stone survives a poor cut (catastrophic failure is off by default)");
        h.assertTrue(after.cutQuality() < 0.4F, "but the cut is poor, got " + after.cutQuality());
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cancellingChangesNothing(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        CuttingSession session = startCut(h, player, wheel, "round");
        ItemStack before = wheel.stone().copy();
        SessionManager.cancelFromClient(player, session.id);
        h.assertTrue(ItemStack.matches(before, wheel.stone()), "The stone stays on the wheel exactly as it was");
        h.assertTrue(!wheel.isLocked(), "and the wheel is free");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void replayedAndEarlyInputIsIgnored(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        CuttingSession session = startCut(h, player, wheel, "round");
        short[] xs = new short[5];
        short[] ys = new short[5];
        SessionManager.cutInput(player, new CutInputPacket(session.id, 0, xs, ys, 0b11111), TestSupport.arrival(session, 5));
        h.assertTrue(session.nextStep() == 5, "Five steps accepted");
        SessionManager.cutInput(player, new CutInputPacket(session.id, 0, xs, ys, 0b11111), TestSupport.arrival(session, 10));
        h.assertTrue(session.nextStep() == 5, "A replayed batch is ignored");
        // 40 steps at once, arriving 200 ms into the session: only what real time (plus tolerance) allows is accepted.
        short[] many = new short[40];
        SessionManager.cutInput(player, new CutInputPacket(session.id, 5, many, many, 0L), session.startMs() + 200);
        h.assertTrue(session.nextStep() < 5 + 40 && session.nextStep() <= 5 + (200 + 120) / 20 + 1,
                "Steps ahead of real time are refused (no speed hacks), reached " + session.nextStep());
        int reached = session.nextStep();
        SessionManager.cutInput(player, new CutInputPacket(session.id + 1, reached, xs, ys, 0), TestSupport.arrival(session, reached + 5));
        h.assertTrue(session.nextStep() == reached, "Another session's id is ignored");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingTheWheelMidCutDropsTheStoneOnce(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        startCut(h, player, wheel, "round");
        BlockPos pos = h.absolutePos(STATION);
        h.getLevel().destroyBlock(pos, true);
        h.assertTrue(SessionManager.byPlayer(player.getUUID()).isEmpty(), "The session ends with the station");
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        long stones = drops.stream().filter(e -> e.getItem().is(ModItems.ROUGH_GEM.get())).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(stones == 1, "Exactly one stone drops, got " + stones);
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recuttingClearsDamageAtACostInSize(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        GemData chipped = TestSupport.cutStone(TestSupport.RUBY, ImmersiveJewelry.id("brilliant"), 0.5F)
                .withDamage(com.otectus.immersivejewelry.api.gem.GemDamage.CHIPPED);
        player.setItemInHand(InteractionHand.MAIN_HAND, CutGemItem.create(chipped));
        TestSupport.use(h, player);
        SessionManager.selectCut(player, new SelectCutPacket(wheel.getBlockPos(), ImmersiveJewelry.id("round"), false, false));
        CuttingSession session = (CuttingSession) SessionManager.byPlayer(player.getUUID()).orElseThrow();
        h.assertTrue(session.recut, "A cut stone on the wheel is recut");
        play(player, session, false);
        GemData after = CutGemItem.data(wheel.stone()).orElseThrow();
        h.assertTrue(after.damage() == com.otectus.immersivejewelry.api.gem.GemDamage.NONE, "Recutting grinds the chip away");
        h.assertTrue(after.size() < chipped.size(), "and the stone is smaller");
        TestSupport.cleanup(player);
        h.succeed();
    }
}
