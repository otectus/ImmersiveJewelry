package com.otectus.immersivejewelry.client.compat.ponder;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.client.guide.Lessons;
import com.otectus.immersivejewelry.client.presentation.anim.JewelerClip;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.LapItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.particle.GemParticleOptions;
import com.otectus.immersivejewelry.quality.Workmanship;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModParticles;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import com.otectus.immersivejewelry.workstation.BenchPlanner;
import com.otectus.immersivejewelry.workstation.CutPlanner;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The demonstrations' kit: the layouts (structures under {@code assets/immersive_jewelry/ponder/}), captions keyed per
 * lesson ({@code immersive_jewelry.ponder.shared.<lesson>.<n>}), and station states set through the stations' own
 * methods and work orders in the demonstration's level only: stones mounted and appraised with fixed example values, cuts
 * planned by {@link CutPlanner}, grinding, setting and finishing stepped through the real simulations by
 * {@link DemoHands}, results made as the stations make them. Everything is a Ponder instruction: it runs at its moment and
 * again, from the start, on every replay or seek. Nothing here can reach a real station, an inventory or the network.
 */
final class SceneKit {
    /** The layouts: plate size and where each station stands (facing north, toward Ponder's camera). */
    enum Layout {
        WHEEL("wheel", 5, new BlockPos(2, 1, 2), null),
        BENCH("bench", 5, null, new BlockPos(2, 1, 2)),
        WORKSHOP("workshop", 7, new BlockPos(2, 1, 2), new BlockPos(4, 1, 2));

        final String structure;
        final int plate;
        @Nullable
        final BlockPos wheel, bench;

        Layout(String structure, int plate, @Nullable BlockPos wheel, @Nullable BlockPos bench) {
            this.structure = structure;
            this.plate = plate;
            this.wheel = wheel;
            this.bench = bench;
        }
    }

    /** The demonstrating jeweler's id on the stations (only ever in a demonstration's level). */
    static final UUID JEWELER = UUID.nameUUIDFromBytes("immersive_jewelry:demonstration".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    /** Simulation steps per scene tick (the stations step every 20 ms: 2.5 per tick). */
    static final int STEPS_PER_TICK = 3;

    final SceneBuilder scene;
    final SceneBuildingUtil util;
    final Lessons lesson;
    final Layout layout;
    @Nullable
    final BlockPos wheel, bench;
    private final boolean reducedMotion;
    @Nullable
    private ElementLink<JewelerActor> actor;

    private SceneKit(SceneBuilder scene, SceneBuildingUtil util, Lessons lesson, Layout layout) {
        this.scene = scene;
        this.util = util;
        this.lesson = lesson;
        this.layout = layout;
        this.wheel = layout.wheel;
        this.bench = layout.bench;
        this.reducedMotion = ClientConfig.reducedTutorialMotion();
    }

    /** A storyboard: the title, the plate, the stations, the lesson's program. */
    static PonderStoryBoard board(Lessons lesson, Layout layout, Consumer<SceneKit> program) {
        return (scene, util) -> {
            SceneKit kit = new SceneKit(scene, util, lesson, layout);
            scene.title(lesson.id(), lesson.id());
            scene.configureBasePlate(0, 0, layout.plate);
            // Jewelry is small: the view comes in close (the workshop a little less, it shows two stations).
            scene.scaleSceneView(layout.plate > 5 ? 1.15F : 1.6F);
            scene.showBasePlate();
            scene.idle(8);
            scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
            scene.idle(12);
            program.accept(kit);
            scene.idle(20);
            scene.markAsFinished();
        };
    }

    // ------------------------------------------------------------------------------------------------ text

    private ResourceLocation caption(int n) {
        return ImmersiveJewelry.id(lesson.id() + "." + n);
    }

    /** Caption {@code n} pointing at a spot; the scene waits to read it. */
    void say(int n, Vec3 at, int ticks) {
        scene.overlay().showText(ticks).sharedText(caption(n)).pointAt(at).placeNearTarget().attachKeyFrame();
        scene.idle(ticks + 10);
    }

    void say(int n, Vec3 at) {
        say(n, at, 70);
    }

    /** Caption {@code n} with no target (a rule or a summary). */
    void note(int n, int ticks) {
        scene.overlay().showText(ticks).sharedText(caption(n)).independent(0).attachKeyFrame();
        scene.idle(ticks + 10);
    }

    void note(int n) {
        note(n, 70);
    }

    /** A failure and its cause, in red. */
    void warn(int n, Vec3 at) {
        scene.overlay().showText(70).sharedText(caption(n)).colored(PonderPalette.RED).pointAt(at).placeNearTarget().attachKeyFrame();
        scene.idle(80);
    }

    /** A success, in green. */
    void good(int n, Vec3 at) {
        scene.overlay().showText(70).sharedText(caption(n)).colored(PonderPalette.GREEN).pointAt(at).placeNearTarget().attachKeyFrame();
        scene.idle(80);
    }

    /** The bundled example notice: these are the mod's own numbers, a pack can change them and the handbook shows the server's. */
    void example() {
        scene.overlay().showText(60).sharedText(ImmersiveJewelry.id("example")).colored(PonderPalette.BLUE).independent(0);
        scene.idle(66);
    }

    void idle(int ticks) {
        scene.idle(ticks);
    }

    void turn(float degrees) {
        if (!reducedMotion) scene.rotateCameraY(degrees);
    }

    // ------------------------------------------------------------------------------------------------ markers

    Vec3 top(BlockPos pos) {
        return util.vector().topOf(pos);
    }

    /** A point of a station in its scene pixels (the painters' north-facing frame; the lessons' stations face north). */
    Vec3 at(BlockPos pos, float px, float py, float pz) {
        return new Vec3(pos.getX() + px / 16.0, pos.getY() + py / 16.0, pos.getZ() + pz / 16.0);
    }

    Vec3 lap() {
        return at(wheel, 9.0F, 12.4F, 8.5F);
    }

    Vec3 quill() {
        return at(wheel, 8.2F, 13.5F, 6.2F);
    }

    Vec3 cup() {
        return at(wheel, 2.0F, 12.6F, 2.0F);
    }

    Vec3 rack() {
        return at(wheel, 14.0F, 12.6F, 1.6F);
    }

    Vec3 lever() {
        return at(wheel, 1.8F, 15.0F, 10.0F);
    }

    Vec3 clamp() {
        return at(bench, 8.0F, 17.0F, 7.6F);
    }

    Vec3 tray() {
        return at(bench, 12.8F, 14.4F, 2.3F);
    }

    Vec3 dock() {
        return at(bench, 2.0F, 14.4F, 6.4F);
    }

    Vec3 supplies() {
        return at(bench, 3.0F, 14.4F, 1.9F);
    }

    Vec3 folio() {
        return at(bench, 13.9F, 14.4F, 7.2F);
    }

    void mark(Vec3 centre, double half, PonderPalette color, int ticks) {
        AABB box = new AABB(centre, centre).inflate(half);
        scene.overlay().chaseBoundingBoxOutline(color, box, box, ticks);
    }

    void click(Vec3 at, int ticks, @Nullable ItemStack with) {
        var input = scene.overlay().showControls(at, Pointing.DOWN, ticks).rightClick();
        if (with != null && !with.isEmpty()) input.withItem(with);
    }

    void success(BlockPos pos) {
        scene.effects().indicateSuccess(pos);
    }

    /** A few grains of stone off the lap (the stations' own gem dust particle). */
    void dust(int color) {
        scene.effects().emitParticles(lap(), scene.effects().simpleParticleEmitter(
                new GemParticleOptions(ModParticles.GEM_DUST.get(), color, 0.6F), new Vec3(0, 0.03, 0)), 3F, 2);
    }

    // ------------------------------------------------------------------------------------------------ the jeweler

    /** The demonstrating jeweler, faded in a step in front of {@code station} (its north side, toward the camera), facing it. */
    void jeweler(BlockPos station) {
        JewelerActor element = new JewelerActor(new Vec3(station.getX() + 0.5, station.getY(), station.getZ() - 0.75), 0F);
        ActorInstruction instruction = new ActorInstruction(10, Direction.DOWN, element);
        scene.addInstruction(instruction);
        actor = instruction.createLink(scene.getScene());
        scene.idle(10);
    }

    void pose(@Nullable JewelerClip loop) {
        if (actor == null) return;
        ElementLink<JewelerActor> link = actor;
        scene.addInstruction(s -> s.resolve(link).hold(loop));
    }

    void act(JewelerClip clip) {
        if (actor == null) return;
        ElementLink<JewelerActor> link = actor;
        scene.addInstruction(s -> s.resolve(link).play(clip, s.getCurrentTime()));
    }

    void holding(ItemStack stack) {
        if (actor == null) return;
        ElementLink<JewelerActor> link = actor;
        ItemStack copy = stack.copy();
        scene.addInstruction(s -> s.resolve(link).item(copy));
    }

    /** The jeweler steps to the other station (workshop layout). */
    void walkTo(BlockPos station) {
        if (actor == null) return;
        ElementLink<JewelerActor> link = actor;
        Vec3 to = new Vec3(station.getX() + 0.5, station.getY(), station.getZ() - 0.75);
        scene.addInstruction(s -> s.resolve(link).move(to));
        scene.idle(6);
    }

    // ------------------------------------------------------------------------------------------------ examples

    static JewelryDefinitions defs() {
        return JewelryDefinitions.client();
    }

    /** A rough stone appraised with fixed example values (a demonstration never rolls). */
    static GemData rough(String gem, float size, float natural, float inclusions) {
        return GemData.rough(ImmersiveJewelry.id(gem), size, natural, inclusions);
    }

    static GemData rough(String gem) {
        return rough(gem, 1.2F, 0.7F, 0.15F);
    }

    /** A cut stone as a careful cut leaves it (example values). */
    static GemData cut(String gem, String cut, float quality) {
        GemDefinition def = defs().gemOrGeneric(ImmersiveJewelry.id(gem));
        CutDefinition c = defs().cut(ImmersiveJewelry.id(cut));
        if (c == null) return rough(gem);
        return JewelryFactory.sampleCut(def, c, quality);
    }

    static ItemStack stack(GemData stone) {
        return stone.isCut() ? CutGemItem.create(stone) : RoughGemItem.create(stone);
    }

    static MetalRef gold() {
        return JewelryFactory.metal(defs(), new ResourceLocation("minecraft", "gold"));
    }

    static ItemStack blank(Item item) {
        return JewelryFactory.blank(item, gold(), 0.72F);
    }

    @Nullable
    static JewelryDesignRecipe design(String path) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        ResourceLocation id = ImmersiveJewelry.id("designs/" + path);
        for (JewelryDesignRecipe r : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get())) {
            if (r.getId().equals(id)) return r;
        }
        return null;
    }

    /** A finished piece of a design with example qualities. */
    static ItemStack piece(String design, List<GemData> stones, String finish) {
        JewelryDesignRecipe recipe = design(design);
        ResourceLocation form = recipe != null ? recipe.form() : ImmersiveJewelry.id("ring");
        return JewelryFactory.jewelry(form, gold(), stones, recipe != null ? recipe.getId() : null, ImmersiveJewelry.id(finish), 0.72F, 0.8F, null);
    }

    // ------------------------------------------------------------------------------------------------ station access

    private static <T extends BlockEntity> void with(PonderScene scene, @Nullable BlockPos pos, Class<T> type, Consumer<T> change) {
        if (pos == null) return;
        BlockEntity entity = scene.getWorld().getBlockEntity(pos);
        if (type.isInstance(entity)) change.accept(type.cast(entity));
    }

    void atWheel(Consumer<LapidaryWheelBlockEntity> change) {
        BlockPos pos = wheel;
        scene.addInstruction(s -> with(s, pos, LapidaryWheelBlockEntity.class, change));
    }

    void atBench(Consumer<JewelersBenchBlockEntity> change) {
        BlockPos pos = bench;
        scene.addInstruction(s -> with(s, pos, JewelersBenchBlockEntity.class, change));
    }

    /** The jeweler steps up to (or away from) a station: rigs and poses follow an operated station. */
    void operate(boolean on) {
        if (wheel != null) atWheel(w -> w.setOperator(on ? JEWELER : null, on ? "Jeweler" : null));
        if (bench != null) atBench(b -> b.setOperator(on ? JEWELER : null, on ? "Jeweler" : null));
    }

    // ------------------------------------------------------------------------------------------------ the wheel

    void lap(Item lap) {
        atWheel(w -> w.swapLap(new ItemStack(lap)));
    }

    void mount(GemData stone) {
        ItemStack stack = stack(stone);
        atWheel(w -> w.setStone(stack));
    }

    /** Plans {@code cut} on the mounted stone as the specimen rack does (recut when the stone is already cut). */
    void plan(String cut) {
        atWheel(w -> {
            GemData stone = w.stone().is(ModItems.CUT_GEM.get()) ? CutGemItem.data(w.stone()).orElse(null) : RoughGemItem.data(w.stone()).orElse(null);
            CutDefinition def = defs().cut(ImmersiveJewelry.id(cut));
            if (stone == null || def == null) return;
            GemDefinition gem = defs().gemOrGeneric(stone.gem());
            LapItem.Tier tier = w.tier();
            long seed = stone.gem().hashCode() * 31L + cut.hashCode();
            CutWork work = new CutWork(seed, def.id(), stone.isCut(), seed,
                    CutPlanner.params(defs(), gem, stone, def, tier, WorkSessionManager.Preferences.NONE, 1F, seed));
            w.setWork(work);
        });
    }

    /** {@code ticks} of grinding by {@code style} hands, with the lap turning and dust at the contact. */
    void grind(int ticks, DemoHands.Style style) {
        grind(ticks, style, STEPS_PER_TICK);
    }

    /** Grinding shown faster than life ({@code steps} per tick; the captions say so), stopping when every face is done. */
    void grind(int ticks, DemoHands.Style style, int steps) {
        BlockPos pos = wheel;
        scene.addInstruction(new WorkInstruction(ticks, () -> {
            DemoHands hands = new DemoHands(style);
            return (s, tick) -> with(s, pos, LapidaryWheelBlockEntity.class, w -> w.work().ifPresent(work -> {
                for (int i = 0; i < steps; i++) work.sim().step(work.sim().ready() && style == DemoHands.Style.CAREFUL
                        ? DemoHands.lifted(work.sim()) : hands.facet(work.sim()));
                w.workChanged();
                spin(w);
            }));
        }));
    }

    /** The stone lifted (and cooling in air) for {@code ticks}, or dipped in the cooling cup. */
    void lift(int ticks, boolean dip) {
        BlockPos pos = wheel;
        scene.addInstruction(new WorkInstruction(ticks, () -> (s, tick) -> with(s, pos, LapidaryWheelBlockEntity.class, w -> w.work().ifPresent(work -> {
            FacetSim sim = work.sim();
            for (int i = 0; i < STEPS_PER_TICK; i++) sim.step(dip ? DemoHands.dip(sim) : DemoHands.lifted(sim));
            w.workChanged();
            spin(w);
        }))));
    }

    private static void spin(LapidaryWheelBlockEntity w) {
        w.speed = 0.55F;
        w.prevAngle = w.angle;
        w.angle = (w.angle + w.speed) % ((float) Math.PI * 2F);
        w.prevAngle = w.angle - w.speed;
    }

    /** The release lever: the cut stone replaces the work, as the wheel finishes a cut. */
    void finishCut() {
        atWheel(w -> w.work().ifPresent(work -> {
            GemData stone = w.stone().is(ModItems.CUT_GEM.get()) ? CutGemItem.data(w.stone()).orElse(null) : RoughGemItem.data(w.stone()).orElse(null);
            CutDefinition cut = defs().cut(work.cut());
            if (stone == null || cut == null) return;
            CutPlanner.Outcome outcome = CutPlanner.outcome(defs().gemOrGeneric(stone.gem()), cut, stone, work.recut(), work.sim());
            w.setStone(outcome.after() != null ? CutGemItem.create(outcome.after()) : new ItemStack(ModItems.GEM_DUST.get()));
        }));
    }

    void takeStone() {
        atWheel(LapidaryWheelBlockEntity::takeStone);
    }

    /** The stone, with its work, taken into the jeweler's hand. */
    void pocketStone() {
        BlockPos pos = wheel;
        scene.addInstruction(s -> with(s, pos, LapidaryWheelBlockEntity.class, w -> PonderStore.put(s, w.takeStone())));
    }

    /** The carried stone mounted again (on this or another wheel): its work comes with it. */
    void remountStone() {
        BlockPos pos = wheel;
        scene.addInstruction(s -> with(s, pos, LapidaryWheelBlockEntity.class, w -> {
            ItemStack stone = PonderStore.take(s);
            if (!stone.isEmpty()) w.setStone(stone);
        }));
    }

    // ------------------------------------------------------------------------------------------------ the bench

    void clampItem(ItemStack stack) {
        ItemStack copy = stack.copy();
        atBench(b -> b.setClamp(copy));
    }

    /** The finished piece taken from the clamp (collected). */
    void collectFromClamp() {
        atBench(b -> b.setClamp(ItemStack.EMPTY));
    }

    void trayItem(int i, ItemStack stack) {
        ItemStack copy = stack.copy();
        atBench(b -> b.setTray(i, copy));
    }

    void dockItem(ItemStack stack) {
        ItemStack copy = stack.copy();
        atBench(b -> b.setDock(copy));
    }

    void supplyItem(int i, ItemStack stack) {
        ItemStack copy = stack.copy();
        atBench(b -> b.setSupply(i, copy));
    }

    /**
     * A new piece of {@code design}: the work planned in the folio, the tray stones seated in their cups (each its own
     * stone; {@code offsets} is how straight each lands), ready for the first closure.
     */
    void assemble(String design, List<GemData> stones, float offset) {
        atBench(b -> {
            JewelryDesignRecipe recipe = design(design);
            if (recipe == null) return;
            long seed = design.hashCode();
            BenchWork work = new BenchWork(seed, BenchWork.Kind.ASSEMBLE, seed);
            work.setDesign(recipe.getId());
            work.setStage(BenchWork.Stage.SETTING);
            float precision = BenchPlanner.pliersPrecision(b.dock());
            SettingSim sim = new SettingSim(BenchPlanner.settingParams(defs(), recipe, stones.size(), stones, precision,
                    WorkSessionManager.Preferences.NONE, 1F), SettingSim.Mode.SET);
            for (int i = 0; i < stones.size(); i++) {
                b.setTray(i, ItemStack.EMPTY);
                b.setSocket(i, CutGemItem.create(stones.get(i)));
                sim.seat(i, offset * (i % 2 == 0 ? 1 : -1), offset * 0.5F, offset * 4F);
            }
            work.setSetting(sim);
            b.setWork(work);
        });
    }

    /** {@code ticks} of closing prongs or a bezel (or opening them, taking apart) by {@code style} hands. */
    void set(int ticks, DemoHands.Style style) {
        set(ticks, style, STEPS_PER_TICK);
    }

    void set(int ticks, DemoHands.Style style, int steps) {
        BlockPos pos = bench;
        scene.addInstruction(new WorkInstruction(ticks, () -> {
            DemoHands hands = new DemoHands(style);
            return (s, tick) -> with(s, pos, JewelersBenchBlockEntity.class, b -> {
                BenchWork work = b.work();
                if (work == null || work.setting() == null) return;
                SettingSim sim = work.setting();
                for (int i = 0; i < steps; i++) sim.step(DemoHands.settled(sim) ? DemoHands.release(sim) : hands.setting(sim));
                b.workChanged();
            });
        }));
    }

    /** Confirming the setting: the chosen finish begins (its material is charged at the first effective stroke). */
    void beginFinish(String finish) {
        atBench(b -> {
            BenchWork work = b.work();
            FinishDefinition def = defs().finish(ImmersiveJewelry.id(finish));
            if (work == null || def == null) return;
            work.setStage(BenchWork.Stage.FINISHING);
            work.setFinish(def.id());
            work.setSurface(new SurfaceSim(BenchPlanner.surfaceParams(defs(), SurfaceParams.Mode.forFinish(def.visualProfile()), def, work.seed(), 0F,
                    WorkSessionManager.Preferences.NONE)));
            b.setWork(work);
        });
    }

    /** {@code ticks} of finishing (or repair) strokes. {@link DemoHands.Style#STILL} rubs one spot. */
    void rub(int ticks, DemoHands.Style style) {
        rub(ticks, style, STEPS_PER_TICK);
    }

    void rub(int ticks, DemoHands.Style style, int steps) {
        BlockPos pos = bench;
        scene.addInstruction(new WorkInstruction(ticks, () -> {
            DemoHands hands = new DemoHands(style);
            return (s, tick) -> with(s, pos, JewelersBenchBlockEntity.class, b -> {
                BenchWork work = b.work();
                if (work == null || work.surface() == null) return;
                SurfaceSim sim = work.surface();
                for (int i = 0; i < steps; i++) sim.step(hands.surface(sim));
                if (!work.finishPaid() && sim.started() && work.kind() != BenchWork.Kind.REPAIR) {
                    // The material is used once, at the first effective stroke (as the bench charges it).
                    work.markFinishPaid();
                    for (int j = 0; j < JewelersBenchBlockEntity.SUPPLIES; j++) {
                        ItemStack supply = b.supply(j);
                        if (!supply.isEmpty() && !supply.is(ModItems.UNFINISHED_JEWELRY.get())) {
                            b.setSupply(j, supply.copyWithCount(supply.getCount() - 1));
                            break;
                        }
                    }
                }
                b.workChanged();
            });
        }));
    }

    /** The finished piece replaces the work in the clamp, as the bench completes an assembly. */
    void completePiece() {
        atBench(b -> {
            BenchWork work = b.work();
            if (work == null || work.setting() == null) return;
            List<GemData> stones = new ArrayList<>();
            for (int i = 0; i < JewelersBenchBlockEntity.SOCKETS; i++) {
                ItemStack socket = b.socket(i);
                CutGemItem.data(socket).ifPresent(stones::add);
                b.setSocket(i, ItemStack.EMPTY);
            }
            JewelryDesignRecipe recipe = design(work.design() != null ? work.design().getPath().replace("designs/", "") : "");
            BlankData frame = BlankData.get(b.clamp()).orElse(BlankData.of(gold(), 0.72F));
            float setting = work.setting().quality();
            float cut = 0F;
            for (GemData g : stones) cut += g.cutQuality();
            cut = stones.isEmpty() ? 0F : cut / stones.size();
            EnumSet<SettingDefect> defects = work.setting().defects();
            JewelryData data = new JewelryData(recipe != null ? recipe.form() : ImmersiveJewelry.id("ring"), frame.metal(), stones, work.design(),
                    work.finish(), frame.smithingQuality(), setting, Workmanship.compute(frame.smithingQuality(), cut, setting), 1F, 0, defects,
                    null, false, null);
            b.setWork(null);
            b.setClamp(JewelryFactory.stack(data));
        });
    }

    /** A rework of the piece in the clamp begun from the folio's rework page, set up as the bench sets it up. */
    void rework(BenchWork.Kind kind, @Nullable String finish) {
        atBench(b -> {
            JewelryData data = JewelryData.get(b.clamp()).orElse(null);
            if (data == null) return;
            long seed = kind.ordinal() * 977L + 13L;
            BenchWork work = new BenchWork(seed, kind, seed);
            work.setDesign(data.design());
            JewelryDesignRecipe recipe = data.design() != null ? design(data.design().getPath().replace("designs/", "")) : null;
            switch (kind) {
                case RETIGHTEN, REMOVE -> {
                    SettingSim sim = new SettingSim(BenchPlanner.settingParams(defs(), recipe, data.gems().size(), data.gems(),
                            BenchPlanner.pliersPrecision(b.dock()), WorkSessionManager.Preferences.NONE, 1F),
                            kind == BenchWork.Kind.REMOVE ? SettingSim.Mode.REMOVE : SettingSim.Mode.RETIGHTEN);
                    BenchPlanner.restore(sim, data, seed, kind == BenchWork.Kind.REMOVE);
                    work.setSetting(sim);
                    if (kind == BenchWork.Kind.RETIGHTEN) work.setRetightenBaseline(data.settingQuality(), sim.quality());
                    else work.setOriginal(data.gems());
                }
                case REPAIR -> {
                    float cap = BenchPlanner.repairCap(data.reworks());
                    float damage = (cap - data.integrity()) / Math.max(0.05F, cap);
                    work.setSurface(new SurfaceSim(BenchPlanner.surfaceParams(defs(), SurfaceParams.Mode.REPAIR, null, seed, damage,
                            WorkSessionManager.Preferences.NONE)));
                }
                case REFINISH -> {
                    FinishDefinition def = defs().finish(ImmersiveJewelry.id(finish == null ? "burnished" : finish));
                    if (def == null) return;
                    work.setFinish(def.id());
                    work.setSurface(new SurfaceSim(BenchPlanner.surfaceParams(defs(), SurfaceParams.Mode.forFinish(def.visualProfile()), def, seed, 0F,
                            WorkSessionManager.Preferences.NONE)));
                }
                default -> {
                    return;
                }
            }
            b.setWork(work);
        });
    }

    /** Completes the rework under way, with the consequences the bench gives it. */
    void completeRework() {
        atBench(b -> {
            BenchWork work = b.work();
            JewelryData data = JewelryData.get(b.clamp()).orElse(null);
            if (work == null || data == null) return;
            float cap = BenchPlanner.repairCap(data.reworks());
            JewelryData next = switch (work.kind()) {
                case RETIGHTEN -> {
                    SettingSim sim = work.setting();
                    float setting = Math.max(0F, Math.min(1F, work.priorSetting() + (sim.quality() - work.baseline())));
                    yield data.withSetting(setting, Workmanship.compute(data.smithingQuality(), data.cutQuality(), setting), sim.defects());
                }
                case REPAIR -> reworked(data.withIntegrity(cap));
                case REFINISH -> reworked(data.withFinish(work.finish()));
                default -> data;
            };
            if (work.kind() == BenchWork.Kind.REMOVE) {
                SettingSim sim = work.setting();
                BlankData frame = BenchPlanner.frameAfterRemoval(data, sim != null ? sim.removalQuality() : 1F, false);
                Item blankItem = JewelryFactory.blankItemFor(data.form());
                for (int i = 0; i < data.gems().size() && i < JewelersBenchBlockEntity.TRAY; i++) b.setTray(i, CutGemItem.create(data.gems().get(i)));
                ItemStack blank = blankItem == null ? ItemStack.EMPTY : new ItemStack(blankItem);
                if (!blank.isEmpty()) frame.apply(blank);
                b.setWork(null);
                b.setClamp(blank);
                return;
            }
            ItemStack result = b.clamp().copy();
            next.apply(result);
            b.setWork(null);
            b.setClamp(result);
        });
    }

    private static JewelryData reworked(JewelryData data) {
        return new JewelryData(data.form(), data.metal(), data.gems(), data.design(), data.finish(), data.smithingQuality(), data.settingQuality(),
                data.workmanship(), data.integrity(), data.reworks() + 1, data.defects(), data.maker(), data.legacyConverted(), data.smithing());
    }

    /** A worn frame (for the repair lesson): integrity lowered, as heavy hits leave it. */
    void worn(float integrity, int reworks, EnumSet<SettingDefect> defects) {
        atBench(b -> JewelryData.get(b.clamp()).ifPresent(data -> {
            ItemStack worn = b.clamp().copy();
            JewelryData next = new JewelryData(data.form(), data.metal(), data.gems(), data.design(), data.finish(), data.smithingQuality(),
                    defects.isEmpty() ? data.settingQuality() : data.settingQuality() * 0.8F, data.workmanship(), integrity, reworks,
                    defects, data.maker(), false, data.smithing());
            next.apply(worn);
            b.setClamp(worn);
        }));
    }

    /** Packing the work into an Unfinished piece (it leaves the bench), and unpacking it again. */
    void pack(Vec3 dropAt) {
        BlockPos pos = bench;
        scene.addInstruction(s -> with(s, pos, JewelersBenchBlockEntity.class, b -> {
            ItemStack carrier = b.pack();
            if (!carrier.isEmpty()) PonderStore.put(s, carrier);
        }));
    }

    void unpack() {
        BlockPos pos = bench;
        scene.addInstruction(s -> with(s, pos, JewelersBenchBlockEntity.class, b -> {
            ItemStack carrier = PonderStore.take(s);
            if (!carrier.isEmpty()) b.unpack(carrier);
        }));
    }

    static ItemStack chipped(GemData stone) {
        return CutGemItem.create(stone.withDamage(GemDamage.CHIPPED));
    }
}
