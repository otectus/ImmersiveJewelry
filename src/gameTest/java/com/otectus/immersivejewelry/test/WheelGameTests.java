package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.event.GemCutEvent;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.quality.CutGrade;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.testing.FacetBot;
import com.otectus.immersivejewelry.workstation.WheelOperations;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static com.otectus.immersivejewelry.test.TestSupport.STATION;

/**
 * The Lapidary Wheel end to end, through the server's real operator path: walk-up mounting, the lease, control samples
 * paced on a test clock, discrete commands, finishing once, and the stone carrying its work everywhere it goes.
 */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WheelGameTests {

    /** What the scene shows under a carried stack (Diegesis takes_items nodes) is exactly where the wheel then puts it. */
    @GameTest(template = "empty")
    public static void placementPreviewAgreesWithTheWheel(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        try {
            expectPlacement(h, player, wheel, new ItemStack(Items.DIRT), WheelOperations.Placement.NONE);
            expectPlacement(h, player, wheel, new ItemStack(ModItems.ROUGH_GEM.get()), WheelOperations.Placement.NONE);
            expectPlacement(h, player, wheel, new ItemStack(Items.DIAMOND), WheelOperations.Placement.QUILL);
            expectPlacement(h, player, wheel, new ItemStack(Items.EMERALD), WheelOperations.Placement.NONE);
            expectPlacement(h, player, wheel, new ItemStack(ModItems.DIAMOND_LAP.get()), WheelOperations.Placement.LAP);
        } finally {
            TestSupport.cleanup(player);
        }
        h.succeed();
    }

    private static void expectPlacement(GameTestHelper h, FakePlayer player, LapidaryWheelBlockEntity wheel, ItemStack stack,
                                        WheelOperations.Placement expected) {
        WheelOperations.Placement preview = WheelOperations.placement(wheel, stack, JewelryDefinitions.server());
        ItemStack stone = wheel.stone().copy(), lap = wheel.lap().copy();
        player.getInventory().setItem(0, stack.copy());
        WheelOperations.placeFrom(player, wheel, 0);
        WheelOperations.Placement done = !ItemStack.matches(lap, wheel.lap()) ? WheelOperations.Placement.LAP
                : !ItemStack.matches(stone, wheel.stone()) ? WheelOperations.Placement.QUILL : WheelOperations.Placement.NONE;
        h.assertTrue(preview == expected && done == expected, stack + ": expected " + expected + ", preview " + preview + ", placed " + done);
        player.getInventory().setItem(0, ItemStack.EMPTY);
    }

    private static LapidaryWheelBlockEntity wheel(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.LAPIDARY_WHEEL.get());
        return (LapidaryWheelBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
    }

    @GameTest(template = "empty")
    public static void animationStaysForwardAcrossWrapAndCoastsToRest(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        wheel.angle = (float) (Math.PI * 2) - .1F;
        wheel.speed = .55F;
        wheel.setOperator(java.util.UUID.randomUUID(), "test");
        LapidaryWheelBlockEntity.clientTick(h.getLevel(), wheel.getBlockPos(), wheel.getBlockState(), wheel);
        h.assertTrue(wheel.angle < wheel.prevAngle, "fixture crosses the modulo boundary");
        float last = wheel.renderAngle(0);
        for (int i = 1; i <= 10; i++) {
            float next = wheel.renderAngle(i / 10F);
            h.assertTrue(next > last && next - last < .06F, "interpolated motion stays forward and smooth");
            last = next;
        }
        h.assertTrue(Math.abs(last - (float) (Math.PI * 2) - wheel.angle) < 1e-5F, "endpoint agrees modulo one revolution");
        wheel.setOperator(null, null);
        for (int i = 0; i < 200; i++) LapidaryWheelBlockEntity.clientTick(h.getLevel(), wheel.getBlockPos(), wheel.getBlockState(), wheel);
        h.assertTrue(wheel.speed < .002F, "idle wheel coasts down");
        h.assertTrue(wheel.getRenderBoundingBox().maxY >= wheel.getBlockPos().getY() + 19D / 16D, "the quill stays inside render bounds");
        h.succeed();
    }

    /** Walks up with a diamond (appraised once), takes the lease and plans {@code cut}. */
    private static TestSupport.Operator planned(GameTestHelper h, FakePlayer player, LapidaryWheelBlockEntity wheel, String cut) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        TestSupport.use(h, player);
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        op.command(0L, WorkCommand.PLAN_CUT, TestSupport.args("Cut", ImmersiveJewelry.id(cut)));
        return op;
    }

    /** The careful reference player, sample by sample through the real input path, reading the server's own state. */
    private static void grind(TestSupport.Operator op, LapidaryWheelBlockEntity wheel, FacetBot bot, int maxSteps) {
        for (int i = 0; i < maxSteps; i++) {
            CutWork work = wheel.work().orElseThrow();
            if (FacetBot.done(work.sim())) break;
            op.feed(work.id(), bot.next(work.sim()));
        }
        CutWork work = wheel.work().orElseThrow();
        for (int i = 0; i < 10; i++) op.feed(work.id(), new WorkSample(2048, 4095, 0, work.sim().region(), WorkSample.TRIM_BIAS, 0));
    }

    @GameTest(template = "empty")
    public static void mountingAppraisesOnceAndTheStoneKeepsItsIdentity(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
        TestSupport.use(h, player);
        h.assertTrue(wheel.stone().is(ModItems.ROUGH_GEM.get()), "a diamond becomes an appraised rough stone");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "exactly one diamond was used");
        GemData first = RoughGemItem.data(wheel.stone()).orElseThrow();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        TestSupport.use(h, player);
        player.setShiftKeyDown(false);
        h.assertFalse(wheel.hasStone(), "sneak-use takes the stone");
        ItemStack taken = player.getInventory().items.stream().filter(s -> s.is(ModItems.ROUGH_GEM.get())).findFirst().orElseThrow();
        player.setItemInHand(InteractionHand.MAIN_HAND, taken.split(1));
        TestSupport.use(h, player);
        h.assertTrue(first.equals(RoughGemItem.data(wheel.stone()).orElseThrow()), "remounting never re-rolls the stone");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aCarefulCutMakesExactlyOneCutStone(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        wheel.swapLap(new ItemStack(ModItems.EMERY_LAP.get()));
        FakePlayer player = TestSupport.player(h);
        AtomicInteger events = new AtomicInteger();
        java.util.function.Consumer<GemCutEvent> listener = e -> events.incrementAndGet();
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            TestSupport.Operator op = planned(h, player, wheel, "round");
            CutWork work = wheel.work().orElseThrow();
            h.assertTrue(!work.started(), "planning removes nothing");
            grind(op, wheel, new FacetBot(FacetBot.Style.CAREFUL, -Math.round(work.params().dopOffset())), 8000);
            h.assertTrue(wheel.work().orElseThrow().sim().ready(), "every face reached its window");
            h.assertTrue(wheel.lap().getDamageValue() == 1, "the lap paid one point of wear, once, at the first contact");
            long id = wheel.work().orElseThrow().id();
            op.command(id, WorkCommand.FINISH_CUT);
            op.command(id, WorkCommand.FINISH_CUT);
            h.assertTrue(wheel.stone().is(ModItems.CUT_GEM.get()), "the stone is cut");
            GemData cut = CutGemItem.data(wheel.stone()).orElseThrow();
            h.assertTrue(CutGrade.of(cut.cutQuality()).ordinal() >= CutGrade.FINE.ordinal(), "careful work makes a fine cut: " + cut.cutQuality());
            h.assertTrue(cut.cut().equals(ImmersiveJewelry.id("round")), "the planned cut");
            h.assertTrue(!CutWork.present(wheel.stone()), "a finished stone carries no work");
            h.assertTrue(events.get() == 1, "the cut event fired exactly once (a repeated finish does nothing): " + events.get());
            h.assertTrue(wheel.lap().getDamageValue() == 1, "finishing charges no more wear");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            TestSupport.cleanup(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silenceStaleOrForeignInputChangesNothing(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        TestSupport.Operator op = planned(h, player, wheel, "round");
        CutWork work = wheel.work().orElseThrow();
        WorkSample press = new WorkSample(2048, 0, 0, work.sim().region(), WorkSample.TRIM_BIAS, 0);
        // A wrong work id (a previous cut), a wrong token and a repeated batch number do nothing.
        op.feed(work.id() + 1, java.util.Collections.nCopies(30, press));
        h.assertTrue(wheel.work().orElseThrow().sim().steps() == 0, "a stale work id is ignored");
        var bad = new com.otectus.immersivejewelry.network.packet.WorkInputPacket(op.pos, op.lease.token + 1, work.id(), 999, 0,
                java.util.List.of(press, press, press));
        com.otectus.immersivejewelry.workstation.WorkInputService.input(player, bad, op.clock + 1000);
        h.assertTrue(wheel.work().orElseThrow().sim().steps() == 0, "a wrong lease token is ignored");
        // A burst far ahead of the clock gets at most the bounded backlog.
        var burst = new java.util.ArrayList<WorkSample>(java.util.Collections.nCopies(10, press));
        int before = wheel.work().orElseThrow().sim().steps();
        for (int i = 0; i < 40; i++) {
            com.otectus.immersivejewelry.workstation.WorkInputService.input(player, new com.otectus.immersivejewelry.network.packet.WorkInputPacket(
                    op.pos, op.lease.token, work.id(), 2000 + i, 5000 + i * 10, burst), op.clock);
        }
        int applied = wheel.work().orElseThrow().sim().steps() - before;
        h.assertTrue(applied <= 70, "input cannot outrun the clock: " + applied);
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void workTravelsWithTheStoneAndResumesExactly(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        TestSupport.Operator op = planned(h, player, wheel, "round");
        FacetBot bot = new FacetBot(FacetBot.Style.CAREFUL);
        grind(op, wheel, bot, 400);
        CutWork before = wheel.work().orElseThrow();
        h.assertTrue(before.started(), "material was removed");
        float depth = before.sim().depth(before.params().order()[0]);
        // Leaving ends the lease, never the work; the stone goes into the inventory with its work.
        WorkSessionManager.release(player.getUUID(), player.server, null, false);
        h.assertTrue(wheel.operator() == null, "no stale lock");
        player.setShiftKeyDown(true);
        TestSupport.use(h, player);
        player.setShiftKeyDown(false);
        ItemStack stone = player.getInventory().items.stream().filter(s -> s.is(ModItems.ROUGH_GEM.get())).findFirst().orElseThrow();
        h.assertTrue(CutWork.present(stone), "the stone carries its work");
        // Mounted on another wheel, the same work carries on.
        BlockPos other = STATION.east(2);
        h.setBlock(other, ModBlocks.LAPIDARY_WHEEL.get());
        LapidaryWheelBlockEntity second = (LapidaryWheelBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(other));
        player.setItemInHand(InteractionHand.MAIN_HAND, stone.split(1));
        player.setPos(h.absolutePos(other).getX() + 0.5, h.absolutePos(other).getY(), h.absolutePos(other).getZ() + 1.5);
        h.getLevel().getBlockState(h.absolutePos(other)).use(h.getLevel(), player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(other)), net.minecraft.core.Direction.SOUTH,
                        h.absolutePos(other), false));
        CutWork resumed = second.work().orElseThrow();
        h.assertTrue(resumed.id() == before.id(), "the same work");
        h.assertTrue(Math.abs(resumed.sim().depth(before.params().order()[0]) - depth) < 1e-6F, "with every bit of progress");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void changingThePlanAfterWorkIsARecutThatRestoresNothing(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        TestSupport.Operator op = planned(h, player, wheel, "round");
        GemData rough = RoughGemItem.data(wheel.stone()).orElseThrow();
        op.command(0L, WorkCommand.PLAN_CUT, TestSupport.args("Cut", ImmersiveJewelry.id("brilliant")));
        h.assertTrue(wheel.work().orElseThrow().cut().equals(ImmersiveJewelry.id("brilliant")), "before any work, the plan changes freely");
        grind(op, wheel, new FacetBot(FacetBot.Style.CAREFUL), 600);
        long id = wheel.work().orElseThrow().id();
        op.command(id, WorkCommand.PLAN_CUT, TestSupport.args("Cut", ImmersiveJewelry.id("emerald")));
        h.assertTrue(wheel.work().orElseThrow().id() == id, "after work, an unconfirmed change does nothing");
        op.command(id, WorkCommand.PLAN_CUT, TestSupport.args("Cut", ImmersiveJewelry.id("emerald"), "Confirm", true));
        CutWork recut = wheel.work().orElseThrow();
        GemData after = RoughGemItem.data(wheel.stone()).orElseThrow();
        h.assertTrue(recut.id() != id && recut.recut() && !recut.started(), "a new, untouched recut plan");
        h.assertTrue(after.size() < rough.size(), "the ground-away material stays gone: " + after.size() + " < " + rough.size());
        h.assertTrue(after.originalSize() == rough.originalSize() && after.naturalQuality() == rough.naturalQuality(), "the stone's identity is kept");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void finishingNeedsReadyFacesOrAnExplicitRoughResult(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        TestSupport.Operator op = planned(h, player, wheel, "round");
        grind(op, wheel, new FacetBot(FacetBot.Style.CAREFUL), 150);
        long id = wheel.work().orElseThrow().id();
        op.command(id, WorkCommand.FINISH_CUT);
        h.assertTrue(wheel.stone().is(ModItems.ROUGH_GEM.get()), "an unfinished cut is not finished by asking once");
        op.command(id, WorkCommand.FINISH_CUT, TestSupport.args("Rough", true));
        h.assertTrue(wheel.stone().is(ModItems.CUT_GEM.get()), "accepting a rougher result finishes it");
        h.assertTrue(CutGemItem.data(wheel.stone()).orElseThrow().cutQuality() < 0.5F, "and it is rough");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void damageLiftsTheQuillAndEndsTheLease(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        FakePlayer player = TestSupport.player(h);
        TestSupport.Operator op = planned(h, player, wheel, "round");
        CutWork work = wheel.work().orElseThrow();
        WorkSample press = new WorkSample(2048, 0, 0, work.sim().region(), WorkSample.TRIM_BIAS, 0);
        op.feed(work.id(), java.util.Collections.nCopies(20, press));
        h.assertTrue(wheel.work().orElseThrow().sim().contact(), "grinding");
        WorkSessionManager.onInterrupted(player);
        FacetSim sim = wheel.work().orElseThrow().sim();
        h.assertTrue(!sim.contact() && sim.height() >= 1F, "the quill is lifted at once");
        h.assertTrue(WorkSessionManager.lease(player).isEmpty() && wheel.operator() == null, "the lease ended");
        float depth = sim.depth(sim.region());
        op.feed(work.id(), java.util.Collections.nCopies(20, press));
        h.assertTrue(wheel.work().orElseThrow().sim().depth(sim.region()) == depth, "input after the lease ended changes nothing");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingTheWheelDropsTheStoneWithItsWorkOnce(GameTestHelper h) {
        LapidaryWheelBlockEntity wheel = wheel(h);
        wheel.swapLap(new ItemStack(ModItems.EMERY_LAP.get()));
        FakePlayer player = TestSupport.player(h);
        TestSupport.Operator op = planned(h, player, wheel, "round");
        CutWork work = wheel.work().orElseThrow();
        op.feed(work.id(), java.util.Collections.nCopies(60, new WorkSample(2048, 0, 0, work.sim().region(), WorkSample.TRIM_BIAS, 0)));
        h.destroyBlock(STATION);
        AABB box = new AABB(h.absolutePos(STATION)).inflate(2);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, box);
        long stones = drops.stream().filter(e -> e.getItem().is(ModItems.ROUGH_GEM.get())).count();
        long laps = drops.stream().filter(e -> e.getItem().is(ModItems.EMERY_LAP.get())).count();
        h.assertTrue(stones == 1 && laps == 1, "one stone and one lap: " + stones + ", " + laps);
        ItemStack dropped = drops.stream().filter(e -> e.getItem().is(ModItems.ROUGH_GEM.get())).findFirst().orElseThrow().getItem();
        h.assertTrue(CutWork.of(dropped).map(CutWork::started).orElse(false), "the dropped stone keeps its work");
        h.assertTrue(WorkSessionManager.lease(player).isEmpty(), "the lease ended with the station");
        TestSupport.cleanup(player);
        h.succeed();
    }
}
