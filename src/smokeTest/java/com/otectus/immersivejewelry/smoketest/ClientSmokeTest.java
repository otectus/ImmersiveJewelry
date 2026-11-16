package com.otectus.immersivejewelry.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.block.StationBlock;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.client.compat.jei.DesignCategory;
import com.otectus.immersivejewelry.client.compat.jei.GemCuttingCategory;
import com.otectus.immersivejewelry.client.compat.jei.ReferenceCategory;
import com.otectus.immersivejewelry.client.workstation.SceneLoader;
import com.otectus.immersivejewelry.client.workstation.WorkstationClient;
import com.otectus.immersivejewelry.client.workstation.WorkstationKeys;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.ClientHooks;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import com.otectus.immersivejewelry.workstation.sim.FacetParams;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drives a dev client from the title screen into a fresh flat world and plays the hands-on route through the real
 * client and server: walk-up placement, the focused stations (Diegesis), the scene behaviours, the server's leases and
 * verdicts. Keys go through Minecraft's own keyboard handler and scene events through the behaviour Diegesis holds, so
 * every step takes the path a player's input takes. Diamond on the wheel: a round cut planned at the specimen rack, a
 * pointer stroke on the handle, keyboard grinding, leaving mid-cut and resuming, the loupe, finishing and taking the
 * stone. A solitaire ring at the bench: walk-up custody, the folio, tweezers, the framing at other window shapes, pliers
 * on every prong, the move on to the finish, the finish by hand, the loupe and taking the ring. Then the accessibility
 * options on a second stone, Jade, the handbook, tooltips, the art and JEI. No crafting screen is ever opened. Saves
 * {@code run/screenshots/smoketest_*.png} and {@code smoketest-result.json}, then quits.
 *
 * <p>The window should be left alone while it runs: real mouse motion over it reaches the focused scene too.</p>
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static final long START = Util.getMillis();
    static final ResourceLocation GOLD = new ResourceLocation("minecraft", "gold");
    static final ResourceLocation SILVER = new ResourceLocation("forge", "silver");
    private static int titleTicks;
    private static int worldTicks = -1;
    private static boolean worldRequested;
    private static boolean finished;
    static BlockPos base;

    private interface Step {
        /** One tick of a step; true when it is done. */
        boolean run(Minecraft mc, int ticks);
    }

    private record Phase(String name, int timeout, Step step) {}

    private static final List<Phase> SCRIPT = new ArrayList<>();
    private static int phase;
    private static int phaseTicks;
    private static long phaseStart;
    private static final Map<String, Long> timings = new LinkedHashMap<>();
    private static final List<Long> frameNanos = new ArrayList<>();
    private static final List<Long> renderNanos = new ArrayList<>();
    private static long lastFrame;
    private static final Set<Integer> held = new LinkedHashSet<>();
    private static Vector3f grip;
    private static ResourceLocation chosenFinish;
    private static int resultAt = -1;
    private static int lastAction = -100;
    private static int cools;
    private static int enteredAt = -1;
    private static int guiScale;
    private static int focusShot = -1;
    private static long scriptStart;

    static BlockPos wheel() { return base.offset(-1, 0, -2); }
    static BlockPos bench() { return base.offset(2, 0, -2); }

    // ================================================================================================ the loop

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (OUTPUT == null || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen && ++titleTicks == 1)
                    sizeWindow(mc, ShowcaseCapture.enabled() ? 1920 : 1280, ShowcaseCapture.enabled() ? 1080 : 720);
                if (mc.screen instanceof TitleScreen && titleTicks == 40 && !worldRequested) {
                    worldRequested = true;
                    checks.put("title_ms", Util.getMillis() - START);
                    String language = System.getenv("IJ_SMOKE_LANGUAGE");
                    if (language != null && !language.equals(mc.options.languageCode)) {
                        mc.options.languageCode = language;
                        mc.getLanguageManager().setSelected(language);
                        mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> createWorld(mc)));
                    } else {
                        createWorld(mc);
                    }
                }
                return;
            }
            worldTicks++;
            // The agent plays with the window unattended: losing focus to the desktop must not let go of its keys, and a
            // pause menu (which pauses the integrated server) is never wanted.
            mc.setWindowActive(true);
            if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                log("closing an unexpected pause menu");
                mc.setScreen(null);
            }
            if (worldTicks < 40) return;
            if (worldTicks == 40) {
                scriptStart = Util.getMillis();
                WorkstationArmChecks.checkGeometry(mc, checks);
                if (ShowcaseCapture.enabled()) ShowcaseCapture.prepare(mc);
            }
            if (ShowcaseCapture.enabled()) ShowcaseCapture.tick(mc);
            if (System.getenv("IJ_STATION_VISUALS_ONLY") != null) {
                if (WorkstationVisualChecks.tick(mc, checks)) finish(mc, allPassed(), "workstation visual checks only");
                return;
            }
            if (WorkstationBenchmark.enabled()) {
                WorkstationBenchmark.tick(mc);
                return;
            }
            if (SCRIPT.isEmpty()) {
                if (System.getenv("IJ_DESIGN_VISUALS_ONLY") != null) designVisualScript();
                else if (System.getenv("IJ_LIBRARIES_ONLY") != null) libraryScript();
                else {
                    script();
                    libraryPhases();
                }
            }
            if (phase >= SCRIPT.size()) {
                if (System.getenv("IJ_LIBRARIES_ONLY") != null) {
                    finish(mc, allPassed(), "completed library checks only");
                    return;
                }
                if (System.getenv("IJ_DESIGN_VISUALS_ONLY") != null) {
                    finish(mc, allPassed(), "completed design visuals only");
                    return;
                }
                if (System.getenv("IJ_STATION_VISUALS") != null) {
                    if (WorkstationVisualChecks.tick(mc, checks)) finish(mc, allPassed(), "complete with workstation visual checks");
                    return;
                }
                finish(mc, allPassed(), "complete");
                return;
            }
            Phase current = SCRIPT.get(phase);
            if (phaseTicks == 0) {
                phaseStart = Util.getMillis();
                lastAction = -100; // pacing of discrete presses restarts with every phase's own tick count
                log("phase " + current.name() + " :: " + state(mc));
            }
            boolean done = current.step().run(mc, phaseTicks++);
            if (done || phaseTicks > current.timeout()) {
                if (!done) {
                    checks.put("phase_" + current.name(), false);
                    log("phase " + current.name() + " TIMED OUT :: " + state(mc));
                }
                timings.put(current.name(), Util.getMillis() - phaseStart);
                phase++;
                phaseTicks = 0;
                // Evidence survives an interrupted run (a closed window): the result so far, marked incomplete.
                writeResult(false, "incomplete: after phase " + current.name());
            }
        } catch (Exception e) {
            ImmersiveJewelry.LOGGER.error("SMOKETEST failed in phase {}", phase, e);
            checks.put("agent_exception", e.toString());
            finish(mc, false, "agent exception");
        }
    }

    /** Frame interval and render-thread time (render tick start to end) while a station is focused. */
    @SubscribeEvent
    public static void onRender(TickEvent.RenderTickEvent event) {
        if (finished || OUTPUT == null) return;
        long now = Util.getNanos();
        if (event.phase == TickEvent.Phase.START) {
            if (lastFrame != 0L && DiegesisClient.isFocused() && frameNanos.size() < 20_000) frameNanos.add(now - lastFrame);
            lastFrame = now;
        } else if (DiegesisClient.isFocused() && lastFrame != 0L) {
            if (renderNanos.size() < 20_000) renderNanos.add(now - lastFrame);
            WorkstationContinuityChecks.renderFrame(Minecraft.getInstance(), checks);
        }
    }

    // ================================================================================================ the script

    /** The 1.2.2 libraries alone (IJ_LIBRARIES_ONLY): staged world, then {@link #libraryPhases}. */
    private static void libraryScript() {
        add("stage", 40, (mc, t) -> {
            if (t == 0) {
                checks.put("world_ms", Util.getMillis() - START);
                mc.options.pauseOnLostFocus = false;
                mc.options.enableVsync().set(false);
                mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(120);
                checks.put("vsync_off_for_agent", true);
                clientChecks(mc);
                stageWorld(mc);
            }
            return t > 20;
        });
        libraryPhases();
    }

    /**
     * Patchouli, Ponder, GeckoLib and playerAnimator in a real client: the assets load, the handbook opens at the book and at
     * entries with live cards, a demonstration compiles and plays and returns to the book, the station rigs draw, and the
     * jeweler's body layer poses a working player.
     */
    private static void libraryPhases() {
        add("library_assets", 40, (mc, t) -> {
            if (t != 2) return t > 4;
            var models = software.bernie.geckolib.cache.GeckoLibCache.getBakedModels();
            var anims = software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations();
            boolean rigs = true;
            for (String rig : new String[]{"lapidary_wheel", "jewelers_bench", "bench_tools"}) {
                rigs &= models.containsKey(ImmersiveJewelry.id("geo/" + rig + ".geo.json"));
                rigs &= anims.containsKey(ImmersiveJewelry.id("animations/" + rig + ".animation.json"));
            }
            checks.put("gecko_rigs_baked", rigs);
            boolean clips = true;
            for (var clip : com.otectus.immersivejewelry.client.presentation.anim.JewelerClip.values()) {
                clips &= dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry.getAnimation(clip.id(false)) != null;
                clips &= dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry.getAnimation(clip.id(true)) != null;
            }
            checks.put("player_clips_loaded", clips);
            List<String> problems = com.otectus.immersivejewelry.client.compat.ponder.JewelryPonder.compileAll();
            checks.put("ponder_lessons_compile", problems.isEmpty());
            if (!problems.isEmpty()) log("ponder problems: " + problems);
            checks.put("patchouli_book_registered", !vazkii.patchouli.api.PatchouliAPI.get().getBookStack(
                    com.otectus.immersivejewelry.client.guide.HandbookNavigator.BOOK).isEmpty());
            return false;
        });
        add("rigs_draw", 80, (mc, t) -> {
            if (t == 0) {
                mc.options.hideGui = true;
                stand(mc, bench(), 2.6, 35F);
            }
            if (t == 60) shot(mc, "smoketest_rigs");
            if (t == 70) {
                var rigs = com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs.class;
                checks.put("gecko_rigs_draw", com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs.WHEEL.working()
                        && com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs.BENCH.working()
                        && com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs.TOOLS.working());
                mc.options.hideGui = false;
                return true;
            }
            return false;
        });
        add("handbook_book", 80, (mc, t) -> {
            if (t == 2) com.otectus.immersivejewelry.client.guide.HandbookNavigator.openBook();
            if (t == 40) shot(mc, "smoketest_handbook");
            if (t == 45) {
                checks.put("handbook_opens", com.otectus.immersivejewelry.client.guide.HandbookNavigator.BOOK.equals(
                        vazkii.patchouli.api.PatchouliAPI.get().getOpenBookGui()));
                return true;
            }
            return false;
        });
        String[][] entries = {{"stones/gems", "1"}, {"designs/atlas", "6"}, {"wheel/cuts", "4"}, {"finishing/finishes", "4"}, {"start/controls", "1"},
                {"metal/blanks", "3"}};
        for (String[] entry : entries) {
            add("handbook_" + entry[0].replace('/', '_'), 60, (mc, t) -> {
                if (t == 2) com.otectus.immersivejewelry.client.guide.HandbookNavigator.openEntry(ImmersiveJewelry.id(entry[0]),
                        Integer.parseInt(entry[1]));
                if (t == 30) shot(mc, "smoketest_handbook_" + entry[0].replace('/', '_'));
                if (t == 34) {
                    checks.put("handbook_entry_" + entry[0].replace('/', '_'), com.otectus.immersivejewelry.client.guide.HandbookNavigator.BOOK
                            .equals(vazkii.patchouli.api.PatchouliAPI.get().getOpenBookGui()));
                    return true;
                }
                return false;
            });
        }
        add("ponder_watch", 260, (mc, t) -> {
            if (t == 2) com.otectus.immersivejewelry.client.guide.HandbookNavigator.watch(com.otectus.immersivejewelry.client.guide.Lessons.FIRST_PIECE,
                    ImmersiveJewelry.id("start/first_piece"), 4);
            if (t == 20) checks.put("ponder_opens", mc.screen instanceof net.createmod.ponder.foundation.ui.AbstractPonderScreen);
            if (t == 220) shot(mc, "smoketest_ponder_first_piece");
            if (t == 230) {
                mc.setScreen(null);
                return true;
            }
            return false;
        });
        add("ponder_returns_to_book", 60, (mc, t) -> {
            if (t == 30) {
                checks.put("ponder_returns_to_book", com.otectus.immersivejewelry.client.guide.HandbookNavigator.BOOK.equals(
                        vazkii.patchouli.api.PatchouliAPI.get().getOpenBookGui()));
                mc.setScreen(null);
                return true;
            }
            return false;
        });
        for (var lesson : new com.otectus.immersivejewelry.client.guide.Lessons[]{com.otectus.immersivejewelry.client.guide.Lessons.ROUND_CUT,
                com.otectus.immersivejewelry.client.guide.Lessons.ATLAS_HALO_AMULET, com.otectus.immersivejewelry.client.guide.Lessons.ANTIQUE_FINISH}) {
            add("ponder_" + lesson.id(), 300, (mc, t) -> {
                if (t == 2) com.otectus.immersivejewelry.client.guide.HandbookNavigator.watch(lesson, null, 0);
                if (t == 280) shot(mc, "smoketest_ponder_" + lesson.id());
                if (t == 290) {
                    checks.put("ponder_plays_" + lesson.id(), mc.screen instanceof net.createmod.ponder.foundation.ui.AbstractPonderScreen);
                    mc.setScreen(null);
                    return true;
                }
                return false;
            });
        }
        add("body_pose_at_bench", 160, (mc, t) -> {
            if (t == 0) {
                stand(mc, bench(), 2.0, 42F);
                give(mc, ItemStack.EMPTY);
            }
            if (t == 10 || (t == 80 && !DiegesisClient.isFocused())) use(mc, bench());
            if (t > 30 && DiegesisClient.isFocused() && mc.player != null
                    && com.otectus.immersivejewelry.client.presentation.anim.JewelerPoses.posing(mc.player)) {
                checks.put("body_pose_while_operating", true);
                mc.setScreen(null);
                return true;
            }
            if (t == 150) checks.put("body_pose_while_operating", false);
            return false;
        });
        add("body_pose_released", 60, (mc, t) -> {
            if (t == 40) {
                checks.put("body_pose_ends_with_lease", mc.player != null
                        && !com.otectus.immersivejewelry.client.presentation.anim.JewelerPoses.posing(mc.player));
                return true;
            }
            return false;
        });
        add("help_key_from_station", 160, (mc, t) -> {
            if (t == 0) stand(mc, wheel(), 2.0, 40F);
            if (t == 10 || (t == 70 && !DiegesisClient.isFocused())) use(mc, wheel());
            if (t == 100 && DiegesisClient.isFocused()) {
                WorkstationClient.focusedStation().ifPresent(station ->
                        com.otectus.immersivejewelry.client.guide.HandbookNavigator.help(station, WorkstationClient::releaseHeld, false, false));
            }
            if (t == 150) {
                checks.put("help_leaves_station_and_opens_book", !DiegesisClient.isFocused()
                        && com.otectus.immersivejewelry.client.guide.HandbookNavigator.BOOK.equals(vazkii.patchouli.api.PatchouliAPI.get().getOpenBookGui()));
                shot(mc, "smoketest_help_from_wheel");
                mc.setScreen(null);
                return true;
            }
            return false;
        });
    }

    /** Fast visual recheck of the completed-design gallery without replaying either minigame. */
    private static void designVisualScript() {
        add("stage", 40, (mc, t) -> {
            if (t == 0) {
                checks.put("world_ms", Util.getMillis() - START);
                mc.options.pauseOnLostFocus = false;
                mc.options.enableVsync().set(false);
                mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(120);
                checks.put("vsync_off_for_agent", true);
                clientChecks(mc);
                stageWorld(mc);
            }
            return t > 20;
        });
        add("enter_bench_gallery", 100, (mc, t) -> {
            if (t == 0) {
                mc.options.hideGui = false;
                stand(mc, bench(), 2.0, 42F);
                give(mc, ItemStack.EMPTY);
            }
            if (t == 10 || (t == 60 && !DiegesisClient.isFocused())) use(mc, bench());
            if (t > 20 && DiegesisClient.isFocused() && WorkstationClient.operates(bench())) {
                checks.put("design_gallery_bench_focus", true);
                return true;
            }
            return false;
        });
        add("design_gallery", 280, (mc, t) -> WorkstationDesignChecks.tick(mc, checks, t));
    }

    private static void script() {
        add("stage", 40, (mc, t) -> {
            if (t == 0) {
                checks.put("world_ms", Util.getMillis() - START);
                mc.options.pauseOnLostFocus = false;
                // An unattended window (another workspace, covered) gets its vsync'd frames throttled by the compositor,
                // and the hands-on stations advance with frames: render unsynchronised, capped.
                mc.options.enableVsync().set(false);
                mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(120);
                checks.put("vsync_off_for_agent", true);
                clientChecks(mc);
                stageWorld(mc);
            }
            return t > 20;
        });
        if (ShowcaseCapture.overviewOnly()) {
            for (int angle = 0; angle < 4; angle++) {
                int selectedAngle = angle;
                add("showcase_overview_" + angle, 60, (mc, t) -> {
                    if (t == 0) ShowcaseCapture.overview(mc, selectedAngle);
                    if (t == 40) shot(mc, "showcase_workshop_angle_" + selectedAngle);
                    return t > 44;
                });
            }
            add("enter_showcase_designs", 100, (mc, t) -> {
                if (t == 0) { stand(mc, bench(), 2.0, 42F); give(mc, ItemStack.EMPTY); }
                if (t == 10 || (t == 60 && !DiegesisClient.isFocused())) use(mc, bench());
                return t > 20 && DiegesisClient.isFocused() && WorkstationClient.operates(bench());
            });
            add("showcase_designs", 280, (mc, t) -> WorkstationDesignChecks.tick(mc, checks, t));
            return;
        }
        add("overview", 40, (mc, t) -> {
            if (t == 0) {
                mc.options.hideGui = true;
                if (ShowcaseCapture.enabled()) ShowcaseCapture.overview(mc);
                else stand(mc, wheel(), 3.4, 30F);
            }
            if (t == 30) shot(mc, "smoketest_stations");
            return t > 32;
        });

        // ---------------------------------------------------------------------------------------- the wheel
        add("mount_walk_up", 60, (mc, t) -> {
            if (t == 0) {
                mc.options.hideGui = false;
                stand(mc, wheel(), 2.0, 40F);
                give(mc, new ItemStack(Items.DIAMOND));
            }
            if (t == 10) use(mc, wheel());
            if (t > 14 && clientWheel(mc).stone().getItem() instanceof RoughGemItem) {
                checks.put("wheel_mounted_and_appraised", RoughGemItem.data(clientWheel(mc).stone()).isPresent());
                return true;
            }
            return false;
        });
        add("enter_wheel", 80, (mc, t) -> {
            if (t == 0) give(mc, ItemStack.EMPTY);
            if (t == 6) use(mc, wheel());
            if (t > 20 && DiegesisClient.isFocused() && WorkstationClient.operates(wheel()) && focusShot < 0) focusShot = t;
            if (focusShot > 0 && t == focusShot + 30) shot(mc, "smoketest_wheel_focus");
            if (focusShot > 0 && t > focusShot + 32) {
                checks.put("wheel_focus_and_lease", true);
                checks.put("wheel_no_own_screen", mc.screen != null && !mc.screen.getClass().getName().startsWith("com.otectus"));
                return true;
            }
            return false;
        });
        add("plan_round", 60, (mc, t) -> {
            int round = Math.max(0, cutsForStone(mc).indexOf(ImmersiveJewelry.id("round")));
            if (t == 6) {
                checks.put("specimen_rack_offers_round", cutsForStone(mc).contains(ImmersiveJewelry.id("round")));
                event(new SceneEvent.Hover("specimen_" + round));
            }
            if (t == 12) shot(mc, "smoketest_wheel_specimen");
            if (t == 14) event(new SceneEvent.Click("specimen_" + round, 0));
            CutWork work = clientWheel(mc).work().orElse(null);
            if (work != null && t > 16) {
                event(new SceneEvent.Hover(null));
                checks.put("wheel_planned_round", work.cut().equals(ImmersiveJewelry.id("round")));
                return true;
            }
            return false;
        });
        add("handle_stroke", 80, (mc, t) -> {
            // The pointer path: take the handle, bring the stone down onto the lap and sweep it across.
            StationView view = WorkstationClient.focusedView().orElse(null);
            if (view == null) return false;
            if (t == 2) grip = view.node("handle").map(n -> new Vector3f(n.scenePosition())).orElse(new Vector3f(5F, 19F, 6F));
            if (grip == null) return false;
            if (t == 2) event(new SceneEvent.Stroke("handle", SceneEvent.Stroke.Phase.START, 0, grip));
            if (t > 2 && t < 50) {
                float down = Math.min(0.15F * (t - 2), 3.2F);
                float across = (float) Math.sin(t * 0.15) * 1.6F;
                event(new SceneEvent.Stroke("handle", SceneEvent.Stroke.Phase.MOVE, 0, new Vector3f(grip).add(across, -down, 0F)));
            }
            if (t == 36) shot(mc, "smoketest_wheel_contact");
            if (t == 50) event(new SceneEvent.Stroke("handle", SceneEvent.Stroke.Phase.END, 0, grip));
            if (t == 62) {
                CutWork work = clientWheel(mc).work().orElse(null);
                checks.put("pointer_stroke_grinds", work != null && work.started());
                return true;
            }
            return false;
        });
        add("grind_half", 20 * 90, (mc, t) -> continuityGrind(mc, t, 0.45F));
        add("leave_mid_cut", 80, (mc, t) -> {
            if (t == 0) releaseKeys(mc);
            if ((t == 4 || t == 24 || t == 44 || t == 64) && DiegesisClient.isFocused()) {
                log("leave_mid_cut escape before t=" + t + " " + focusExitState(mc));
                escape(mc);
                log("leave_mid_cut escape after t=" + t + " " + focusExitState(mc));
            }
            if (t > 40 && !DiegesisClient.isFocused()) {
                CutWork work = clientWheel(mc).work().orElse(null);
                checks.put("leaving_keeps_the_work", work != null && work.started() && !work.sim().contact());
                checks.put("leaving_ends_the_lease", !WorkstationClient.operates(wheel()) && !clientWheel(mc).isOperated());
                timings.put("progress_at_leave_pct", work == null ? -1L : Math.round(work.sim().overallProgress() * 100));
                return true;
            }
            return false;
        });
        add("resume_wheel", 80, (mc, t) -> {
            if (t == 2) use(mc, wheel());
            if (t > 20 && DiegesisClient.isFocused() && WorkstationClient.operates(wheel())) {
                checks.put("resume_after_leaving", clientWheel(mc).work().map(CutWork::started).orElse(false));
                return true;
            }
            return false;
        });
        add("grind_rest", 20 * 150, (mc, t) -> continuityGrind(mc, t, 1.01F));
        add("inspect_stone", 80, (mc, t) -> {
            if (t == 0) releaseKeys(mc);
            if (t == 6) press(mc, WorkstationKeys.INSPECT);
            if (t == 36) shot(mc, "smoketest_wheel_loupe");
            if (t == 38) {
                checks.put("loupe_overlay_shown", overlayShown());
                key(mc, GLFW.GLFW_KEY_ESCAPE, true);
                key(mc, GLFW.GLFW_KEY_ESCAPE, false);
            }
            if (t == 60) {
                checks.put("escape_closes_only_the_loupe", !overlayShown() && DiegesisClient.isFocused());
                return true;
            }
            return false;
        });
        add("finish_cut", 100, (mc, t) -> {
            if (t == 2) {
                CutWork work = clientWheel(mc).work().orElse(null);
                checks.put("cut_ready_before_finish", work != null && work.sim().ready());
                press(mc, WorkstationKeys.CONFIRM);
            }
            if (t == 8 && clientWheel(mc).work().isPresent()) press(mc, WorkstationKeys.CONFIRM); // accepts a rough result
            if (t > 10 && clientWheel(mc).stone().getItem() instanceof CutGemItem && clientWheel(mc).work().isEmpty()) {
                GemData cut = CutGemItem.data(clientWheel(mc).stone()).orElse(null);
                checks.put("cut_quality", cut == null ? -1F : cut.cutQuality());
                checks.put("cut_is_round", cut != null && ImmersiveJewelry.id("round").equals(cut.cut()));
                checks.put("cut_quality_usable", cut != null && cut.cutQuality() >= 0.5F);
                return true;
            }
            return false;
        });
        add("take_stone", 60, (mc, t) -> {
            if (t == 10) shot(mc, "smoketest_wheel_finished");
            if (t == 12) event(new SceneEvent.Click("stone", 0));
            if (t > 16 && !clientWheel(mc).hasStone()) {
                checks.put("cut_stone_in_inventory", count(mc, ModItems.CUT_GEM.get()) == 1);
                return true;
            }
            return false;
        });
        add("leave_wheel", 60, (mc, t) -> {
            if (t == 2 || t == 22) escape(mc);
            return t > 30 && !DiegesisClient.isFocused();
        });

        // ---------------------------------------------------------------------------------------- the bench
        add("bench_walk_up", 120, (mc, t) -> {
            if (t == 0) stand(mc, bench(), 2.0, 42F);
            if (t == 4) give(mc, JewelryFactory.blank(ModItems.RING_BLANK.get(), JewelryFactory.metal(JewelryDefinitions.client(), GOLD), 0.85F));
            if (t == 10) use(mc, bench());
            if (t == 16) give(mc, new ItemStack(ModItems.JEWELERS_PLIERS.get()));
            if (t == 22) use(mc, bench());
            if (t == 28) server(mc, (server, player) -> handCutGem(player));
            if (t == 34) use(mc, bench());
            if (t == 40) give(mc, new ItemStack(ModItems.POLISHING_COMPOUND.get()));
            if (t == 46) use(mc, bench());
            if (t == 52) give(mc, ItemStack.EMPTY);
            if (t > 56) {
                JewelersBenchBlockEntity b = clientBench(mc);
                boolean ok = b.clamp().is(ModItems.RING_BLANK.get()) && !b.dock().isEmpty() && b.tray(0).is(ModItems.CUT_GEM.get())
                        && b.supply(0).is(ModItems.POLISHING_COMPOUND.get());
                if (ok || t > 100) {
                    checks.put("bench_walk_up_custody", ok);
                    return true;
                }
            }
            return false;
        });
        add("enter_bench", 120, (mc, t) -> {
            if (t == 2 || (t == 60 && !DiegesisClient.isFocused())) use(mc, bench());
            if (t > 20 && DiegesisClient.isFocused() && WorkstationClient.operates(bench())) {
                checks.put("bench_focus_and_lease", true);
                return true;
            }
            return false;
        });
        add("diegesis_beta10", 160, ClientSmokeTest::diegesisBeta10);
        add("folio_design", 80, (mc, t) -> {
            if (t == 4) shot(mc, "smoketest_bench_focus");
            if (t == 6) press(mc, WorkstationKeys.FOLIO);
            if (t == 20) {
                checks.put("folio_overlay_shown", overlayShown());
                shot(mc, "smoketest_bench_folio");
            }
            if (t == 22) press(mc, WorkstationKeys.CONFIRM);
            if (t == 30) press(mc, WorkstationKeys.FOLIO);
            BenchWork work = clientBench(mc).work();
            if (t > 34 && work != null) {
                checks.put("folio_chose_solitaire", ImmersiveJewelry.id("designs/solitaire_ring").equals(work.design()));
                checks.put("folio_closed", !overlayShown());
                chosenFinish = work.finish();
                checks.put("folio_default_finish", chosenFinish != null);
                return true;
            }
            return false;
        });
        add("tweezers_seat", 80, (mc, t) -> {
            if (t == 2) press(mc, WorkstationKeys.TOOL);
            if (t == 8) press(mc, WorkstationKeys.NEXT);
            if (t == 10) {
                // A stone in the tweezers: the turn key turns it, also over a stack JEI could look up (the frame in the clamp).
                event(new SceneEvent.Hover("piece"));
                press(mc, WorkstationKeys.TURN);
                checks.put("station_key_beats_lookup", DiegesisClient.isStationScreen(mc.screen) && heldTurn() > 0.2F);
            }
            if (t == 12 && DiegesisClient.isStationScreen(mc.screen)) {
                // One notch of the mouse wheel, through Diegesis' scroll path (StationBehavior.onScroll), turns it back.
                mc.screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, -1.0);
                checks.put("scroll_turns_held_stone", heldTurn() == 0F);
                event(new SceneEvent.Hover(null));
            }
            if (t == 16) shot(mc, "smoketest_bench_tweezers");
            if (t == 18) press(mc, WorkstationKeys.CONFIRM);
            if (t > 24 && clientBench(mc).socket(0).is(ModItems.CUT_GEM.get())) {
                checks.put("stone_seated_with_tweezers", clientBench(mc).tray(0).isEmpty());
                return true;
            }
            return false;
        });
        if (!ShowcaseCapture.enabled()) add("framing", 200, ClientSmokeTest::framing);
        if (WorkstationArmChecks.visualsEnabled()) add("arm_visuals", 140,
                (mc, t) -> WorkstationArmChecks.visualTick(mc, checks, t));
        add("close_prongs", 20 * 120, ClientSmokeTest::keyboardProngs);
        add("on_to_finish", 60, (mc, t) -> {
            if (t == 0) releaseKeys(mc);
            if (t == 6) {
                shot(mc, "smoketest_bench_prongs_closed");
                SettingSim sim = clientBench(mc).work() == null ? null : clientBench(mc).work().setting();
                checks.put("all_prongs_secured", sim != null && sim.allSecured());
            }
            if (t == 8) press(mc, WorkstationKeys.CONFIRM);
            BenchWork work = clientBench(mc).work();
            if (t > 12 && work != null && work.stage() == BenchWork.Stage.FINISHING) {
                checks.put("confirm_moves_on_to_finish", true);
                return true;
            }
            return false;
        });
        add("finish_by_hand", 20 * 90, ClientSmokeTest::keyboardFinish);
        add("ring_result", 80, (mc, t) -> {
            if (t == 0) releaseKeys(mc);
            if (!"NONE".equals(String.valueOf(behaviourField(bench(), "tool")))) {
                if (t % 5 == 2) press(mc, WorkstationKeys.TOOL);
                return false;
            }
            if (resultAt < 0) resultAt = t;
            if (WorkstationContinuityChecks.enabled() && t == resultAt + 8)
                shot(mc, "smoketest_bench_completed_design_closeup");
            if (t == resultAt + 16) shot(mc, "smoketest_bench_finished");
            if (t == resultAt + 18) {
                JewelryData ring = JewelryData.get(clientBench(mc).clamp()).orElse(null);
                checks.put("ring_made", ring != null && clientBench(mc).clamp().is(ModItems.RING.get()));
                if (ring != null) {
                    checks.put("ring_setting_quality", ring.settingQuality());
                    checks.put("ring_workmanship", ring.workmanship());
                    checks.put("ring_design_and_finish", ImmersiveJewelry.id("designs/solitaire_ring").equals(ring.design())
                            && chosenFinish != null && chosenFinish.equals(ring.finish()) && ring.gems().size() == 1);
                    checks.put("ring_defects", ring.defects().toString());
                }
                checks.put("supplies_used_once", clientBench(mc).supply(0).isEmpty());
                checks.put("pliers_stay_docked", !clientBench(mc).dock().isEmpty());
                return true;
            }
            return false;
        });
        add("bench_loupe", 60, (mc, t) -> {
            if (t == 2) press(mc, WorkstationKeys.INSPECT);
            if (t == 30) {
                checks.put("bench_loupe_shown", overlayShown());
                shot(mc, "smoketest_bench_loupe");
                if (WorkstationContinuityChecks.enabled()) shot(mc, "smoketest_bench_completed_loupe_closeup");
            }
            if (t == 32) {
                key(mc, GLFW.GLFW_KEY_ESCAPE, true);
                key(mc, GLFW.GLFW_KEY_ESCAPE, false);
            }
            return t > 50;
        });
        add("take_ring", 80, (mc, t) -> {
            if (t == 2) event(new SceneEvent.Click("piece", 0));
            if (t > 6 && clientBench(mc).clamp().isEmpty()) {
                checks.put("ring_in_inventory", count(mc, ModItems.RING.get()) == 1);
                return true;
            }
            return false;
        });
        if (WorkstationDesignChecks.enabled()) add("design_gallery", 280,
                (mc, t) -> WorkstationDesignChecks.tick(mc, checks, t));
        add("leave_bench", 60, (mc, t) -> {
            if (t == 2 || t == 22) escape(mc);
            if (t > 40) {
                checks.put("bench_exit_restores", !DiegesisClient.isFocused() && mc.screen == null && !WorkstationClient.operates(bench()));
                return true;
            }
            return false;
        });

        if (ShowcaseCapture.enabled()) return;

        // ---------------------------------------------------------------------------------------- accessibility
        add("accessible_wheel", 20 * 20, ClientSmokeTest::accessibleWheel);
        add("jade", 80, (mc, t) -> {
            if (t == 0) {
                mc.options.hideGui = false;
                stand(mc, wheel(), 2.2, 38F);
            }
            if (t == 24) {
                checks.put("jade_loaded", net.minecraftforge.fml.ModList.get().isLoaded("jade"));
                shot(mc, "smoketest_jade_wheel");
            }
            if (t == 26) stand(mc, bench(), 2.2, 38F);
            if (t == 50) shot(mc, "smoketest_jade_bench");
            return t > 52;
        });

        // ---------------------------------------------------------------------------------------- reference screens
        add("handbook", 60, (mc, t) -> {
            if (t == 0) {
                mc.getToasts().clear();
                com.otectus.immersivejewelry.client.guide.HandbookNavigator.openBook();
            }
            if (t == 20) shot(mc, "smoketest_handbook_landing");
            if (t == 22) com.otectus.immersivejewelry.client.guide.HandbookNavigator.openEntry(ImmersiveJewelry.id("wheel/wheel"), 0);
            if (t == 34) shot(mc, "smoketest_handbook_wheel");
            if (t == 36) com.otectus.immersivejewelry.client.guide.HandbookNavigator.openEntry(ImmersiveJewelry.id("setting/bench"), 0);
            if (t == 48) shot(mc, "smoketest_handbook_bench");
            if (t == 50) mc.setScreen(null);
            return t > 52;
        });
        add("sheets", 160, (mc, t) -> {
            if (t == 0) mc.setScreen(new TooltipSheet(0));
            if (t == 10) shot(mc, "smoketest_tooltips");
            if (t == 12) {
                ClientHooks.shiftDown = () -> true;
                mc.setScreen(new TooltipSheet(1));
            }
            if (t == 22) {
                shot(mc, "smoketest_tooltips_shift");
                ClientHooks.shiftDown = Screen::hasShiftDown;
            }
            if (t == 24) mc.setScreen(new ItemSheet());
            if (t == 36) shot(mc, "smoketest_items");
            if (t == 38) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            if (t == 50) shot(mc, "smoketest_inventory");
            if (t == 52) {
                checks.put("art_models", ArtSheet.checkModels(mc));
                mc.setScreen(new ArtSheet());
            }
            if (t == 68) shot(mc, "smoketest_art");
            if (t == 70) mc.setScreen(new ProfessionSheet(mc));
            if (t == 86) shot(mc, "smoketest_professions");
            if (t == 88) mc.setScreen(null);
            return t > 90;
        });
        add("jei", 100, (mc, t) -> {
            if (t == 0) checks.put("jei_runtime", SmokeJeiPlugin.runtime != null);
            if (t == 2) jei(GemCuttingCategory.TYPE);
            if (t == 18) shot(mc, "smoketest_jei_cutting");
            if (t == 20) jei(DesignCategory.TYPE);
            if (t == 36) shot(mc, "smoketest_jei_designs");
            if (t == 38) jei(ReferenceCategory.FINISHING);
            if (t == 54) shot(mc, "smoketest_jei_finishing");
            if (t == 56) jei(ReferenceCategory.REWORKING);
            if (t == 72) shot(mc, "smoketest_jei_reworking");
            if (t == 74) mc.setScreen(null);
            return t > 76;
        });
        add("jewelry_slots", 40, (mc, t) -> {
            boolean builtin = com.otectus.immersivejewelry.equipment.JewelryEquipment.builtinActive();
            if (t == 2 && builtin) {
                com.otectus.immersivejewelry.network.ModNetwork.CHANNEL.sendToServer(new com.otectus.immersivejewelry.network.packet.OpenJewelrySlotsPacket());
            }
            if (t == 14 && builtin) {
                checks.put("equipment_screen", mc.screen instanceof com.otectus.immersivejewelry.client.screen.JewelrySlotsScreen);
                shot(mc, "smoketest_jewelry_slots");
            }
            if (t == 16) mc.setScreen(null);
            return t > 18;
        });
    }

    private static void add(String name, int timeout, Step step) {
        SCRIPT.add(new Phase(name, timeout, step));
    }

    // ================================================================================================ the reference players

    private static boolean continuityGrind(Minecraft mc, int t, float share) {
        if (t == 0) WorkstationContinuityChecks.begin();
        WorkstationContinuityChecks.clientTick(mc);
        boolean done = keyboardGrind(mc, t, share);
        if (done) WorkstationContinuityChecks.pause(checks);
        return done;
    }

    /**
     * Grinds with the keyboard frontend, the way a player using it would: the collar turned ({@code NEXT}) to the face
     * the plan asks for next, contact held with {@code APPLY} (the assisted sweep moves the stone), short touches near the
     * face's depth because what the server reports trails a few ticks behind, the cup ({@code COOL}) when the stone runs
     * warm. Stops at {@code share} of the planned removal, or when every face sits inside its window.
     */
    private static boolean keyboardGrind(Minecraft mc, int t, float share) {
        CutWork work = clientWheel(mc).work().orElse(null);
        if (work == null) return false;
        FacetSim sim = work.sim();
        FacetParams p = work.params();
        if (t % 400 == 200) shot(mc, "smoketest_wheel_grinding");
        int target = -1;
        for (int i : p.order()) {
            if (sim.depth(i) < p.target()[i] - p.window() * 0.3F) {
                target = i;
                break;
            }
        }
        if (sim.overallProgress() >= share || target < 0) {
            hold(mc, WorkstationKeys.APPLY, false);
            return !sim.contact();
        }
        if (sim.hot()) {
            hold(mc, WorkstationKeys.APPLY, false);
            if (!sim.contact() && t - lastAction > 20) {
                press(mc, WorkstationKeys.COOL);
                lastAction = t;
                cools++;
            }
            return false;
        }
        if (sim.region() != target) {
            hold(mc, WorkstationKeys.APPLY, false);
            if (!sim.contact() && t - lastAction > 8) {
                press(mc, WorkstationKeys.NEXT);
                lastAction = t;
            }
            return false;
        }
        float depth = sim.depth(target);
        float aim = p.target()[target];
        if (depth < aim - p.window() * 1.6F) hold(mc, WorkstationKeys.APPLY, true);
        else hold(mc, WorkstationKeys.APPLY, t % 12 < 4);
        return false;
    }

    /**
     * Closes every prong with the keyboard: the pliers ({@code TOOL}), {@code NEXT} round to the prong the setting order
     * asks for, {@code APPLY} to grip and {@code LOWER} to squeeze; the last part in short squeezes, then let go.
     */
    private static boolean keyboardProngs(Minecraft mc, int t) {
        BenchWork work = clientBench(mc).work();
        if (work == null || work.setting() == null) return false;
        SettingSim sim = work.setting();
        if (!"PLIERS".equals(String.valueOf(behaviourField(bench(), "tool")))) {
            releaseKeys(mc);
            if (t % 6 == 0) press(mc, WorkstationKeys.TOOL);
            return false;
        }
        if (t % 300 == 150) shot(mc, "smoketest_bench_prong_work");
        if (t == 90) shot(mc, "smoketest_bench_pliers");
        float tol = sim.params().tolerance();
        int targeted = (Integer) behaviourField(bench(), "targetPoint");
        if (sim.gripping()) {
            float c = sim.closure(0, sim.gripPoint());
            if (c >= 1F + tol * 0.4F) {
                hold(mc, WorkstationKeys.LOWER, false);
                hold(mc, WorkstationKeys.APPLY, false);
            } else {
                hold(mc, WorkstationKeys.APPLY, true);
                hold(mc, WorkstationKeys.LOWER, c < 0.55F || t % 6 == 0);
            }
            return false;
        }
        int open = sim.recommended(0);
        if (open < 0) {
            releaseKeys(mc);
            return work.committed();
        }
        if (targeted != open) {
            releaseKeys(mc);
            if (t - lastAction > 4) {
                press(mc, WorkstationKeys.NEXT);
                lastAction = t;
            }
            return false;
        }
        hold(mc, WorkstationKeys.APPLY, true);
        hold(mc, WorkstationKeys.LOWER, true);
        return false;
    }

    /** Finishes the piece by hand: the tool the finish calls for, {@code APPLY} held (assisted rubbing round the piece). */
    private static boolean keyboardFinish(Minecraft mc, int t) {
        JewelersBenchBlockEntity bench = clientBench(mc);
        BenchWork work = bench.work();
        if (work == null) {
            releaseKeys(mc);
            return bench.clamp().is(ModItems.RING.get());
        }
        SurfaceSim surface = work.surface();
        if (surface == null) return false;
        int id = surface.params().mode().tool(surface.phase());
        String wanted = id == SurfaceParams.TOOL_CLOTH ? "CLOTH" : id == SurfaceParams.TOOL_BURNISHER ? "BURNISHER" : "BRUSH";
        if (!wanted.equals(String.valueOf(behaviourField(bench(), "tool")))) {
            releaseKeys(mc);
            if (t - lastAction > 6) {
                press(mc, WorkstationKeys.TOOL);
                lastAction = t;
            }
            return false;
        }
        hold(mc, WorkstationKeys.APPLY, true);
        if (t % 200 == 100) shot(mc, "smoketest_bench_finishing");
        return false;
    }

    /** The bench shot at other window shapes and GUI scales while focused (the framing must hold up). */
    private static boolean framing(Minecraft mc, int t) {
        if (t == 0) guiScale = mc.options.guiScale().get();
        if (t == 2) sizeWindow(mc, 1024, 768);
        if (t == 40) {
            checks.put("framing_4x3_framebuffer", mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
            shot(mc, "smoketest_bench_4x3");
        }
        if (t == 42) sizeWindow(mc, 1600, 600);
        if (t == 80) {
            checks.put("framing_wide_framebuffer", mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
            shot(mc, "smoketest_bench_wide");
        }
        if (t == 82) {
            sizeWindow(mc, 1280, 720);
            mc.options.guiScale().set(4);
            mc.resizeDisplay();
        }
        if (t == 120) shot(mc, "smoketest_bench_gui4");
        if (t == 122) {
            mc.options.guiScale().set(guiScale);
            mc.resizeDisplay();
        }
        if (t == 140) {
            checks.put("framing_kept_focus", DiegesisClient.isFocused() && WorkstationClient.operates(bench()));
            return true;
        }
        return false;
    }

    /**
     * The accessibility options together: no zoom, instant transitions, numeric guide, high contrast, larger targets,
     * hands off. A second stone mounted at the wheel, the cut planned from the keyboard ({@code TOOL} cycles the rack),
     * a little grinding, then left on the wheel as a work in progress.
     */
    private static boolean accessibleWheel(Minecraft mc, int t) {
        if (t == 0) {
            stand(mc, wheel(), 2.0, 40F);
            give(mc, new ItemStack(Items.EMERALD));
            setAccessible(true);
        }
        if (t == 8) use(mc, wheel());
        if (t == 14) give(mc, ItemStack.EMPTY);
        if (t == 20) use(mc, wheel());
        if (t > 20 && t < 60 && DiegesisClient.isFocused() && enteredAt < 0) {
            enteredAt = t;
            checks.put("instant_transition_ticks", t - 20);
        }
        if (enteredAt > 0) {
            int s = t - enteredAt;
            if (s == 6) press(mc, WorkstationKeys.TOOL);
            if (s == 14) checks.put("keyboard_planned_cut", clientWheel(mc).work().isPresent());
            if (s == 16) hold(mc, WorkstationKeys.APPLY, true);
            if (s == 70) shot(mc, "smoketest_wheel_accessible");
            if (s == 72) hold(mc, WorkstationKeys.APPLY, false);
            if (s == 90) escape(mc);
            if (s == 110 && DiegesisClient.isFocused()) escape(mc);
            if (s > 120 && !DiegesisClient.isFocused()) {
                setAccessible(false);
                checks.put("accessible_work_left_in_progress", clientWheel(mc).work().map(CutWork::started).orElse(false));
                return true;
            }
        }
        if (t > 300) setAccessible(false);
        return false;
    }

    private static void setAccessible(boolean on) {
        ClientConfig.NO_ZOOM.set(on);
        ClientConfig.INSTANT_TRANSITIONS.set(on);
        ClientConfig.NUMERIC_GUIDE.set(on);
        ClientConfig.HIGH_CONTRAST_MINIGAMES.set(on);
        ClientConfig.LARGER_TARGETS.set(on);
        ClientConfig.WORKSTATION_HANDS.set(!on);
        SceneLoader.INSTANCE.refresh();
    }

    // ================================================================================================ input helpers

    private static void event(SceneEvent event) {
        StationView view = WorkstationClient.focusedView().orElse(null);
        if (view == null) return;
        WorkstationClient.behaviourAt(view.pos()).ifPresent(b -> b.onEvent(view, event));
    }

    private static void press(Minecraft mc, KeyMapping key) {
        hold(mc, key, true);
        hold(mc, key, false);
    }

    /** Holds or lets go of a key mapping through Minecraft's own keyboard handler (the path a real key takes). */
    private static void hold(Minecraft mc, KeyMapping mapping, boolean down) {
        int code = mapping.getKey().getValue();
        if (held.contains(code) == down) return;
        if (down && !DiegesisClient.isFocused()) return; // never type into the world (T is chat there)
        if (down) held.add(code);
        else held.remove(code);
        key(mc, code, down);
    }

    private static void key(Minecraft mc, int code, boolean down) {
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), code, 0, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }

    private static void releaseKeys(Minecraft mc) {
        for (int code : List.copyOf(held)) {
            held.remove(code);
            key(mc, code, false);
        }
    }

    /**
     * Escape, as pressed at a station: closes an overlay first, otherwise leaves the station. Only while focused: in the
     * world Escape opens the pause menu, which would pause the integrated server under the agent.
     */
    private static void escape(Minecraft mc) {
        releaseKeys(mc);
        if (!DiegesisClient.isFocused()) return;
        key(mc, GLFW.GLFW_KEY_ESCAPE, true);
        key(mc, GLFW.GLFW_KEY_ESCAPE, false);
    }

    private static String focusExitState(Minecraft mc) {
        return "overlay=" + overlayShown() + " focused=" + DiegesisClient.isFocused()
                + " screen=" + (mc.screen == null ? "-" : mc.screen.getClass().getSimpleName())
                + " drawerPinned=" + drawerPinned();
    }

    /** Test-only diagnosis of the Diegesis drawer state which can consume Escape before the focus screen closes. */
    private static Object drawerPinned() {
        try {
            Class<?> inputType = Class.forName("com.pockyl.diegesis.impl.client.focus.FocusInput");
            var get = inputType.getDeclaredMethod("get");
            get.setAccessible(true);
            Object input = get.invoke(null);
            var drawer = inputType.getDeclaredMethod("drawer");
            drawer.setAccessible(true);
            Object value = drawer.invoke(input);
            var pinned = value.getClass().getDeclaredMethod("isPinned");
            pinned.setAccessible(true);
            return pinned.invoke(value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "probe_error:" + e.getClass().getSimpleName();
        }
    }

    /** Whether the focused station shows a screen overlay (the loupe, the folio). Reads the library's own state. */
    private static boolean overlayShown() {
        try {
            Field f = Class.forName("com.pockyl.diegesis.impl.client.focus.FocusScreen").getDeclaredField("overlay");
            f.setAccessible(true);
            return f.get(null) != null;
        } catch (ReflectiveOperationException e) {
            checks.put("overlay_probe", e.toString());
            return false;
        }
    }

    /**
     * Diegesis 1.0.0-beta.10 at the bench, before any work: the stack under the cursor for recipe viewers (a tray stone);
     * JEI's show-recipe key over it (the turn key too, which has nothing to turn with empty tweezers) opens JEI's page as
     * a child screen that keeps the station and its lease, with no key reaching the covered station, and Escape returns
     * to the station; JEI lays nothing over the station; a stack taken from the drawer through Diegesis (at the drawer's
     * carry size) shows where it would go over a placement node (takes_items); the drawer lists only what the bench can use.
     */
    private static boolean diegesisBeta10(Minecraft mc, int t) {
        JewelersBenchBlockEntity b = clientBench(mc);
        StationView view = WorkstationClient.focusedView().orElse(null);
        if (t < 40) event(new SceneEvent.Hover("tray_0")); // Diegesis reports its own hover changes too: keep ours
        if (t == 6) {
            checks.put("lookup_tray_stone", !b.tray(0).isEmpty() && ItemStack.matches(WorkstationClient.stackUnderCursor(), b.tray(0)));
            // JEI has no GUI on the station's screen: its list and bookmarks are not laid over the work.
            checks.put("no_jei_list_over_station", SmokeJeiPlugin.runtime != null
                    && !SmokeJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed());
        }
        if (t == 8) {
            key(mc, GLFW.GLFW_KEY_R, true);
            key(mc, GLFW.GLFW_KEY_R, false);
        }
        boolean page = mc.screen instanceof mezz.jei.api.runtime.IRecipesGui;
        if (t == 30) {
            checks.put("jei_key_opens_page_over_station", page && DiegesisClient.isFocused() && WorkstationClient.operates(bench()));
            shot(mc, "smoketest_bench_jei_child");
            if (page) {
                // The apply key goes to JEI's page: read at once, before the next tick lets every held key go anyway.
                key(mc, GLFW.GLFW_KEY_SPACE, true);
                checks.put("no_station_keys_under_child_screen", !Boolean.TRUE.equals(behaviourField(bench(), "keyApply")));
                key(mc, GLFW.GLFW_KEY_SPACE, false);
            }
        }
        if (t == 34 && page) {
            key(mc, GLFW.GLFW_KEY_ESCAPE, true);
            key(mc, GLFW.GLFW_KEY_ESCAPE, false);
        }
        if (t == 50) {
            checks.put("back_at_station_after_jei", DiegesisClient.isStationScreen(mc.screen) && DiegesisClient.isFocused()
                    && WorkstationClient.operates(bench()));
            event(new SceneEvent.Hover(null));
            ItemStack spare = b.tray(0).copy();
            server(mc, (server, player) -> {
                player.getInventory().add(spare);
                player.getInventory().add(new ItemStack(Items.DIRT));
            });
        }
        if (t == 58 && view != null) {
            for (int slot = 0; slot < mc.player.getInventory().items.size(); slot++) {
                if (mc.player.getInventory().items.get(slot).is(ModItems.CUT_GEM.get())) {
                    view.takeFromInventory(slot);
                    break;
                }
            }
        }
        if (t > 60 && t < 92) event(new SceneEvent.Hover("tray_1"));
        if (t == 72) {
            checks.put("carried_from_drawer", mc.player.containerMenu.getCarried().is(ModItems.CUT_GEM.get()));
            checks.put("placement_hint_tray_1", "Target[group=tray, index=1]".equals(String.valueOf(behaviourCall(bench(), "placement", b))));
            shot(mc, "smoketest_bench_place_hint");
        }
        if (t == 92 && view != null) {
            view.putAwayCarried();
            event(new SceneEvent.Hover(null));
        }
        if (t == 100) key(mc, GLFW.GLFW_KEY_TAB, true);
        if (t == 101) key(mc, GLFW.GLFW_KEY_TAB, false);
        if (t == 112) shot(mc, "smoketest_bench_drawer");
        if (t == 114) {
            key(mc, GLFW.GLFW_KEY_TAB, true);
            key(mc, GLFW.GLFW_KEY_TAB, false);
        }
        if (t == 120) {
            checks.put("put_away_after_hint", mc.player.containerMenu.getCarried().isEmpty());
            server(mc, (server, player) -> {
                player.getInventory().clearOrCountMatchingItems(s -> s.is(ModItems.CUT_GEM.get()), 1, player.inventoryMenu.getCraftSlots());
                player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.DIRT), 1, player.inventoryMenu.getCraftSlots());
            });
        }
        return t > 126;
    }

    /** How far the bench's tweezers have turned the stone they hold (radians), or -1. */
    private static float heldTurn() {
        return behaviourField(bench(), "heldTurn") instanceof Float turn ? turn : -1F;
    }

    /** A private method of the station's behaviour, called (test-only probe); null when it is missing or fails. */
    @Nullable
    private static Object behaviourCall(BlockPos pos, String name, Object... args) {
        Object behaviour = WorkstationClient.behaviourAt(pos).orElse(null);
        for (Class<?> c = behaviour == null ? null : behaviour.getClass(); c != null; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(behaviour, args);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** A private field of the station's behaviour (test-only probe of what the player sees selected). */
    @Nullable
    private static Object behaviourField(BlockPos pos, String name) {
        Object behaviour = WorkstationClient.behaviourAt(pos).orElse(null);
        for (Class<?> c = behaviour == null ? null : behaviour.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(behaviour);
            } catch (NoSuchFieldException ignored) {
                // up the hierarchy
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    /** Uses the station block as a click would: Diegesis decides whether this opens the scene or uses the block. */
    private static void use(Minecraft mc, BlockPos pos) {
        if (mc.player == null || mc.gameMode == null) return;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.45, 0), Direction.UP, pos, false);
        String held = mc.player.getMainHandItem().isEmpty() ? "empty hand" : mc.player.getMainHandItem().getHoverName().getString();
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        log("use " + pos.toShortString() + " with " + held + " -> " + result + ", focused " + DiegesisClient.isFocused());
    }

    /** One line of state for the log: hand, focus, leases, what each station holds. */
    private static String state(Minecraft mc) {
        StringBuilder b = new StringBuilder();
        b.append("hand=").append(mc.player.getMainHandItem().isEmpty() ? "-" : mc.player.getMainHandItem().getHoverName().getString());
        b.append(" carried=").append(mc.player.containerMenu.getCarried().isEmpty() ? "-" : mc.player.containerMenu.getCarried().getHoverName().getString());
        b.append(" focused=").append(DiegesisClient.isFocused()).append(" screen=").append(mc.screen == null ? "-" : mc.screen.getClass().getSimpleName());
        if (base != null && mc.level.getBlockEntity(wheel()) instanceof LapidaryWheelBlockEntity w) {
            b.append(" | wheel lease=").append(WorkstationClient.operates(wheel())).append(" stone=").append(w.stone().isEmpty() ? "-" : w.stone().getHoverName().getString())
                    .append(" work=").append(w.work().map(c -> c.cut() + "@" + Math.round(c.sim().overallProgress() * 100) + "%").orElse("-"));
        }
        if (base != null && mc.level.getBlockEntity(bench()) instanceof JewelersBenchBlockEntity j) {
            b.append(" | bench lease=").append(WorkstationClient.operates(bench())).append(" clamp=").append(j.clamp().isEmpty() ? "-" : j.clamp().getHoverName().getString())
                    .append(" tray0=").append(j.tray(0).isEmpty() ? "-" : j.tray(0).getHoverName().getString())
                    .append(" socket0=").append(j.socket(0).isEmpty() ? "-" : j.socket(0).getHoverName().getString())
                    .append(" dock=").append(j.dock().isEmpty() ? "-" : "pliers").append(" supply0=").append(j.supply(0).isEmpty() ? "-" : j.supply(0).getHoverName().getString())
                    .append(" work=").append(j.work() == null ? "-" : j.work().kind() + "/" + j.work().stage());
        }
        return b.toString();
    }

    /** Puts {@code stack} in the main hand; whatever was there moves to another slot (never destroyed). */
    private static void give(Minecraft mc, ItemStack stack) {
        server(mc, (server, player) -> hand(player, stack.copy()));
    }

    private static void hand(ServerPlayer player, ItemStack stack) {
        ItemStack old = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
    }

    /** One cut stone from anywhere in the inventory into the main hand. */
    private static void handCutGem(ServerPlayer player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof CutGemItem) {
                hand(player, s.split(1));
                return;
            }
        }
        log("no cut stone in the inventory");
    }

    private static int count(Minecraft mc, Item item) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static List<ResourceLocation> cutsForStone(Minecraft mc) {
        List<ResourceLocation> out = new ArrayList<>();
        GemData data = RoughGemItem.data(clientWheel(mc).stone()).orElse(null);
        if (data == null) return out;
        JewelryDefinitions defs = JewelryDefinitions.client();
        for (CutDefinition cut : defs.cutsFor(defs.gemOrGeneric(data.gem()))) out.add(cut.id());
        return out;
    }

    private static void jei(mezz.jei.api.recipe.RecipeType<?> type) {
        if (SmokeJeiPlugin.runtime != null) SmokeJeiPlugin.runtime.getRecipesGui().showTypes(List.of(type));
    }

    private static LapidaryWheelBlockEntity clientWheel(Minecraft mc) {
        return (LapidaryWheelBlockEntity) mc.level.getBlockEntity(wheel());
    }

    private static JewelersBenchBlockEntity clientBench(Minecraft mc) {
        return (JewelersBenchBlockEntity) mc.level.getBlockEntity(bench());
    }

    // ================================================================================================ staging

    /**
     * A {@code width}x{@code height} window. A tiling Wayland compositor ignores the requested size, so on Hyprland this
     * client's own window (matched by process id) is floated and sized; nothing else on the desktop is touched.
     */
    static void sizeWindow(Minecraft mc, int width, int height) {
        mc.getWindow().setWindowed(width, height);
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") == null) return;
        String target = "window = \"pid:" + ProcessHandle.current().pid() + "\"";
        Thread thread = new Thread(() -> {
            for (String dispatch : new String[]{"hl.dsp.window.float({ action = \"enable\", " + target + " })",
                    "hl.dsp.window.resize({ x = " + width + ", y = " + height + ", " + target + " })",
                    "hl.dsp.window.move({ x = 40, y = 40, " + target + " })"}) {
                try {
                    new ProcessBuilder("hyprctl", "dispatch", dispatch).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .start().waitFor();
                } catch (IOException e) {
                    log("hyprctl unavailable: " + e.getMessage());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "smoketest-window");
        thread.setDaemon(true);
        thread.start();
    }

    private static void createWorld(Minecraft mc) {
        String name = "SmokeTest" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20260925L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void clientChecks(Minecraft mc) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        checks.put("client_gems_ok", defs.gems().size() >= 9);
        checks.put("client_cuts_ok", defs.cuts().size() == 4);
        checks.put("client_designs_ok", mc.level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get()).size() >= 10);
        checks.put("server_defs_match_client", JewelryDefinitions.server().gems().size() == defs.gems().size());
        checks.put("language", mc.options.languageCode);
        WorkstationVisualChecks.checkTints(checks);
        for (String page : List.of("welcome", "stones", "wheel", "cutting", "cuts", "blanks", "bench", "setting",
                "finishes", "wearing", "effects", "care", "designs", "jeweler")) {
            // HandbookScreen pages are vanilla book pages: 114 px lines, 14 to a page, 11 under a chapter's heading.
            int lines = mc.font.split(Component.translatable("handbook.immersive_jewelry." + page + ".text"), 114).size();
            checks.put("handbook_" + page + "_lines", lines);
            checks.put("handbook_" + page + "_sheets", lines <= 11 ? 1 : 1 + (lines - 11 + 13) / 14);
        }
        if (mc.options.languageCode.equals("ru_ru")) {
            for (String finish : List.of("polished", "burnished", "antique")) {
                int lines = mc.font.split(Component.translatable("finish.immersive_jewelry." + finish + ".description"), 120).size();
                checks.put("russian_finish_" + finish + "_fits", lines <= 5);
            }
        }
    }

    private static void stageWorld(Minecraft mc) {
        server(mc, (server, player) -> {
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            if (ShowcaseCapture.enabled()) level.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
            base = player.blockPosition();
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), (x + z) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                        : Blocks.DEEPSLATE_TILES.defaultBlockState());
                for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            BlockState wheelState = ModBlocks.LAPIDARY_WHEEL.get().defaultBlockState().setValue(StationBlock.FACING, Direction.SOUTH);
            BlockState benchState = ModBlocks.JEWELERS_BENCH.get().defaultBlockState().setValue(StationBlock.FACING, Direction.SOUTH);
            level.setBlockAndUpdate(wheel(), wheelState);
            level.setBlockAndUpdate(bench(), benchState);
            if (ShowcaseCapture.enabled()) ShowcaseWorkshop.build(level, base);
            if (ShowcaseCapture.overviewOnly()) {
                if (level.getBlockEntity(wheel()) instanceof LapidaryWheelBlockEntity w)
                    w.setStone(RoughGemItem.create(GemData.rough(ImmersiveJewelry.id("sapphire"), 1F, .85F, .1F)));
                if (level.getBlockEntity(bench()) instanceof JewelersBenchBlockEntity b) {
                    b.setClamp(piece(JewelryDefinitions.server(), "ring", "ruby", "brilliant", GOLD, "polished", .95F));
                    b.setDock(new ItemStack(ModItems.FINE_PLIERS.get()));
                }
            }
            player.getInventory().clearContent();
            player.getInventory().setItem(8, new ItemStack(ModItems.JEWELERS_HANDBOOK.get()));
        });
    }

    static ItemStack piece(JewelryDefinitions defs, String form, String gem, String cut, ResourceLocation metal, String finish, float q) {
        GemData stone = JewelryFactory.sampleCut(defs.gem(ImmersiveJewelry.id(gem)), defs.cut(ImmersiveJewelry.id(cut)), q);
        MetalRef ref = JewelryFactory.metal(defs, metal);
        return JewelryFactory.jewelry(ImmersiveJewelry.id(form), ref, List.of(stone), null, ImmersiveJewelry.id(finish), q, q, "Otectus");
    }

    interface ServerTask {
        void run(IntegratedServer server, ServerPlayer player);
    }

    static void server(Minecraft mc, ServerTask task) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            try {
                task.run(server, server.getPlayerList().getPlayers().get(0));
            } catch (Exception e) {
                ImmersiveJewelry.LOGGER.error("SMOKETEST server task failed", e);
                checks.put("server_exception", e.toString());
            }
        });
    }

    /** Stands {@code distance} blocks south of a station, survival, looking down at it at {@code pitch}. */
    private static void stand(Minecraft mc, BlockPos target, double distance, float pitch) {
        server(mc, (server, player) -> {
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(server.overworld(), target.getX() + 0.5, target.getY(), target.getZ() + 0.5 + distance, 180F, pitch);
        });
    }

    static void shot(Minecraft mc, String name) {
        if (ShowcaseCapture.enabled()) {
            ShowcaseCapture.shot(mc, name);
            log("showcase screenshot " + name);
            return;
        }
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> log("screenshot " + name));
    }

    private static boolean allPassed() {
        WorkstationContinuityChecks.publish(checks);
        for (Object value : checks.values()) {
            if (value instanceof Boolean b && !b) return false;
        }
        return !checks.containsKey("agent_exception") && !checks.containsKey("server_exception");
    }

    static void finish(Minecraft mc, boolean pass, String reason) {
        WorkstationContinuityChecks.publish(checks);
        WorkstationArmChecks.restore(mc);
        finished = true;
        writeResult(pass, reason);
        log("finished: pass=" + pass + " (" + reason + ")");
        mc.stop();
    }

    private static void writeResult(boolean pass, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass);
        result.put("reason", reason);
        result.put("checks", checks);
        result.put("phase_ms", timings);
        if (scriptStart > 0) {
            // Client ticks per real second over the script: 20 unless the window's frames were throttled.
            result.put("client_ticks_per_second", (worldTicks - 40) * 1000.0 / Math.max(1L, Util.getMillis() - scriptStart));
        }
        if (!frameNanos.isEmpty()) result.put("frame_interval_while_focused_ms", percentiles(frameNanos));
        if (!renderNanos.isEmpty()) result.put("render_thread_while_focused_ms", percentiles(renderNanos));
        result.put("frame_limit", "vsync off, 120 fps cap (the interval cannot go below 8.3 ms)");
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "smoketest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ImmersiveJewelry.LOGGER.error("SMOKETEST could not write result", e);
        }
    }

    private static Map<String, Object> percentiles(List<Long> nanos) {
        List<Long> sorted = new ArrayList<>(nanos);
        sorted.sort(Long::compare);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("samples", sorted.size());
        out.put("median", sorted.get(sorted.size() / 2) / 1_000_000.0);
        out.put("p95", sorted.get((int) (sorted.size() * 0.95)) / 1_000_000.0);
        out.put("p99", sorted.get((int) (sorted.size() * 0.99)) / 1_000_000.0);
        return out;
    }

    static void log(String message) {
        ImmersiveJewelry.LOGGER.info("SMOKETEST {}", message);
    }

    /** Tooltips of representative pieces (the details of a finished ring and a cut stone). */
    private static final class TooltipSheet extends Screen {
        private final int page;

        private TooltipSheet(int page) {
            super(Component.literal("Tooltips"));
            this.page = page;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            JewelryDefinitions defs = JewelryDefinitions.client();
            ItemStack[] stacks = page == 0 ? new ItemStack[]{piece(defs, "ring", "ruby", "brilliant", GOLD, "polished", 0.93F),
                    piece(defs, "amulet", "sapphire", "round", SILVER, "burnished", 0.8F)}
                    : new ItemStack[]{piece(defs, "circlet", "diamond", "brilliant", GOLD, "polished", 0.97F)};
            int x = 4;
            int y = 16;
            int rowHeight = 0;
            for (ItemStack stack : stacks) {
                List<Component> lines = Screen.getTooltipFromItem(minecraft, stack);
                int w = lines.stream().mapToInt(font::width).max().orElse(0) + 8;
                int h = lines.size() * 10 + 6;
                if (x > 4 && x + w > width) {
                    x = 4;
                    y += rowHeight + 10;
                    rowHeight = 0;
                }
                g.renderComponentTooltip(font, lines, x - 12, y + 12);
                x += w + 10;
                rowHeight = Math.max(rowHeight, h);
            }
        }
    }

    /** Every form in several metals, gems, cuts and finishes, plus the stones, blanks, tools, carriers and stations, at 2x. */
    private static final class ItemSheet extends Screen {
        private ItemSheet() {
            super(Component.literal("Items"));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            JewelryDefinitions defs = JewelryDefinitions.client();
            List<ItemStack> stacks = new ArrayList<>();
            String[][] combos = {{"minecraft:gold", "ruby", "brilliant", "polished"}, {"forge:silver", "sapphire", "round", "burnished"},
                    {"minecraft:copper", "emerald", "emerald", "antique"}, {"minecraft:netherite", "diamond", "brilliant", "polished"},
                    {"minecraft:iron", "amethyst", "cabochon", "burnished"}, {"minecraft:gold", "onyx", "cabochon", "antique"}};
            for (String form : new String[]{"ring", "amulet", "bracelet", "circlet"}) {
                for (String[] c : combos) stacks.add(piece(defs, form, c[1], c[2], new ResourceLocation(c[0]), c[3], 0.85F));
            }
            for (GemDefinition gem : defs.gems()) {
                if (gem.auto()) continue;
                stacks.add(RoughGemItem.create(GemData.rough(gem.id(), 1F, 0.6F, 0.2F)));
                for (CutDefinition cut : defs.cutsFor(gem)) stacks.add(CutGemItem.create(JewelryFactory.sampleCut(gem, cut, 0.85F)));
            }
            for (Item blank : new Item[]{ModItems.RING_BLANK.get(), ModItems.AMULET_FRAME.get(), ModItems.BRACELET_BLANK.get(), ModItems.CIRCLET_FRAME.get()}) {
                ItemStack b = JewelryFactory.blank(blank, JewelryFactory.metal(defs, GOLD), 0.8F);
                stacks.add(b);
                stacks.add(com.otectus.immersivejewelry.item.UnfinishedJewelryItem.create(b, List.of(), new net.minecraft.nbt.CompoundTag()));
            }
            for (Item item : new Item[]{ModItems.JEWELERS_PLIERS.get(), ModItems.FINE_PLIERS.get(), ModItems.EMERY_LAP.get(),
                    ModItems.DIAMOND_LAP.get(), ModItems.POLISHING_COMPOUND.get(), ModItems.GEM_DUST.get(), ModItems.DESIGN_SKETCH.get(),
                    ModItems.JEWELERS_HANDBOOK.get(), ModItems.LAPIDARY_WHEEL.get(), ModItems.JEWELERS_BENCH.get()}) {
                stacks.add(new ItemStack(item));
            }
            int cols = Math.max(1, (width - 10) / 36);
            g.pose().pushPose();
            g.pose().scale(2F, 2F, 1F);
            for (int i = 0; i < stacks.size(); i++) g.renderItem(stacks.get(i), 3 + (i % cols) * 18, 3 + (i / cols) * 18);
            g.pose().popPose();
        }
    }

    private ClientSmokeTest() {}
}
