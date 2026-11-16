package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.event.JewelryCraftedEvent;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.integration.IJewelryCraftGate;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.UnfinishedJewelryItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.testing.SettingBot;
import com.otectus.immersivejewelry.testing.SurfaceBot;
import com.otectus.immersivejewelry.workstation.BenchOperations;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.otectus.immersivejewelry.test.TestSupport.DIAMOND;
import static com.otectus.immersivejewelry.test.TestSupport.RUBY;
import static com.otectus.immersivejewelry.test.TestSupport.STATION;

/**
 * The Jeweler's Bench end to end through the real paths: walk-up custody, the lease, commands, control samples on a test
 * clock, commit at the first closure, finishing, completion exactly once, packing, breaking, reworks and migration.
 */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BenchGameTests {
    static final ResourceLocation POLISHED = ImmersiveJewelry.id("polished");
    static final ResourceLocation BURNISHED = ImmersiveJewelry.id("burnished");

    static JewelersBenchBlockEntity bench(GameTestHelper h, BlockPos at) {
        h.setBlock(at, ModBlocks.JEWELERS_BENCH.get());
        return (JewelersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(at));
    }

    static ItemStack goldRingBlank() {
        return JewelryFactory.blank(ModItems.RING_BLANK.get(), MetalRef.of(new ResourceLocation("minecraft", "gold")), 0.9F);
    }

    static ItemStack stone(ResourceLocation gem, String cut, float quality) {
        return CutGemItem.create(TestSupport.cutStone(gem, ImmersiveJewelry.id(cut), quality));
    }

    /** Puts {@code stack} on the bench through the walk-up use path. */
    static void give(GameTestHelper h, FakePlayer player, BlockPos at, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = h.absolutePos(at);
        h.getLevel().getBlockState(pos).use(h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** A blank, pliers, a stone and compound on the bench; the design and finish chosen; the stone seated. */
    static TestSupport.Operator solitaire(GameTestHelper h, FakePlayer player, JewelersBenchBlockEntity bench) {
        give(h, player, STATION, goldRingBlank());
        give(h, player, STATION, new ItemStack(ModItems.JEWELERS_PLIERS.get()));
        give(h, player, STATION, stone(DIAMOND, "round", 0.9F));
        give(h, player, STATION, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 2));
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        op.command(0L, WorkCommand.SELECT_DESIGN, TestSupport.args("Design", ImmersiveJewelry.id("designs/solitaire_ring")));
        long id = bench.work().id();
        op.command(id, WorkCommand.SELECT_FINISH, TestSupport.args("Finish", POLISHED));
        op.command(id, WorkCommand.SEAT, TestSupport.args("Tray", 0, "Socket", 0, "Dx", 0.1F, "Dy", -0.05F, "Turn", 0F));
        return op;
    }

    /** Closes every point with the careful reference player through the input path, then lets go. */
    static void set(TestSupport.Operator op, JewelersBenchBlockEntity bench, SettingBot bot, int maxSteps) {
        for (int i = 0; i < maxSteps; i++) {
            BenchWork work = bench.work();
            if (work == null || work.setting() == null || work.stage() == BenchWork.Stage.FINISHING) break;
            if (work.committed() && SettingBot.even(work.setting())) break;
            op.feed(work.id(), bot.next(work.setting()));
        }
        letGo(op, bench);
    }

    /** Lifts every tool off the work (as a player does before packing or moving on). */
    static void letGo(TestSupport.Operator op, JewelersBenchBlockEntity bench) {
        BenchWork work = bench.work();
        if (work == null) return;
        for (int i = 0; i < 3; i++) op.feed(work.id(), new com.otectus.immersivejewelry.workstation.sim.WorkSample(0, 0, 0, 0, 0, 0));
    }

    /** Sets every stone and declares the setting done (on to the finish). */
    static void setAndConfirm(TestSupport.Operator op, JewelersBenchBlockEntity bench, SettingBot bot, int maxSteps) {
        set(op, bench, bot, maxSteps);
        if (bench.work() != null) op.command(bench.work().id(), WorkCommand.FINISH_WORK);
    }

    /** Finishes (or repairs) the surface with the reference player. */
    static void finish(TestSupport.Operator op, JewelersBenchBlockEntity bench, int maxSteps) {
        SurfaceBot bot = new SurfaceBot();
        for (int i = 0; i < maxSteps; i++) {
            BenchWork work = bench.work();
            if (work == null || work.surface() == null) return;
            op.feed(work.id(), bot.next(work.surface()));
        }
        letGo(op, bench);
    }

    /**
     * What the scene shows under a carried stack (Diegesis takes_items nodes) is exactly what the custody move then does:
     * wrong targets, occupied and full positions, merging, and an unspecified tray position.
     */
    @GameTest(template = "empty")
    public static void placementPreviewAgreesWithCustody(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        try {
            ItemStack diamond = stone(DIAMOND, "round", 0.9F);
            ItemStack ruby = stone(RUBY, "round", 0.8F);
            ItemStack pliers = new ItemStack(ModItems.JEWELERS_PLIERS.get());
            ItemStack compound = new ItemStack(ModItems.POLISHING_COMPOUND.get(), 2);
            ItemStack gunpowder = new ItemStack(Items.GUNPOWDER);
            ItemStack dirt = new ItemStack(Items.DIRT);
            String clamp = BenchOperations.CLAMP, tray = BenchOperations.TRAY, supply = BenchOperations.SUPPLY, dock = BenchOperations.DOCK;
            Object[][] cases = {
                    {dirt, clamp, 0}, {dirt, tray, 0}, {dirt, supply, 0}, {dirt, dock, 0},
                    {diamond, clamp, 0}, {pliers, tray, 1}, {compound, tray, 2}, {goldRingBlank(), supply, 0},
                    {goldRingBlank(), clamp, 0}, {goldRingBlank(), clamp, 0},
                    {diamond, tray, 0}, {ruby, tray, 0}, {ruby, tray, 1}, {diamond, tray, -1},
                    {compound, supply, 0}, {compound, supply, 0}, {gunpowder, supply, 0}, {gunpowder, supply, 1},
                    {pliers, dock, 0}, {pliers, dock, 0},
            };
            int placed = 0;
            for (Object[] c : cases) {
                ItemStack stack = ((ItemStack) c[0]).copy();
                String target = (String) c[1];
                int index = (Integer) c[2];
                boolean preview = BenchOperations.accepts(bench, stack, target, index);
                boolean moved = BenchOperations.place(player, bench, stack, target, index, true);
                h.assertTrue(preview == moved, "preview " + preview + ", custody " + moved + ": " + c[0] + " at " + target + " " + index);
                if (moved) placed++;
            }
            h.assertTrue(placed == 8, "eight of the cases place something, got " + placed);
        } finally {
            TestSupport.cleanup(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aSolitaireRingFromStoneToWearablePiece(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        AtomicInteger crafted = new AtomicInteger();
        java.util.function.Consumer<JewelryCraftedEvent> listener = e -> crafted.incrementAndGet();
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            TestSupport.Operator op = solitaire(h, player, bench);
            h.assertTrue(bench.socket(0).is(ModItems.CUT_GEM.get()) && bench.tray(0).isEmpty(), "the stone rests in its cup");
            h.assertFalse(bench.work().committed(), "seating commits nothing");
            set(op, bench, new SettingBot(SettingBot.Style.CAREFUL), 4000);
            h.assertTrue(bench.work().stage() == BenchWork.Stage.SETTING, "the setting waits for the worker's word");
            op.command(bench.work().id(), WorkCommand.FINISH_WORK);
            BenchWork work = bench.work();
            h.assertTrue(work != null && work.committed() && work.stage() == BenchWork.Stage.FINISHING, "set and on to the finish");
            h.assertTrue(bench.dock().getDamageValue() == 1, "the pliers paid one point of wear");
            finish(op, bench, 3000);
            h.assertTrue(bench.work() == null, "the work is complete");
            JewelryData ring = JewelryData.get(bench.clamp()).orElseThrow();
            h.assertTrue(bench.clamp().is(ModItems.RING.get()), "a ring rests in the clamp until it is collected");
            h.assertTrue(ring.design().equals(ImmersiveJewelry.id("designs/solitaire_ring")) && POLISHED.equals(ring.finish()), "design and finish");
            h.assertTrue(ring.gems().size() == 1 && ring.gems().get(0).gem().equals(DIAMOND), "one diamond");
            h.assertTrue(ring.settingQuality() >= 0.7F, "careful setting: " + ring.settingQuality());
            h.assertTrue(!ring.defects().contains(SettingDefect.LOOSE), "nothing loose");
            h.assertTrue(bench.socket(0).isEmpty(), "the stone is in the ring now, not also in the cup");
            h.assertTrue(bench.supply(0).getCount() == 1, "exactly one compound was used");
            h.assertTrue(crafted.get() == 1, "the crafted event fired once");
            // Taking it: the piece goes to the player.
            op.command(0L, WorkCommand.TAKE, TestSupport.args("Target", "clamp", "Index", 0));
            h.assertTrue(bench.clamp().isEmpty() && TestSupport.count(h, player, ModItems.RING.get()) == 1, "collected");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            TestSupport.cleanup(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void seatingIsReversibleUntilTheFirstClosure(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        TestSupport.Operator op = solitaire(h, player, bench);
        long id = bench.work().id();
        op.command(id, WorkCommand.LIFT, TestSupport.args("Socket", 0));
        h.assertTrue(bench.socket(0).isEmpty() && bench.tray(0).is(ModItems.CUT_GEM.get()), "lifted back into the tray, unharmed");
        op.command(id, WorkCommand.SEAT, TestSupport.args("Tray", 0, "Socket", 0, "Dx", 0F, "Dy", 0F, "Turn", 0F));
        set(op, bench, new SettingBot(SettingBot.Style.CAREFUL), 40);
        h.assertTrue(bench.work().committed(), "the first closure commits");
        op.command(id, WorkCommand.LIFT, TestSupport.args("Socket", 0));
        h.assertTrue(bench.socket(0).is(ModItems.CUT_GEM.get()), "a held stone cannot be lifted out");
        op.command(id, WorkCommand.CANCEL_PLAN);
        h.assertTrue(bench.work() != null, "committed work cannot be cancelled away");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nothingCommitsUntilEverythingIsInOrder(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        give(h, player, STATION, goldRingBlank());
        give(h, player, STATION, new ItemStack(ModItems.JEWELERS_PLIERS.get()));
        give(h, player, STATION, stone(DIAMOND, "round", 0.8F));
        give(h, player, STATION, stone(RUBY, "round", 0.8F));
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        // A sketch design this jeweler has not learned.
        op.command(0L, WorkCommand.SELECT_DESIGN, TestSupport.args("Design", ImmersiveJewelry.id("designs/trilogy_ring")));
        h.assertTrue(bench.work() == null, "an unknown sketch design is refused");
        op.command(0L, WorkCommand.SELECT_DESIGN, TestSupport.args("Design", ImmersiveJewelry.id("designs/solitaire_ring")));
        long id = bench.work().id();
        // No stone seated: the first squeeze commits nothing.
        set(op, bench, new SettingBot(SettingBot.Style.CAREFUL), 30);
        h.assertFalse(bench.work().committed(), "an empty setting is not committed");
        // A craft gate's refusal stops the commit too, with the stone still free.
        IJewelryCraftGate gate = (p, design, blank, stones) -> Optional.of(Component.literal("Not yet"));
        ImmersiveJewelryAPI.registerCraftGate(gate);
        try {
            op.command(id, WorkCommand.SEAT, TestSupport.args("Tray", 0, "Socket", 0, "Dx", 0F, "Dy", 0F, "Turn", 0F));
            set(op, bench, new SettingBot(SettingBot.Style.CAREFUL), 30);
            h.assertFalse(bench.work().committed(), "the gate refused");
            op.command(id, WorkCommand.LIFT, TestSupport.args("Socket", 0));
            h.assertTrue(bench.socket(0).isEmpty(), "and the stone came back out");
        } finally {
            ImmersiveJewelryAPI.unregisterCraftGate(gate);
        }
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stonesOfOnePieceAreOneGem(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        player.getAbilities().instabuild = true; // knows every design
        give(h, player, STATION, goldRingBlank());
        give(h, player, STATION, stone(DIAMOND, "round", 0.8F));
        give(h, player, STATION, stone(RUBY, "round", 0.8F));
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        op.command(0L, WorkCommand.SELECT_DESIGN, TestSupport.args("Design", ImmersiveJewelry.id("designs/trilogy_ring")));
        long id = bench.work().id();
        op.command(id, WorkCommand.SEAT, TestSupport.args("Tray", 0, "Socket", 0, "Dx", 0F, "Dy", 0F, "Turn", 0F));
        op.command(id, WorkCommand.SEAT, TestSupport.args("Tray", 1, "Socket", 1, "Dx", 0F, "Dy", 0F, "Turn", 0F));
        h.assertTrue(bench.socket(1).isEmpty() && bench.tray(1).is(ModItems.CUT_GEM.get()), "a ruby beside a diamond is refused");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void packingAndUnpackingMoveOwnershipExactlyOnce(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        TestSupport.Operator op = solitaire(h, player, bench);
        give(h, player, STATION, stone(DIAMOND, "brilliant", 0.7F));
        set(op, bench, new SettingBot(SettingBot.Style.CAREFUL), 60);
        BenchWork before = bench.work();
        h.assertTrue(before.committed(), "committed");
        op.command(before.id(), WorkCommand.PACK);
        h.assertTrue(bench.clamp().isEmpty() && bench.socket(0).isEmpty() && bench.work() == null, "the bench gave up the work");
        h.assertTrue(bench.tray(0).is(ModItems.CUT_GEM.get()), "the uncommitted tray stone stays on the bench");
        ItemStack carrier = player.getInventory().items.stream().filter(s -> s.getItem() instanceof UnfinishedJewelryItem).findFirst().orElseThrow();
        h.assertTrue(TestSupport.count(h, player, ModItems.UNFINISHED_JEWELRY.get()) == 1, "one carrier");
        h.assertTrue(TestSupport.count(h, player, ModItems.CUT_GEM.get()) == 0, "no loose copy of the committed stone");
        // Into another bench: everything comes back exactly as it was.
        BlockPos second = STATION.east(2);
        JewelersBenchBlockEntity other = bench(h, second);
        player.setPos(h.absolutePos(second).getX() + 0.5, h.absolutePos(second).getY(), h.absolutePos(second).getZ() + 1.5);
        give(h, player, second, carrier.copy());
        carrier.shrink(1);
        h.assertTrue(other.work() != null && other.work().id() == before.id(), "the same work");
        h.assertTrue(other.clamp().is(ModItems.RING_BLANK.get()) && other.socket(0).is(ModItems.CUT_GEM.get()), "frame and stone");
        h.assertTrue(other.work().setting().closure(0, 0) == before.setting().closure(0, 0), "every closure kept");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingTheBenchMidWorkDropsEveryItemOnce(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        TestSupport.Operator op = solitaire(h, player, bench);
        give(h, player, STATION, stone(DIAMOND, "brilliant", 0.7F));
        set(op, bench, new SettingBot(SettingBot.Style.CAREFUL), 60);
        h.destroyBlock(STATION);
        var drops = TestSupport.freshDrops(h, h.absolutePos(STATION));
        long carriers = drops.stream().filter(e -> e.getItem().is(ModItems.UNFINISHED_JEWELRY.get())).count();
        long blanks = drops.stream().filter(e -> e.getItem().is(ModItems.RING_BLANK.get())).count();
        long stones = drops.stream().filter(e -> e.getItem().is(ModItems.CUT_GEM.get())).mapToInt(e -> e.getItem().getCount()).sum();
        long pliers = drops.stream().filter(e -> e.getItem().is(ModItems.JEWELERS_PLIERS.get())).count();
        long compound = drops.stream().filter(e -> e.getItem().is(ModItems.POLISHING_COMPOUND.get())).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(carriers == 1 && blanks == 0, "the work drops once, as one carrier: " + carriers + "/" + blanks);
        h.assertTrue(stones == 1, "only the uncommitted tray stone drops loose: " + stones);
        h.assertTrue(pliers == 1 && compound == 2, "tools and supplies drop as themselves");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void theFinishIsPaidOnceAcrossPackingAndResuming(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        TestSupport.Operator op = solitaire(h, player, bench);
        setAndConfirm(op, bench, new SettingBot(SettingBot.Style.CAREFUL), 4000);
        finish(op, bench, 40);
        h.assertTrue(bench.work().finishPaid() && bench.supply(0).getCount() == 1, "compound used at the first wipe");
        op.command(bench.work().id(), WorkCommand.PACK);
        ItemStack carrier = player.getInventory().items.stream().filter(s -> s.getItem() instanceof UnfinishedJewelryItem).findFirst().orElseThrow();
        give(h, player, STATION, carrier.split(1));
        finish(op, bench, 3000);
        h.assertTrue(bench.work() == null && bench.clamp().is(ModItems.RING.get()), "finished after resuming");
        h.assertTrue(bench.supply(0).getCount() == 1, "no second compound was taken");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aBezelIsClosedRoundItsRim(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        give(h, player, STATION, goldRingBlank());
        give(h, player, STATION, stone(DIAMOND, "round", 0.85F));
        give(h, player, STATION, new ItemStack(ModItems.POLISHING_COMPOUND.get()));
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        op.command(0L, WorkCommand.SELECT_DESIGN, TestSupport.args("Design", ImmersiveJewelry.id("designs/bezel_ring")));
        long id = bench.work().id();
        op.command(id, WorkCommand.SEAT, TestSupport.args("Tray", 0, "Socket", 0, "Dx", 0F, "Dy", 0F, "Turn", 0F));
        setAndConfirm(op, bench, new SettingBot(SettingBot.Style.CAREFUL), 6000);
        h.assertTrue(bench.work().stage() == BenchWork.Stage.FINISHING, "the rim is closed without pliers (the bench's pusher)");
        finish(op, bench, 3000);
        JewelryData ring = JewelryData.get(bench.clamp()).orElseThrow();
        h.assertTrue(ring.settingQuality() >= 0.7F && !ring.defects().contains(SettingDefect.BENT_PRONG), "an even rim: " + ring.settingQuality());
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void takingAPieceApartLiftsEachStoneOnceAndKeepsTheFrame(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        GemData stone = TestSupport.cutStone(DIAMOND, ImmersiveJewelry.id("round"), 0.8F);
        ItemStack ring = JewelryFactory.jewelry(ImmersiveJewelry.id("ring"), MetalRef.of(new ResourceLocation("minecraft", "gold")),
                List.of(stone, stone, stone), ImmersiveJewelry.id("designs/trilogy_ring"), POLISHED, 0.8F, 0.8F, "Maker");
        ring.setHoverName(Component.literal("Grandmother's ring"));
        give(h, player, STATION, ring);
        give(h, player, STATION, new ItemStack(ModItems.JEWELERS_PLIERS.get()));
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        op.command(0L, WorkCommand.BEGIN, TestSupport.args("Kind", "remove"));
        SettingBot bot = new SettingBot(SettingBot.Style.CAREFUL);
        for (int socket = 0; socket < 3; socket++) {
            for (int i = 0; i < 3000; i++) {
                BenchWork work = bench.work();
                if (work == null || work.setting().opened(socket) && !work.setting().gripping()) break;
                op.feed(work.id(), bot.next(work.setting()));
            }
            op.command(bench.work().id(), WorkCommand.LIFT, TestSupport.args("Socket", socket));
            if (socket < 2) {
                h.assertTrue(JewelryData.get(bench.clamp()).orElseThrow().gems().size() == 2 - socket, "the piece loses exactly the lifted stone");
            }
        }
        int loose = 0;
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) loose += bench.tray(i).is(ModItems.CUT_GEM.get()) ? bench.tray(i).getCount() : 0;
        h.assertTrue(loose == 3, "three stones in the tray: " + loose);
        h.assertTrue(bench.work() == null && bench.clamp().is(ModItems.RING_BLANK.get()), "the frame is a blank again");
        BlankData blank = BlankData.get(bench.clamp()).orElseThrow();
        h.assertTrue(blank.reworks() == 1 && blank.integrity() < 1F, "worn by the rework");
        h.assertTrue(bench.clamp().getHoverName().getString().equals("Grandmother's ring"), "the name survives");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void retighteningClosesLooseProngsWithoutExperience(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        GemData stone = TestSupport.cutStone(DIAMOND, ImmersiveJewelry.id("round"), 0.8F);
        JewelryData loose = new JewelryData(ImmersiveJewelry.id("ring"), MetalRef.of(new ResourceLocation("minecraft", "gold")), List.of(stone),
                ImmersiveJewelry.id("designs/solitaire_ring"), POLISHED, 0.8F, 0.55F, 0.7F, 1F, 0, Set.of(SettingDefect.LOOSE), "Maker", false, null);
        give(h, player, STATION, JewelryFactory.stack(loose));
        give(h, player, STATION, new ItemStack(ModItems.JEWELERS_PLIERS.get()));
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        op.command(0L, WorkCommand.BEGIN, TestSupport.args("Kind", "retighten"));
        set(op, bench, new SettingBot(SettingBot.Style.CAREFUL), 4000);
        int xp = player.totalExperience;
        op.command(bench.work().id(), WorkCommand.FINISH_WORK);
        JewelryData after = JewelryData.get(bench.clamp()).orElseThrow();
        h.assertTrue(bench.work() == null, "done");
        h.assertFalse(after.defects().contains(SettingDefect.LOOSE), "nothing loose any more");
        h.assertTrue(after.settingQuality() >= loose.settingQuality(), "the setting got better: " + after.settingQuality());
        h.assertTrue(player.totalExperience == xp, "no crafting experience for a retightening");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void repairAndRefinishAreHandWorkWithTheSameEconomics(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        FakePlayer player = TestSupport.player(h);
        GemData stone = TestSupport.cutStone(DIAMOND, ImmersiveJewelry.id("round"), 0.8F);
        JewelryData worn = new JewelryData(ImmersiveJewelry.id("ring"), MetalRef.of(new ResourceLocation("minecraft", "gold")), List.of(stone),
                ImmersiveJewelry.id("designs/solitaire_ring"), POLISHED, 0.8F, 0.8F, 0.8F, 0.5F, 0, Set.of(), "Maker", false, null);
        give(h, player, STATION, JewelryFactory.stack(worn));
        give(h, player, STATION, new ItemStack(Items.GOLD_NUGGET, 2));
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        op.command(0L, WorkCommand.BEGIN, TestSupport.args("Kind", "repair"));
        finish(op, bench, 3000);
        JewelryData repaired = JewelryData.get(bench.clamp()).orElseThrow();
        h.assertTrue(Math.abs(repaired.integrity() - 0.8F) < 1e-4F, "integrity restored by the configured amount: " + repaired.integrity());
        h.assertTrue(bench.supply(0).getCount() == 1, "one nugget used");
        op.command(0L, WorkCommand.BEGIN, TestSupport.args("Kind", "refinish", "Finish", BURNISHED));
        finish(op, bench, 3000);
        JewelryData refinished = JewelryData.get(bench.clamp()).orElseThrow();
        h.assertTrue(BURNISHED.equals(refinished.finish()), "burnished now");
        h.assertTrue(refinished.workmanship() == repaired.workmanship(), "a finish never changes workmanship");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aBenchFromOneOneRecoversItsEscrowOnce(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h, STATION);
        ItemStack blank = goldRingBlank();
        ItemStack escrowStone = stone(DIAMOND, "round", 0.8F);
        CompoundTag old = new CompoundTag();
        old.put("Workpiece", blank.save(new CompoundTag()));
        ListTag escrow = new ListTag();
        escrow.add(escrowStone.save(new CompoundTag()));
        escrow.add(new ItemStack(ModItems.POLISHING_COMPOUND.get()).save(new CompoundTag()));
        old.put("Escrow", escrow);
        old.put("DisplayStone", escrowStone.save(new CompoundTag()));
        old.putFloat("Progress", 0.5F);
        bench.load(old);
        h.assertTrue(bench.clamp().is(ModItems.RING_BLANK.get()), "the workpiece is in the clamp");
        int stones = 0;
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) stones += bench.tray(i).getCount();
        h.assertTrue(stones == 1, "the escrowed stone is recovered once; the display copy is not a second one: " + stones);
        h.assertTrue(bench.supply(0).is(ModItems.POLISHING_COMPOUND.get()), "the finish material too");
        h.assertTrue(bench.work() == null, "old escrow is not taken for resumable work");
        // Saved in the new format and loaded again: nothing changes, nothing doubles.
        CompoundTag saved = bench.saveWithoutMetadata();
        bench.load(saved);
        stones = 0;
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) stones += bench.tray(i).getCount();
        h.assertTrue(stones == 1 && !saved.contains("Escrow"), "idempotent");
        h.succeed();
    }
}
