package com.otectus.immersivejewelry.client.workstation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.workstation.station.JewelryStations;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * The workstations' Diegesis scenes. They are authored as Diegesis scene files under
 * {@code assets/immersive_jewelry/diegesis/stations/<station>.json} (the library's own scene format and schema, so resource
 * packs restyle them as usual), and registered as code scenes after this player's comfort options are applied: no zoom
 * keeps their own field of view, instant transitions cut instead of easing, reduced motion stops the HUD sliding. The
 * library itself is never asked to clear blocks or entities out of the view or to go over to neighbouring stations: the
 * authored files turn those off and this loader enforces it.
 */
public final class SceneLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
    public static final SceneLoader INSTANCE = new SceneLoader();
    private static final String DIR = "diegesis/stations/";
    private Map<ResourceLocation, JsonObject> authored = Map.of();

    @Override
    protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> out = new HashMap<>();
        for (ResourceLocation station : new ResourceLocation[]{JewelryStations.WHEEL_ID, JewelryStations.BENCH_ID}) {
            ResourceLocation file = new ResourceLocation(station.getNamespace(), DIR + station.getPath() + ".json");
            manager.getResource(file).ifPresent(resource -> {
                try (Reader reader = resource.openAsReader()) {
                    out.put(station, JsonParser.parseReader(reader).getAsJsonObject());
                } catch (Exception e) {
                    ImmersiveJewelry.LOGGER.error("Could not read workstation scene {}", file, e);
                }
            });
        }
        return out;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> loaded, ResourceManager manager, ProfilerFiller profiler) {
        authored = loaded;
        refresh();
    }

    /** Registers both scenes again with the current options (after a resource reload or an options change). */
    public void refresh() {
        authored.forEach((station, json) -> {
            SceneDefinition scene = build(station, json.deepCopy());
            if (scene != null) DiegesisClient.registerScene(station, scene);
        });
    }

    @Nullable
    private static SceneDefinition build(ResourceLocation station, JsonObject json) {
        boolean noZoom = ClientConfig.get(ClientConfig.NO_ZOOM);
        boolean instant = ClientConfig.get(ClientConfig.INSTANT_TRANSITIONS);
        boolean reduced = ClientConfig.get(ClientConfig.REDUCED_MOTION);
        JsonObject camera = json.has("camera") ? json.getAsJsonObject("camera") : new JsonObject();
        if (noZoom) camera.addProperty("fov", 0);
        if (instant) camera.addProperty("transition", 0);
        JsonObject neighbors = new JsonObject();
        neighbors.addProperty("enabled", false);
        neighbors.addProperty("keys", false);
        camera.add("neighbors", neighbors);
        if (camera.has("glances")) {
            for (JsonElement e : camera.getAsJsonArray("glances")) {
                JsonObject glance = e.getAsJsonObject();
                if (noZoom) {
                    glance.addProperty("fov", 0);
                    // Without magnification the loupe look stays where the station's own shot is.
                    if (glance.has("id") && glance.get("id").getAsString().equals("loupe") && camera.has("position") && camera.has("target")) {
                        glance.add("position", camera.get("position").deepCopy());
                        glance.add("target", camera.get("target").deepCopy());
                    }
                }
                if (instant) glance.addProperty("time", 0.01);
            }
        }
        json.add("camera", camera);
        JsonObject clear = json.has("clear_view") ? json.getAsJsonObject("clear_view") : new JsonObject();
        clear.addProperty("blocks", false);
        clear.addProperty("entities", false);
        json.add("clear_view", clear);
        JsonObject hud = json.has("hud") ? json.getAsJsonObject("hud") : new JsonObject();
        if (reduced || instant) hud.addProperty("slide", false);
        json.add("hud", hud);
        var result = SceneDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        result.error().ifPresent(error -> ImmersiveJewelry.LOGGER.error("Workstation scene {} is broken: {}", station, error.message()));
        return result.result().orElse(null);
    }

    /** The authored camera of a station's scene (position and target in scene pixels), for mapping pointer motion. */
    @Nullable
    public float[] camera(ResourceLocation station) {
        JsonObject json = authored.get(station);
        if (json == null || !json.has("camera")) return null;
        JsonObject camera = json.getAsJsonObject("camera");
        if (!camera.has("position") || !camera.has("target")) return null;
        JsonArray p = camera.getAsJsonArray("position");
        JsonArray t = camera.getAsJsonArray("target");
        return new float[]{p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(),
                t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat()};
    }

    private SceneLoader() {}
}
