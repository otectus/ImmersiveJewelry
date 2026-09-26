package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.minigame.SessionManager;
import com.otectus.immersivejewelry.minigame.setting.SettingSession;
import com.otectus.immersivejewelry.network.packet.BenchActionPacket;
import com.otectus.immersivejewelry.network.packet.SettingInputPacket;
import com.otectus.immersivejewelry.network.packet.StartSettingPacket;
import com.otectus.immersivejewelry.quality.Workmanship;
import com.otectus.immersivejewelry.recipe.DesignKnowledge;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.testing.SetBot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.otectus.immersivejewelry.test.TestSupport.STATION;

/** The Jeweler's Bench end to end: setting, escrow safety, removal, repair, refinishing (spec 12, 41-42, 62-63). */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BenchGameTests {
    private static final ResourceLocation GOLD = new ResourceLocation("minecraft", "gold");
    private static final ResourceLocation POLISHED = ImmersiveJewelry.id("polished");
    private static final ResourceLocation BURNISHED = ImmersiveJewelry.id("burnished");

    private static JewelersBenchBlockEntity bench(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.JEWELERS_BENCH.get());
        return (JewelersBenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
    }

    private static ItemStack ringBlank(float smithing) {
        return JewelryFactory.blank(ModItems.RING_BLANK.get(), JewelryFactory.metal(JewelryDefinitions.server(), GOLD), smithing);
    }

    /** A blank on the bench, pliers in hand, stones in slots 1..n and polishing compound in slot 8. */
    private static FakePlayer ready(GameTestHelper h, JewelersBenchBlockEntity bench, int stones) {
        FakePlayer player = TestSupport.player(h);
        bench.setWorkpiece(ringBlank(0.8F));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.JEWELERS_PLIERS.get()));
        for (int i = 0; i < stones; i++) {
            player.getInventory().items.set(1 + i, CutGemItem.create(TestSupport.cutStone(TestSupport.RUBY, ImmersiveJewelry.id("round"), 0.8F + 0.01F * i)));
        }
        player.getInventory().items.set(8, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 3));
        return player;
    }

    private static SettingSession start(FakePlayer player, JewelersBenchBlockEntity bench, String design, int[] slots) {
        SessionManager.startSetting(player, new StartSettingPacket(bench.getBlockPos(), ImmersiveJewelry.id("designs/" + design), slots, POLISHED, false, false));
        return (SettingSession) SessionManager.byPlayer(player.getUUID()).orElse(null);
    }

    private static SetBot play(FakePlayer player, SettingSession session, boolean heavy) {
        SetBot bot = new SetBot(session.sim.params(), heavy);
        int step = 0;
        while (!session.isComplete() && step < session.maxSteps()) {
            int n = Math.min(5, session.maxSteps() - step);
            long bits = 0;
            for (int i = 0; i < n; i++) if (bot.next()) bits |= 1L << i;
            SessionManager.settingInput(player, new SettingInputPacket(session.id, step, n, bits), TestSupport.arrival(session, step + n));
            step += n;
        }
        return bot;
    }

    private static int count(FakePlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = "empty")
    public static void settingAStoneMakesARing(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = ready(h, bench, 1);
        SettingSession session = start(player, bench, "solitaire_ring", new int[]{1});
        h.assertTrue(session != null, "Setting starts");
        h.assertTrue(count(player, ModItems.CUT_GEM.get()) == 0 && bench.escrow().size() == 2, "The stone and the compound wait in the bench's escrow");
        SetBot bot = play(player, session, false);
        ItemStack piece = bench.workpiece();
        JewelryData data = JewelryData.get(piece).orElse(null);
        h.assertTrue(piece.getItem() instanceof JewelryItem && data != null, "A ring lies on the bench");
        h.assertTrue(bench.escrow().isEmpty(), "and the escrow was consumed");
        h.assertTrue(data.metal().id().equals(GOLD) && data.principal().gem().equals(TestSupport.RUBY), "Gold and ruby");
        h.assertTrue(POLISHED.equals(data.finish()), "Polished");
        h.assertTrue(Math.abs(data.settingQuality() - bot.sim().quality()) < 1e-6, "Setting quality is the server's replay of the same inputs");
        float expected = Workmanship.compute(0.8F, data.cutQuality(), data.settingQuality());
        h.assertTrue(Math.abs(data.workmanship() - expected) < 1e-5, "Workmanship combines the three stages");
        h.assertTrue(data.defects().isEmpty(), "Careful setting leaves no flaws, got " + data.defects());
        h.assertTrue(player.getGameProfile().getName().equals(data.maker()), "The jeweler's name is kept");
        // Save and reload the bench: the piece survives intact (spec 76: item data survives save/reload).
        var saved = bench.saveWithFullMetadata();
        bench.load(saved);
        h.assertTrue(JewelryData.get(bench.workpiece()).orElseThrow().equals(data), "The piece survives a save and load");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cancellingRefundsExactlyOnce(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = ready(h, bench, 1);
        SettingSession session = start(player, bench, "solitaire_ring", new int[]{1});
        SessionManager.cancelFromClient(player, session.id);
        SessionManager.cancelFromClient(player, session.id);
        h.assertTrue(count(player, ModItems.CUT_GEM.get()) == 1, "The stone comes back once");
        h.assertTrue(count(player, ModItems.POLISHING_COMPOUND.get()) == 3, "and so does the compound");
        h.assertTrue(bench.escrow().isEmpty() && !bench.isLocked(), "The bench is empty and free");
        h.assertTrue(bench.workpiece().is(ModItems.RING_BLANK.get()), "The blank is still on the bench");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingTheBenchMidSettingDropsEverythingOnce(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = ready(h, bench, 1);
        start(player, bench, "solitaire_ring", new int[]{1});
        BlockPos pos = h.absolutePos(STATION);
        h.getLevel().destroyBlock(pos, false);
        int inInventory = count(player, ModItems.CUT_GEM.get());
        long dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .filter(e -> e.getItem().is(ModItems.CUT_GEM.get())).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(inInventory + dropped == 1, "The stone exists exactly once, inventory " + inInventory + " dropped " + dropped);
        long blanks = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .filter(e -> e.getItem().is(ModItems.RING_BLANK.get())).count();
        h.assertTrue(blanks == 1, "The blank drops with the bench");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sketchDesignsMustBeLearned(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = ready(h, bench, 3);
        h.assertTrue(start(player, bench, "trilogy_ring", new int[]{1, 2, 3}) == null, "An unlearned sketch design is refused");
        h.assertTrue(count(player, ModItems.CUT_GEM.get()) == 3, "and takes nothing");
        DesignKnowledge.learn(player, ImmersiveJewelry.id("designs/trilogy_ring"));
        h.assertTrue(start(player, bench, "trilogy_ring", new int[]{1, 2}) == null, "Two stones are not three");
        SettingSession session = start(player, bench, "trilogy_ring", new int[]{1, 2, 3});
        h.assertTrue(session != null && session.sim.params().points() == 12, "Three stones, four prongs each");
        play(player, session, false);
        JewelryData data = JewelryData.get(bench.workpiece()).orElseThrow();
        h.assertTrue(data.gems().size() == 3, "A trilogy ring holds three stones");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void removingStonesGivesThemBackAndWearsTheFrame(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = ready(h, bench, 1);
        play(player, start(player, bench, "solitaire_ring", new int[]{1}), false);
        JewelryData made = JewelryData.get(bench.workpiece()).orElseThrow();
        SessionManager.benchAction(player, new BenchActionPacket(bench.getBlockPos(), BenchActionPacket.Action.REMOVE, null, false, false));
        SettingSession removal = (SettingSession) SessionManager.byPlayer(player.getUUID()).orElseThrow();
        h.assertTrue(removal.mode == SettingSession.Mode.REMOVE && removal.sim.params().removal(), "Removal is its own gentle minigame");
        play(player, removal, false);
        ItemStack frame = bench.workpiece();
        BlankData blank = BlankData.get(frame).orElse(null);
        h.assertTrue(frame.is(ModItems.RING_BLANK.get()) && blank != null, "The frame is a ring blank again");
        h.assertTrue(blank.reworks() == 1 && blank.integrity() < made.integrity(), "one rework the worse: " + blank.integrity());
        h.assertTrue(blank.metal().id().equals(GOLD) && Math.abs(blank.smithingQuality() - made.smithingQuality()) < 1e-6, "Its metal and metalwork are kept");
        GemData stone = player.getInventory().items.stream().map(s -> CutGemItem.data(s).orElse(null)).filter(g -> g != null).findFirst().orElse(null);
        h.assertTrue(stone != null && stone.gem().equals(TestSupport.RUBY), "The ruby is back in the inventory");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repairRestoresIntegrityWithinTheReworkCap(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = TestSupport.player(h);
        GemData stone = TestSupport.cutStone(TestSupport.RUBY, ImmersiveJewelry.id("round"), 0.8F);
        ItemStack ring = JewelryFactory.jewelry(ImmersiveJewelry.id("ring"), MetalRef.of(GOLD), java.util.List.of(stone), null, POLISHED, 0.7F, 0.7F, null);
        JewelryData data = JewelryData.get(ring).orElseThrow();
        new JewelryData(data.form(), data.metal(), data.gems(), data.design(), data.finish(), data.smithingQuality(), data.settingQuality(),
                data.workmanship(), 0.2F, 3, data.defects(), null, false, null).apply(ring);
        bench.setWorkpiece(ring);
        player.getInventory().items.set(0, new ItemStack(Items.GOLD_NUGGET, 5));
        SessionManager.benchAction(player, new BenchActionPacket(bench.getBlockPos(), BenchActionPacket.Action.REPAIR, null, false, false));
        JewelryData repaired = JewelryData.get(bench.workpiece()).orElseThrow();
        h.assertTrue(repaired.integrity() > 0.2F, "Repair restores integrity, got " + repaired.integrity());
        h.assertTrue(player.getInventory().items.get(0).getCount() == 4, "for one gold nugget");
        for (int i = 0; i < 4; i++) {
            SessionManager.benchAction(player, new BenchActionPacket(bench.getBlockPos(), BenchActionPacket.Action.REPAIR, null, false, false));
        }
        float cap = 1F - 0.06F * 3;
        h.assertTrue(Math.abs(JewelryData.get(bench.workpiece()).orElseThrow().integrity() - cap) < 1e-4, "never past the rework cap " + cap);
        h.assertTrue(player.getInventory().items.get(0).getCount() >= 1, "and stops charging once at the cap");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refinishingChangesTheFinish(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = TestSupport.player(h);
        GemData stone = TestSupport.cutStone(TestSupport.DIAMOND, ImmersiveJewelry.id("brilliant"), 0.8F);
        bench.setWorkpiece(JewelryFactory.jewelry(ImmersiveJewelry.id("amulet"), MetalRef.of(GOLD), java.util.List.of(stone), null, POLISHED, 0.7F, 0.7F, null));
        SessionManager.benchAction(player, new BenchActionPacket(bench.getBlockPos(), BenchActionPacket.Action.REFINISH, BURNISHED, false, false));
        h.assertTrue(BURNISHED.equals(JewelryData.get(bench.workpiece()).orElseThrow().finish()), "Burnished now (it needs no material)");
        SessionManager.benchAction(player, new BenchActionPacket(bench.getBlockPos(), BenchActionPacket.Action.REFINISH, POLISHED, false, false));
        h.assertTrue(BURNISHED.equals(JewelryData.get(bench.workpiece()).orElseThrow().finish()), "Polished needs compound the player lacks");
        TestSupport.cleanup(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heavyHandsLeaveFlaws(GameTestHelper h) {
        JewelersBenchBlockEntity bench = bench(h);
        FakePlayer player = ready(h, bench, 1);
        play(player, start(player, bench, "solitaire_ring", new int[]{1}), true);
        JewelryData data = JewelryData.get(bench.workpiece()).orElseThrow();
        h.assertTrue(data.defects().contains(com.otectus.immersivejewelry.api.jewelry.SettingDefect.BENT_PRONG), "Over-squeezing bends prongs");
        h.assertTrue(data.settingQuality() < 0.5F, "and the setting is poor, got " + data.settingQuality());
        TestSupport.cleanup(player);
        h.succeed();
    }
}
