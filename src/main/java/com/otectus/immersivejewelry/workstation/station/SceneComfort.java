package com.otectus.immersivejewelry.workstation.station;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Set;

/**
 * The station scenes' comfort transform: the authored scene JSON with one player's presentation options applied, before the
 * library's scene codec reads it. Pure JSON in, JSON out, with no client class, so the GameTests parse exactly what a client
 * registers (normal and calm). The client's {@code SceneLoader} is its only other caller.
 *
 * <p>What it changes, and only this: no zoom keeps the player's field of view (and the loupe look where the shot is); instant
 * transitions cut instead of easing; reduced motion or instant transitions stop the HUD sliding; calm loose items drops
 * {@code placing.physics}, so staged stacks stand exactly where they are put (the same group, the same stored poses); the
 * work lamp switched off drops the lamp node's light. Whatever the options, neighbouring stations are never gone over to and
 * nothing is cleared out of view. Nothing here reaches an item, a work or an outcome.</p>
 *
 * <p>Compatibility: the scenes are written for the Diegesis Jewelry was built against, and the library parses them strictly. A
 * newer (or different) Diegesis may not read every field, so {@link #fallback} simplifies a scene by {@link Fallback} levels,
 * each dropping what the one before kept, down to a bare scene of the camera and the pick zones; the client's loader uses the
 * first level the installed library reads. Options also drop the parts whose client API is missing: the drawer's surfaces
 * ({@code placing}) without moving surfaces or native storage, the jars' library slots without native storage (they then
 * take items by Jewelry's own commands, as before 1.3).</p>
 */
public final class SceneComfort {
    /** The node whose light is the bench's work lamp. */
    public static final String LAMP = "lamp";

    /**
     * One player's presentation options, and what of the library this client can use: {@code placing} the drawer's physical
     * surfaces, {@code jarSlots} the jars as library slots.
     */
    public record Options(boolean noZoom, boolean instantTransitions, boolean reducedMotion, boolean calmLooseItems, boolean workLamp,
                          boolean placing, boolean jarSlots) {
        public static final Options AUTHORED = new Options(false, false, false, false, true, true, true);
        public static final Options CALM = new Options(false, false, true, true, true, true, true);

        public Options(boolean noZoom, boolean instantTransitions, boolean reducedMotion, boolean calmLooseItems, boolean workLamp) {
            this(noZoom, instantTransitions, reducedMotion, calmLooseItems, workLamp, true, true);
        }
    }

    /** How far a scene is simplified for a Diegesis that cannot read it as written; each level includes the ones before. */
    public enum Fallback {
        /** As written. */
        AUTHORED,
        /** Without the drawer's physics. */
        NO_PHYSICS,
        /** Without the placing's newer fields (counts, poses, hover, open sides, headroom). */
        PLAIN_PLACING,
        /** Without the slots' count settings. */
        PLAIN_SLOTS,
        /** Without the drawer's surfaces: the drawer is a list. */
        NO_PLACING,
        /** The jars take items by Jewelry's commands instead of being library slots. */
        NO_JAR_SLOTS,
        /** Without lights, glances and the drawer's carried-stack size. */
        NO_EXTRAS,
        /** The camera, the drawer settings every release had, and the pick zones: nothing else. */
        BARE
    }

    /** The node fields a {@link Fallback#BARE} scene keeps. */
    private static final Set<String> BARE_NODE = Set.of("id", "collider", "strokes", "pickable", "tooltip");
    /** A bare camera still never goes over to neighbouring stations ({@code neighbors} off, as {@link #apply} sets it). */
    private static final Set<String> BARE_CAMERA = Set.of("position", "target", "fov", "transition", "neighbors");

    /** Simplifies {@code json} (in place) to {@code level}, and returns it. */
    public static JsonObject fallback(JsonObject json, Fallback level) {
        int at = level.ordinal();
        JsonObject placing = json.has("placing") ? json.getAsJsonObject("placing") : null;
        if (at >= Fallback.NO_PHYSICS.ordinal() && placing != null) placing.remove("physics");
        if (at >= Fallback.PLAIN_PLACING.ordinal() && placing != null) {
            for (String field : new String[]{"counts", "frame_pose", "hover_lift"}) placing.remove(field);
            if (placing.has("surfaces")) {
                for (JsonElement e : placing.getAsJsonArray("surfaces")) {
                    e.getAsJsonObject().remove("open");
                    e.getAsJsonObject().remove("headroom");
                }
            }
        }
        if (at >= Fallback.PLAIN_SLOTS.ordinal()) {
            forNodes(json, node -> {
                if (node.has("slot")) {
                    node.getAsJsonObject("slot").remove("count_facing");
                    node.getAsJsonObject("slot").remove("counts");
                }
            });
        }
        if (at >= Fallback.NO_PLACING.ordinal()) json.remove("placing");
        if (at >= Fallback.NO_JAR_SLOTS.ordinal()) withoutJarSlots(json);
        if (at >= Fallback.NO_EXTRAS.ordinal()) {
            forNodes(json, node -> node.remove("light"));
            if (json.has("camera")) json.getAsJsonObject("camera").remove("glances");
            if (json.has("inventory_drawer")) json.getAsJsonObject("inventory_drawer").remove("carry_size");
        }
        if (at >= Fallback.BARE.ordinal()) bare(json);
        return json;
    }

    /** The jars' library slots become the pick zones they were before 1.3: their clicks and placements are Jewelry's then. */
    private static void withoutJarSlots(JsonObject json) {
        forNodes(json, node -> {
            if (!node.has("slot")) return;
            String slot = node.getAsJsonObject("slot").has("slot") ? node.getAsJsonObject("slot").get("slot").getAsString() : "";
            if (!slot.startsWith(JewelryStations.SUPPLIES)) return;
            node.remove("slot");
            node.addProperty("takes_items", true);
            if (!node.has("collider")) {
                JsonObject collider = new JsonObject();
                collider.add("from", vec(-0.6F, -0.8F, -0.6F));
                collider.add("to", vec(0.6F, 0.8F, 0.6F));
                node.add("collider", collider);
            }
        });
    }

    private static void bare(JsonObject json) {
        for (String key : json.keySet().toArray(new String[0])) {
            if (!Set.of("camera", "work_plane", "idle_visible", "inventory_drawer", "clear_view", "nodes").contains(key)) json.remove(key);
        }
        keepOnly(json, "camera", BARE_CAMERA);
        keepOnly(json, "inventory_drawer", Set.of("side", "compact"));
        keepOnly(json, "clear_view", Set.of("blocks", "entities"));
        forNodes(json, node -> {
            for (String key : node.keySet().toArray(new String[0])) if (!BARE_NODE.contains(key)) node.remove(key);
        });
    }

    private static void keepOnly(JsonObject json, String key, Set<String> fields) {
        if (!json.has(key)) return;
        JsonObject inner = json.getAsJsonObject(key);
        for (String field : inner.keySet().toArray(new String[0])) if (!fields.contains(field)) inner.remove(field);
    }

    private static void forNodes(JsonObject json, java.util.function.Consumer<JsonObject> action) {
        if (!json.has("nodes")) return;
        for (JsonElement e : json.getAsJsonArray("nodes")) action.accept(e.getAsJsonObject());
    }

    private static JsonArray vec(float x, float y, float z) {
        JsonArray out = new JsonArray();
        out.add(x);
        out.add(y);
        out.add(z);
        return out;
    }

    /** Applies {@code options} to {@code json} in place and returns it. */
    public static JsonObject apply(JsonObject json, Options options) {
        boolean noZoom = options.noZoom();
        boolean instant = options.instantTransitions();
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
        if (options.reducedMotion() || instant) hud.addProperty("slide", false);
        json.add("hud", hud);
        if (options.calmLooseItems() && json.has("placing")) json.getAsJsonObject("placing").remove("physics");
        if (!options.placing()) json.remove("placing");
        if (!options.jarSlots()) withoutJarSlots(json);
        if (!options.workLamp() && json.has("nodes")) {
            for (JsonElement e : json.getAsJsonArray("nodes")) {
                JsonObject node = e.getAsJsonObject();
                if (node.has("id") && LAMP.equals(node.get("id").getAsString())) node.remove("light");
            }
        }
        return json;
    }

    private SceneComfort() {}
}
