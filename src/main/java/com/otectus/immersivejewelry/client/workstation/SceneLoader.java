package com.otectus.immersivejewelry.client.workstation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.workstation.station.JewelryStations;
import com.otectus.immersivejewelry.workstation.station.SceneComfort;
import com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat;
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
 * packs restyle them as usual), and registered as code scenes after this player's comfort options are applied
 * ({@link SceneComfort}: no zoom, instant transitions, reduced motion, calm loose items, the work lamp). They stay here rather
 * than under the library's auto-loaded {@code diegesis/scenes/}, which would bypass that transform. The library itself is
 * never asked to clear blocks or entities out of the view or to go over to neighbouring stations: the authored files turn
 * those off and the transform enforces it.
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

    @Nullable
    private SceneComfort.Options registered;

    /**
     * Registers both scenes again with the current options (after a resource reload or an options change). Returns whether
     * the options the scenes are built from changed since they were last registered (the working side is not one of them: it
     * is applied live).
     */
    public boolean refresh() {
        SceneComfort.Options now = options();
        boolean changed = registered != null && !registered.equals(now);
        registered = now;
        if (!DiegesisAccess.ready()) return changed;
        built.clear();
        authored.forEach((station, json) -> {
            Built scene = build(station, json);
            if (scene != null && DiegesisAccess.registerScene(station, scene.definition())) built.put(station, scene);
        });
        return changed;
    }

    /** A scene as registered: at which {@link SceneComfort.Fallback} level, and whether the drawer and the jar slots are in it. */
    record Built(SceneDefinition definition, SceneComfort.Fallback level, boolean placing, boolean jarSlots) {}

    private final Map<ResourceLocation, Built> built = new HashMap<>();
    /** Simplified scenes already reported (an options change rebuilds the scenes; the warning is said once a session). */
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    /**
     * The scene with this player's options, at the first fallback level the installed Diegesis reads (the authored one with the
     * tested release). A level below the authored one is logged, with what the library refused; with no level
     * read the station gets no scene (it then says so when opened), and nothing else is affected. Each simplification is
     * logged once a session.
     */
    @Nullable
    private static Built build(ResourceLocation station, JsonObject authoredJson) {
        String firstError = null;
        for (SceneComfort.Fallback level : SceneComfort.Fallback.values()) {
            JsonObject json = SceneComfort.fallback(SceneComfort.apply(authoredJson.deepCopy(), options()), level);
            var result = SceneDefinition.CODEC.parse(JsonOps.INSTANCE, json);
            if (result.result().isPresent() && result.error().isEmpty()) {
                if (level != SceneComfort.Fallback.AUTHORED && WARNED.add(station + "@" + level)) {
                    ImmersiveJewelry.LOGGER.warn("Immersive Jewelry: Diegesis {} cannot read workstation scene {} as written ({}); using it simplified to {}",
                            DiegesisCompat.installedVersion(), station, firstError, level);
                }
                boolean jars = false;
                if (json.has("nodes")) {
                    for (var n : json.getAsJsonArray("nodes")) jars |= n.getAsJsonObject().has("slot");
                }
                return new Built(result.result().get(), level, json.has("placing"), jars);
            }
            if (firstError == null) firstError = result.error().map(e -> e.message()).orElse("?");
        }
        ImmersiveJewelry.LOGGER.error("Immersive Jewelry: Diegesis {} cannot read workstation scene {} even at its simplest ({}); that station"
                + " opens without its scene", DiegesisCompat.installedVersion(), station, firstError);
        return null;
    }

    /** Whether a station's scene is registered. */
    public boolean hasScene(ResourceLocation station) {
        return built.containsKey(station);
    }

    /** Whether the bench's registered scene has the drawer's physical surfaces (else the drawer is a list). */
    public boolean hasPlacing(ResourceLocation station) {
        Built scene = built.get(station);
        return scene != null && scene.placing();
    }

    /** Whether the bench's registered scene shows the jars as library slots (else Jewelry's own clicks move their stacks). */
    public boolean hasJarSlots(ResourceLocation station) {
        Built scene = built.get(station);
        return scene != null && scene.jarSlots();
    }

    /** The fallback level a station's scene is registered at, or null without a scene. */
    @Nullable
    public SceneComfort.Fallback level(ResourceLocation station) {
        Built scene = built.get(station);
        return scene == null ? null : scene.level();
    }

    /** This player's presentation options, as the comfort transform takes them, and what of the library can be used. */
    static SceneComfort.Options options() {
        boolean storage = com.otectus.immersivejewelry.workstation.station.JewelryStations.nativeStorage();
        return new SceneComfort.Options(ClientConfig.get(ClientConfig.NO_ZOOM), ClientConfig.get(ClientConfig.INSTANT_TRANSITIONS),
                ClientConfig.get(ClientConfig.REDUCED_MOTION), ClientConfig.calmLooseItems(), ClientConfig.get(ClientConfig.WORK_LAMP),
                storage && DiegesisCompat.has(DiegesisCompat.Feature.DRAWER_MOTION), storage);
    }

    /**
     * The plane the camera is mirrored across for this player ({@code StationView.mirrorCamera}): the block's middle for the
     * left working side, none (NaN) for the authored right side.
     */
    public static float mirrorPlane() {
        if (!DiegesisCompat.has(DiegesisCompat.Feature.CAMERA_MIRROR)) return Float.NaN;
        return ClientConfig.get(ClientConfig.WORKING_SIDE) == ClientConfig.WorkingSide.LEFT ? 8F : Float.NaN;
    }

    /**
     * The camera a station's scene is seen through now (position and target in scene pixels), for mapping pointer motion: the
     * authored shot, reflected across {@code mirror} (x' = 2p - x, NaN: as authored) when the library mirrors it.
     */
    @Nullable
    public float[] camera(ResourceLocation station, float mirror) {
        float[] authored = camera(station);
        if (authored == null || !Float.isFinite(mirror)) return authored;
        authored[0] = 2F * mirror - authored[0];
        authored[3] = 2F * mirror - authored[3];
        return authored;
    }

    /** The authored camera of a station's scene (position and target in scene pixels). */
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
