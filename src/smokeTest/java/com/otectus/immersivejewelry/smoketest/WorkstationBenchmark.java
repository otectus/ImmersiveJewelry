package com.otectus.immersivejewelry.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.block.StationBlock;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.registry.ModBlocks;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in, test-only fixed-seed scene benchmark: IJ_WORKSTATION_BENCHMARK=baseline runClient -PsmokeTest. */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class WorkstationBenchmark {
    private static final String LABEL = System.getenv("IJ_WORKSTATION_BENCHMARK");
    private static final String[] SCENES = {"empty", "bench_empty", "bench_occupied", "wheel_empty", "wheel_occupied",
            "wheel_working", "workshop_idle", "workshop_working", "workshop_approach", "workshop_circle"};
    private static final BlockPos BASE = new BlockPos(0, -60, 0);
    private static final int WARMUP = 100, SAMPLE = 120;
    private static final List<Double> cpu = new ArrayList<>(), frames = new ArrayList<>(), gpu = new ArrayList<>();
    private static final Map<String, Object> result = new LinkedHashMap<>(), scenes = new LinkedHashMap<>();
    private static int scene = -1, ticks, frame, queryStart, queryEnd;
    private static long renderStart, previousFrame;
    private static boolean sampling, pendingQuery, recordingQuery, finished;
    private static Recording recording;

    static boolean enabled() { return LABEL != null; }

    static void tick(Minecraft mc) throws Exception {
        if (finished) return;
        if (scene == -1) {
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            mc.options.renderDistance().set(8);
            mc.options.simulationDistance().set(5);
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            result.put("label", LABEL);
            result.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
            result.put("java", System.getProperty("java.version"));
            result.put("opengl", GL11.glGetString(GL11.GL_VERSION));
            result.put("gpu_timer_supported", GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_timer_query);
            result.put("resolution", mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
            result.put("settings", "vsync off; unlimited FPS (260 slider maximum); render 8; simulation 5; seed 20260925; 5s warmup + 6s sample per scene");
            result.put("graphics", mc.options.graphicsMode().get().toString());
            result.put("mipmaps", mc.options.mipmapLevels().get());
            if (GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_timer_query) {
                queryStart = GL15.glGenQueries(); queryEnd = GL15.glGenQueries();
            }
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.start();
            next(mc);
        }
        ticks++;
        // Keep desktop mouse motion and key state out of the comparison.
        mc.mouseHandler.releaseMouse();
        net.minecraft.client.KeyMapping.releaseAll();
        if (scene < 8) camera(mc, 0, scene >= 6 ? 10 : 3, 0, scene >= 6 ? 28 : 30);
        if (ticks == WARMUP) { sampling = true; previousFrame = 0; }
        if (scene >= 8 && ticks >= WARMUP) {
            double t = (ticks - WARMUP) / (double) SAMPLE;
            double a = scene == 9 ? t * Math.PI * 2 : 0;
            double distance = scene == 8 ? 14 - t * 10 : 10;
            camera(mc, Math.sin(a) * distance, Math.cos(a) * distance, (float) Math.toDegrees(-a), 28);
        }
        if (ticks == WARMUP + SAMPLE - 4) {
            Screenshot.grab(mc.gameDirectory, "workstation_" + LABEL + "_" + SCENES[scene] + ".png", mc.getMainRenderTarget(), c -> {});
        }
        if (ticks >= WARMUP + SAMPLE) {
            sampling = false;
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("render_cpu_ms", stats(cpu)); stats.put("frame_ms", stats(frames)); stats.put("gpu_ms", stats(gpu));
            scenes.put(SCENES[scene], stats);
            ImmersiveJewelry.LOGGER.info("WORKSTATION BENCHMARK {} {}", SCENES[scene], stats);
            cpu.clear(); frames.clear(); gpu.clear();
            next(mc);
        }
    }

    private static Map<String, Object> stats(List<Double> values) {
        if (values.isEmpty()) return Map.of("samples", 0);
        var sorted = values.stream().sorted().toList();
        return Map.of("samples", values.size(), "mean", values.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                "median", sorted.get(sorted.size() / 2), "p95", sorted.get((int) (sorted.size() * .95)));
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!enabled() || finished || !sampling) return;
        if (event.phase == TickEvent.Phase.START) {
            renderStart = System.nanoTime();
            if (previousFrame != 0) frames.add((renderStart - previousFrame) / 1e6);
            previousFrame = renderStart;
            if (pendingQuery && GL15.glGetQueryObjecti(queryEnd, GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                gpu.add((GL33.glGetQueryObjecti64(queryEnd, GL15.GL_QUERY_RESULT) - GL33.glGetQueryObjecti64(queryStart, GL15.GL_QUERY_RESULT)) / 1e6);
                pendingQuery = false;
            }
            recordingQuery = queryStart != 0 && !pendingQuery && ++frame % 16 == 0;
            if (recordingQuery) GL33.glQueryCounter(queryStart, GL33.GL_TIMESTAMP);
        } else {
            cpu.add((System.nanoTime() - renderStart) / 1e6);
            if (recordingQuery) {
                GL33.glQueryCounter(queryEnd, GL33.GL_TIMESTAMP);
                pendingQuery = true;
            }
        }
    }

    private static void next(Minecraft mc) throws Exception {
        if (++scene == SCENES.length) {
            finished = true;
            Path out = Path.of(System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY), "workstation-" + LABEL);
            Files.createDirectories(out);
            result.put("scenes", scenes);
            Files.writeString(out.resolve("measurements.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            recording.stop(); recording.dump(out.resolve("profile.jfr")); recording.close();
            mc.stop();
            return;
        }
        ticks = 0;
        int selected = scene;
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().get(0);
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            player.setGameMode(GameType.SPECTATOR);
            for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
                for (int y = 0; y < 3; y++) level.setBlockAndUpdate(BASE.offset(x, y, z), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(BASE.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
            // Breaking the previous scene's occupied stations drops their contents. They must not accumulate
            // as extra item renderers in subsequent measurements.
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(BASE).inflate(30)).forEach(net.minecraft.world.entity.Entity::discard);
            int count = selected == 0 ? 0 : selected >= 6 ? 16 : 1;
            var defs = JewelryDefinitions.server();
            for (int i = 0; i < count; i++) {
                BlockPos pos = count == 1 ? BASE : BASE.offset((i % 4) * 2 - 3, 0, (i / 4) * 2 - 3);
                boolean wheel = selected >= 3 && selected <= 5 || selected >= 6 && i % 2 == 0;
                var block = wheel ? ModBlocks.LAPIDARY_WHEEL.get() : ModBlocks.JEWELERS_BENCH.get();
                level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(StationBlock.FACING, Direction.from2DDataValue(count == 1 ? 2 : (i / 2) % 4)));
                boolean occupied = selected == 2 || selected >= 4;
                boolean working = selected == 5 || selected >= 7;
                if (level.getBlockEntity(pos) instanceof LapidaryWheelBlockEntity w) {
                    if (occupied) w.setStone(RoughGemItem.create(GemData.rough(ImmersiveJewelry.id("ruby"), 1, .8F, .1F)));
                    if (working) w.setOperator(player.getUUID(), player.getGameProfile().getName());
                }
                if (level.getBlockEntity(pos) instanceof JewelersBenchBlockEntity b) {
                    if (occupied) b.setClamp(ClientSmokeTest.piece(defs, "ring", "ruby", "brilliant", new ResourceLocation("minecraft", "gold"), "polished", .9F));
                    if (working) b.setOperator(player.getUUID(), player.getGameProfile().getName());
                }
            }
            player.teleportTo(level, .5, BASE.getY() + (count > 1 ? 4 : 1), count > 1 ? 10.5 : 3.5, 180, count > 1 ? 28 : 30);
        });
    }

    private static void camera(Minecraft mc, double x, double z, float yaw, float pitch) {
        mc.player.setPos(.5 + x, BASE.getY() + (scene >= 6 ? 4 : 1), .5 + z);
        mc.player.xo = mc.player.getX(); mc.player.yo = mc.player.getY(); mc.player.zo = mc.player.getZ();
        mc.player.setYRot(180 + yaw); mc.player.yRotO = 180 + yaw;
        mc.player.setXRot(pitch); mc.player.xRotO = pitch;
    }

    private WorkstationBenchmark() {}
}
