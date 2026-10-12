package com.otectus.immersivejewelry.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivejewelry.client.screen.CuttingScreen;
import com.otectus.immersivejewelry.client.screen.SettingScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Measures the existing reference bots during real networked crafting, including their real screens. */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class CraftingProfile {
    private static final String LABEL = System.getenv("IJ_SMOKE_PROFILE");
    private static final Map<String, List<Double>> times = new LinkedHashMap<>();
    private static long start;
    private static String active;

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (LABEL == null) return;
        if (event.phase == TickEvent.Phase.START) {
            var screen = Minecraft.getInstance().screen;
            active = screen instanceof CuttingScreen cut && !cut.finished() ? "cutting"
                    : screen instanceof SettingScreen setting && !setting.finished() ? "setting" : null;
            start = System.nanoTime();
        } else if (active != null) times.computeIfAbsent(active, k -> new ArrayList<>()).add((System.nanoTime() - start) / 1e6);
    }

    static void write() throws java.io.IOException {
        if (LABEL == null) return;
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : times.entrySet()) {
            var sorted = entry.getValue().stream().sorted().toList();
            result.put(entry.getKey(), Map.of("samples", sorted.size(), "median_render_cpu_ms", sorted.get(sorted.size() / 2),
                    "p95_render_cpu_ms", sorted.get((int) (sorted.size() * .95)),
                    "mean_render_cpu_ms", sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        }
        Files.writeString(Path.of(System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY), "crafting-" + LABEL + ".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }

    private CraftingProfile() {}
}
