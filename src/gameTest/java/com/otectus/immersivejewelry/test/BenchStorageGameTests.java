package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.blockentity.HostedBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.workstation.station.DiegesisStations;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import com.otectus.immersivejewelry.workstation.station.JewelryStations;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.pockyl.diegesis.api.station.StationType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaternionf;

import java.util.Collection;
import java.util.List;

import static com.otectus.immersivejewelry.test.TestSupport.STATION;

/**
 * The bench's storage (1.3, Diegesis beta 11): the two native groups bound to the bench's own arrays, the gate the library's
 * gestures pass through, the staging drawer's commands, the preparation of a design's stones, the guards every discrete
 * command passes (sequence, budget, generations, arguments), and saving, loading and dropping with the new fields. Every move
 * is counted: what went in equals what is stored, carried, given back or dropped.
 */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BenchStorageGameTests {

    private static JewelersBenchBlockEntity bench(GameTestHelper h) {
        return BenchGameTests.bench(h, STATION);
    }

    /** The bench as the library's host: with the tested Diegesis every bench is created as one. */
    private static HostedBenchBlockEntity host(JewelersBenchBlockEntity bench) {
        if (!(bench instanceof HostedBenchBlockEntity hosted)) throw new IllegalStateException("not a hosted bench: " + bench.getClass());
        return hosted;
    }

    /** The station as the library hands it to the logic, for asking the real logic object directly. */
    private record Context(StationType type, Level level, BlockPos pos, StationState state) implements StationContext {
        @Override
        public BlockState blockState() {
            return level.getBlockState(pos);
        }

        @Override
        public Direction facing() {
            return Direction.NORTH;
        }

        @Override
        public Collection<? extends Player> users() {
            return List.of();
        }

        @Override
        public void effect(ResourceLocation effect, String node, CompoundTag data) {
        }
    }

    private static int count(Player player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    @GameTest(template = "empty")
    public static void theLibrarysGesturesReachOnlyTheTwoBoundGroupsUnderTheLease(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = TestSupport.player(h);
        Context ctx = new Context(DiegesisStations.BENCH, h.getLevel(), bench.getBlockPos(), host(bench).stationState());
        StationLogic logic = DiegesisStations.BENCH.logic().create(ctx);
        ItemStack compound = new ItemStack(ModItems.POLISHING_COMPOUND.get());
        ItemStack stone = BenchGameTests.stone(TestSupport.DIAMOND, "round", 0.8F);
        SlotRef supply = SlotRef.of(JewelryStations.SUPPLIES, 0);
        SlotRef staged = SlotRef.of(JewelryStations.STAGING, 0);
        // No lease: nothing.
        h.assertFalse(logic.mayPlace(ctx, player, supply, compound), "no lease, no supply placement");
        h.assertFalse(logic.mayTake(ctx, player, supply), "no lease, no supply pickup");
        WorkSessionManager.Lease lease = WorkSessionManager.acquire(player, bench);
        h.assertTrue(lease != null, "lease");
        h.assertTrue(logic.mayPlace(ctx, player, supply, compound), "compound goes into a jar");
        h.assertFalse(logic.mayPlace(ctx, player, supply, new ItemStack(Items.DIRT)), "dirt is no supply");
        h.assertTrue(logic.mayTake(ctx, player, supply), "a jar's stack can be taken back");
        h.assertFalse(logic.mayPlace(ctx, player, staged, stone), "a shut drawer takes nothing");
        h.assertFalse(logic.mayTake(ctx, player, staged), "a shut drawer gives nothing by gesture");
        bench.setDrawerOpen(true);
        h.assertTrue(logic.mayPlace(ctx, player, staged, stone), "an open drawer takes a stone");
        h.assertFalse(logic.mayPlace(ctx, player, staged, new ItemStack(Items.DIRT)), "the drawer takes only the bench's own things");
        h.assertFalse(logic.mayPlace(ctx, player, SlotRef.of(JewelryStations.STAGING, 4), stone), "no fifth drawer place");
        h.assertFalse(logic.mayPlace(ctx, player, SlotRef.of("tray", 0), stone), "the tray is no library group");
        // Only the two groups, kept, the right sizes; the drawer's placed, the jars' not.
        var groups = DiegesisStations.BENCH.schema().slotGroups();
        h.assertTrue(groups.keySet().equals(java.util.Set.of(JewelryStations.SUPPLIES, JewelryStations.STAGING)), "groups " + groups.keySet());
        h.assertTrue(groups.get(JewelryStations.SUPPLIES).size() == 3 && !groups.get(JewelryStations.SUPPLIES).rules().returnOnClose()
                && !groups.get(JewelryStations.SUPPLIES).rules().handPlaced(), "three kept, fixed jars");
        h.assertTrue(groups.get(JewelryStations.STAGING).size() == 4 && !groups.get(JewelryStations.STAGING).rules().returnOnClose()
                && groups.get(JewelryStations.STAGING).rules().handPlaced(), "four kept, hand-placed drawer places");
        h.assertTrue(DiegesisStations.WHEEL.schema().slotGroups().isEmpty(), "the wheel has no library slots");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theHostStateIsAWindowOnTheBenchsOwnArrays(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        StationState state = host(bench).stationState();
        h.assertTrue(state.isBound(JewelryStations.SUPPLIES) && state.isBound(JewelryStations.STAGING), "both groups bound");
        bench.setSupply(1, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 5));
        h.assertTrue(state.getItem(SlotRef.of(JewelryStations.SUPPLIES, 1)).getCount() == 5, "the state reads the jar");
        int before = bench.generation("staging", 2);
        state.setItem(SlotRef.of(JewelryStations.STAGING, 2), new ItemStack(Items.GOLD_NUGGET, 7));
        h.assertTrue(bench.staged(2).is(Items.GOLD_NUGGET) && bench.staged(2).getCount() == 7, "a library write lands in the drawer array");
        h.assertTrue(bench.generation("staging", 2) == before + 1, "and raises that place's generation");
        CompoundTag saved = state.save();
        CompoundTag slots = saved.getCompound("slots");
        h.assertFalse(slots.contains(JewelryStations.SUPPLIES) || slots.contains(JewelryStations.STAGING), "the library saves no second inventory");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stagingMovesRealStacksExactlyOnce(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = TestSupport.player(h);
        player.getInventory().items.set(0, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 5));
        player.getInventory().items.set(1, BenchGameTests.stone(TestSupport.RUBY, "round", 0.8F));
        ItemStack stoneCopy = player.getInventory().items.get(1).copy();
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        op.command(0L, WorkCommand.STAGE_IN, TestSupport.args("From", 0, "Count", 2));
        h.assertTrue(bench.staged(0).isEmpty() && count(player, ModItems.POLISHING_COMPOUND.get()) == 5, "a shut drawer takes nothing");
        op.command(0L, WorkCommand.DRAWER, TestSupport.args("Open", true));
        h.assertTrue(bench.drawerOpen(), "drawer open");
        op.command(0L, WorkCommand.STAGE_IN, TestSupport.args("From", 0, "Count", 2));
        h.assertTrue(bench.staged(0).getCount() == 2 && count(player, ModItems.POLISHING_COMPOUND.get()) == 3, "two staged, three kept");
        op.command(0L, WorkCommand.STAGE_IN, TestSupport.args("From", 1, "Count", 0));
        h.assertTrue(ItemStack.matches(bench.staged(1), stoneCopy) && player.getInventory().items.get(1).isEmpty(), "the stone staged with its data");
        op.command(0L, WorkCommand.STAGE_IN, TestSupport.args("From", 2, "Count", 0));
        h.assertTrue(bench.staged(2).isEmpty(), "an empty slot stages nothing");
        // The compound goes on into a jar; the stone back to the inventory, data unchanged.
        op.command(0L, WorkCommand.MOVE_STAGED, TestSupport.args("Index", 0, "Count", 0, "To", "work", "Gen", bench.generation("staging", 0)));
        h.assertTrue(bench.staged(0).isEmpty() && bench.supply(0).getCount() == 2, "the staged compound is a supply now");
        op.command(0L, WorkCommand.MOVE_STAGED, TestSupport.args("Index", 1, "Count", 0, "To", "inventory", "Gen", bench.generation("staging", 1)));
        boolean back = false;
        for (ItemStack s : player.getInventory().items) back |= ItemStack.matches(s, stoneCopy);
        h.assertTrue(back && bench.staged(1).isEmpty(), "the stone is back exactly as it was");
        h.assertTrue(count(player, ModItems.POLISHING_COMPOUND.get()) + bench.supply(0).getCount() == 5, "five compound in all, none made or lost");
        // A creative player's staged stack still moves rather than copies.
        player.getAbilities().instabuild = true;
        op.command(0L, WorkCommand.STAGE_IN, TestSupport.args("From", 0, "Count", 1));
        op.command(0L, WorkCommand.MOVE_STAGED, TestSupport.args("Index", 0, "Count", 0, "To", "work"));
        h.assertTrue(bench.staged(0).isEmpty() && bench.supply(0).getCount() == 3 && count(player, ModItems.POLISHING_COMPOUND.get()) == 2,
                "creative mode moves the real stack too");
        player.getAbilities().instabuild = false;
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void staleRepeatedFloodingAndMalformedCommandsChangeNothing(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = TestSupport.player(h);
        BenchGameTests.give(h, player, STATION, BenchGameTests.stone(TestSupport.DIAMOND, "round", 0.8F));
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        int gen = bench.generation("tray", 0);
        op.command(0L, WorkCommand.TAKE, TestSupport.args("Target", "tray", "Index", 0, "Gen", gen + 1));
        h.assertFalse(bench.tray(0).isEmpty(), "a take made on a stale picture takes nothing");
        op.command(0L, WorkCommand.TAKE, TestSupport.args("Target", "tray", "Index", 0, "Gen", gen));
        h.assertTrue(bench.tray(0).isEmpty(), "the same take on the current picture works");
        // A repeated sequence number is dropped.
        player.getInventory().items.set(0, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 64));
        op.command(0L, WorkCommand.DRAWER, TestSupport.args("Open", true));
        int seq = ++op.commandSeq;
        op.clock += 1000L;
        op.commandAt(seq, WorkCommand.STAGE_IN, TestSupport.args("From", 0, "Count", 1));
        op.commandAt(seq, WorkCommand.STAGE_IN, TestSupport.args("From", 0, "Count", 1));
        h.assertTrue(bench.staged(0).getCount() == 1, "a repeated command runs once");
        // A flood at one instant: the lease's budget lets a burst through, never more.
        op.clock += 5000L;
        for (int i = 0; i < 40; i++) op.commandAt(++op.commandSeq, WorkCommand.STAGE_IN, TestSupport.args("From", 0, "Count", 1));
        int staged = bench.staged(0).getCount() - 1;
        h.assertTrue(staged == WorkSessionManager.COMMAND_BURST, "a burst of " + WorkSessionManager.COMMAND_BURST + " commands, got " + staged);
        h.assertTrue(bench.staged(0).getCount() + count(player, ModItems.POLISHING_COMPOUND.get()) == 64, "every item accounted for");
        // Malformed: a seat with a non-finite offset, a placement from an invalid slot.
        op.clock += 5000L;
        op.command(0L, WorkCommand.SEAT, TestSupport.args("Tray", 0, "Socket", 0, "Dx", Float.NaN, "Dy", 0F, "Turn", 0F));
        op.command(0L, WorkCommand.PLACE, TestSupport.args("From", -2, "Target", "", "Index", -1));
        op.command(0L, WorkCommand.MOVE_STAGED, TestSupport.args("Index", 9, "Count", 0, "To", "work"));
        h.assertTrue(bench.work() == null && bench.socket(0).isEmpty(), "malformed commands change nothing");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void preparingMovesExactlyTheStonesStillNeeded(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = TestSupport.player(h);
        // Three identical stones in one stack, and two better single ones.
        ItemStack three = BenchGameTests.stone(TestSupport.DIAMOND, "round", 0.7F);
        three.setCount(3);
        player.getInventory().items.set(0, three);
        player.getInventory().items.set(1, BenchGameTests.stone(TestSupport.DIAMOND, "round", 0.95F));
        player.getInventory().items.set(2, BenchGameTests.stone(TestSupport.RUBY, "round", 0.99F));
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        // A solitaire with a preference for the stack: exactly one stone, from that stack.
        CompoundTag args = TestSupport.args("Design", ImmersiveJewelry.id("designs/solitaire_ring").toString(), "Gem", TestSupport.DIAMOND.toString());
        args.put("Prefer", new IntArrayTag(new int[]{0}));
        op.command(0L, WorkCommand.PREPARE, args);
        h.assertTrue(bench.tray(0).getCount() == 1 && player.getInventory().items.get(0).getCount() == 2, "one stone from the preferred stack");
        op.command(0L, WorkCommand.PREPARE, args);
        h.assertTrue(bench.tray(1).isEmpty() && player.getInventory().items.get(0).getCount() == 2, "nothing more when the tray holds enough");
        // A trilogy (three of one gem): two more diamonds, best cut first; the better ruby stays (another gem).
        op.command(0L, WorkCommand.PREPARE, TestSupport.args("Design", ImmersiveJewelry.id("designs/trilogy_ring").toString()));
        int diamonds = 0;
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) {
            if (CutGemItem.data(bench.tray(i)).map(g -> g.gem().equals(TestSupport.DIAMOND)).orElse(false)) diamonds += bench.tray(i).getCount();
        }
        h.assertTrue(diamonds == 3, "three diamonds in the tray, got " + diamonds);
        h.assertTrue(player.getInventory().items.get(1).isEmpty(), "the best diamond was taken first");
        h.assertTrue(!player.getInventory().items.get(2).isEmpty(), "the ruby was never mixed in");
        h.assertTrue(player.getInventory().items.get(0).getCount() == 1, "one of the stack left: exactly what was needed moved");
        h.assertTrue(bench.work() == null, "preparing starts no work");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theDrawerSavesLoadsAndDropsWithEveryStackOnce(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        bench.setSupply(0, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 4));
        bench.setTray(3, BenchGameTests.stone(TestSupport.RUBY, "round", 0.8F));
        bench.setStaged(0, new ItemStack(Items.GOLD_NUGGET, 9));
        bench.setStaged(2, BenchGameTests.stone(TestSupport.DIAMOND, "round", 0.6F));
        bench.setDrawerOpen(true);
        SlotRef pose = SlotRef.of(JewelryStations.STAGING, 0);
        host(bench).stationState().setPose(pose, new ItemPose(7F, 14.1F, 2F, 0.4F, new Quaternionf(), 1F, -1));
        CompoundTag saved = bench.saveWithoutMetadata();
        h.assertTrue(saved.getInt("Format") == JewelersBenchBlockEntity.FORMAT, "still format 2");
        // A format-2 bench without a clamp (but with tray and supplies) is no 1.1 bench.
        JewelersBenchBlockEntity loaded = JewelersBenchBlockEntity.create(bench.getBlockPos(), bench.getBlockState());
        CompoundTag copy = saved.copy();
        // A pose for an empty place is presentation without a stack: dropped on load.
        ListTag poses = copy.getCompound("Station").getList("poses", 10);
        CompoundTag stray = new ItemPose(8F, 14.1F, 1F, 0.4F, new Quaternionf(), 1F, -1).save();
        stray.putString("Group", JewelryStations.STAGING);
        stray.putInt("Slot", 3);
        poses.add(stray);
        copy.getCompound("Station").put("poses", poses);
        loaded.load(copy);
        h.assertTrue(loaded.clamp().isEmpty() && loaded.supply(0).getCount() == 4 && !loaded.tray(3).isEmpty(), "format 2 read as format 2");
        h.assertTrue(loaded.staged(0).getCount() == 9 && ItemStack.matches(loaded.staged(2), bench.staged(2)) && loaded.drawerOpen(), "the drawer");
        h.assertTrue(host(loaded).stationState().getPose(pose) != null, "the staged stack's pose");
        h.assertTrue(host(loaded).stationState().getPose(SlotRef.of(JewelryStations.STAGING, 3)) == null, "no pose without a stack");
        // A bench saved by 1.2 has no drawer: empty and shut, nothing copied into it.
        CompoundTag old = saved.copy();
        old.remove("Staging");
        old.remove("DrawerOpen");
        old.remove("Station");
        JewelersBenchBlockEntity older = new JewelersBenchBlockEntity(bench.getBlockPos(), bench.getBlockState());
        older.load(old);
        boolean empty = !older.drawerOpen();
        for (int i = 0; i < JewelersBenchBlockEntity.STAGING; i++) empty &= older.staged(i).isEmpty();
        h.assertTrue(empty && older.supply(0).getCount() == 4 && !older.tray(3).isEmpty(), "1.2 bench: drawer empty, the rest unchanged");
        // Breaking drops each stack once, the drawer's included.
        bench.dropContents();
        AABB around = new AABB(bench.getBlockPos()).inflate(2);
        int nuggets = 0, compound = 0, stones = 0;
        for (ItemEntity e : TestSupport.freshDrops(h, bench.getBlockPos())) {
            ItemStack s = e.getItem();
            if (s.is(Items.GOLD_NUGGET)) nuggets += s.getCount();
            if (s.is(ModItems.POLISHING_COMPOUND.get())) compound += s.getCount();
            if (s.is(ModItems.CUT_GEM.get())) stones += s.getCount();
        }
        h.assertTrue(nuggets == 9 && compound == 4 && stones == 2, "dropped once: " + nuggets + " nuggets, " + compound + " compound, " + stones + " stones");
        h.assertTrue(bench.isEmpty(), "nothing left behind");
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, around)) e.discard();
        h.succeed();
    }

    /** A client's copy of the bench, fed the server's update tag, names the generations the server checks. */
    @GameTest(template = "empty")
    public static void aSyncedCopyCarriesTheGenerationsCommandsName(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = TestSupport.player(h);
        BenchGameTests.give(h, player, STATION, BenchGameTests.goldRingBlank());
        BenchGameTests.give(h, player, STATION, new ItemStack(ModItems.JEWELERS_PLIERS.get()));
        BenchGameTests.give(h, player, STATION, BenchGameTests.stone(TestSupport.DIAMOND, "round", 0.9F));
        TestSupport.Operator op = TestSupport.operate(h, player, STATION);
        op.command(0L, WorkCommand.SELECT_DESIGN, TestSupport.args("Design", ImmersiveJewelry.id("designs/solitaire_ring")));
        JewelersBenchBlockEntity client = new JewelersBenchBlockEntity(bench.getBlockPos(), bench.getBlockState());
        client.handleUpdateTag(bench.getUpdateTag());
        int gen = client.generation("tray", 0);
        h.assertTrue(gen == bench.generation("tray", 0) && gen > 0, "the copy's tray generation " + gen + " is the server's " + bench.generation("tray", 0));
        long id = client.work().id();
        op.command(id, WorkCommand.SEAT, TestSupport.args("Tray", 0, "Socket", 0, "Dx", 0F, "Dy", 0F, "Turn", 0F, "Gen", gen));
        h.assertTrue(!bench.socket(0).isEmpty(), "seated with the generation the copy showed");
        TestSupport.cleanup(player);
        h.succeed();
    }

    /**
     * A bench made without the library's host (a Diegesis whose host API changed) still loads and saves everything, keeps the
     * library's own tag as it was read for a compatible Diegesis later, and drops only the pose of a place whose stack changed.
     */
    @GameTest(template = "empty")
    public static void aBenchWithoutTheLibrarysHostKeepsEverything(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        h.assertTrue(bench instanceof HostedBenchBlockEntity, "with the tested Diegesis the world's bench is a host");
        bench.setStaged(0, new ItemStack(Items.GOLD_NUGGET, 5));
        bench.setStaged(1, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 2));
        host(bench).stationState().setPose(SlotRef.of(JewelryStations.STAGING, 0), new ItemPose(6F, 14.1F, 2F, 0.4F, new Quaternionf(), 1F, -1));
        host(bench).stationState().setPose(SlotRef.of(JewelryStations.STAGING, 1), new ItemPose(8.5F, 14.1F, 2F, 0.4F, new Quaternionf(), 1F, -1));
        CompoundTag saved = bench.saveWithoutMetadata();
        JewelersBenchBlockEntity plain = new JewelersBenchBlockEntity(bench.getBlockPos(), bench.getBlockState());
        h.assertFalse(plain instanceof com.pockyl.diegesis.api.state.StationHost, "the plain bench is no library host");
        plain.load(saved.copy());
        h.assertTrue(plain.staged(0).getCount() == 5 && plain.staged(1).getCount() == 2, "the staged stacks");
        CompoundTag again = plain.saveWithoutMetadata();
        h.assertTrue(again.getCompound("Station").getList("poses", 10).size() == 2, "both poses kept untouched");
        plain.setStaged(1, new ItemStack(Items.GOLD_NUGGET, 1));
        CompoundTag changed = plain.saveWithoutMetadata();
        ListTag poses = changed.getCompound("Station").getList("poses", 10);
        h.assertTrue(poses.size() == 1 && poses.getCompound(0).getInt("Slot") == 0, "only the changed place's pose went");
        // A host reads it back: the kept pose returns, the changed place has none.
        JewelersBenchBlockEntity back = JewelersBenchBlockEntity.create(bench.getBlockPos(), bench.getBlockState());
        back.load(changed);
        h.assertTrue(host(back).stationState().getPose(SlotRef.of(JewelryStations.STAGING, 0)) != null
                && host(back).stationState().getPose(SlotRef.of(JewelryStations.STAGING, 1)) == null, "poses as kept");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void walkingUpRetrievesAStagedStackFromAShutDrawer(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = TestSupport.player(h);
        bench.setStaged(1, new ItemStack(Items.GOLD_NUGGET, 3));
        player.setShiftKeyDown(true);
        TestSupport.use(h, player);
        player.setShiftKeyDown(false);
        h.assertTrue(bench.staged(1).isEmpty() && count(player, Items.GOLD_NUGGET) == 3, "sneak-use takes the staged stack back");
        TestSupport.cleanup(player);
        h.succeed();
    }
}
