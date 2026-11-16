package com.otectus.immersivejewelry.integration.diegesis;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.Restriction;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What of Diegesis this game can use. Diegesis is a beta library whose API still changes, and Immersive Jewelry accepts newer
 * releases than the one it was built against ({@code diegesis_version_range}), so nothing here assumes the API it was
 * compiled with is the API that is loaded.
 *
 * <p>At start-up {@link #probe()} looks up, by reflection only, every Diegesis class, method and field Jewelry uses, grouped
 * into {@link Feature}s. A feature whose members are all there (with the same signatures, and whose interfaces Jewelry implements
 * have no new abstract method) is on; any missing member turns that feature off, and the features that need it. What is off is
 * simply not used: the code paths check {@link #has} before they touch the library, and every call is also wrapped so that a
 * change the probe could not see ({@link LinkageError}: a method whose signature changed under the same name) turns the
 * feature off at that moment ({@link #disable}) instead of crashing. One summary line says what is on and what is missing.</p>
 *
 * <p>This class never imports a Diegesis type, so it loads with any Diegesis, or none.</p>
 *
 * <p>Degraded modes, from mild to severe: without {@link Feature#KEY_HOOKS} station keys come through Forge's screen events (as
 * in 1.2); without {@link Feature#CAMERA_MIRROR} only the authored side is used; without {@link Feature#DRAWER_MOTION} or
 * {@link Feature#ROOM_QUERY} the staging drawer is a list rather than a physical surface; without {@link Feature#NATIVE_STORAGE}
 * the jars and the drawer move stacks only through Jewelry's own commands; without {@link Feature#CLIENT} or
 * {@link Feature#STATIONS} the hands-on stations do not open, and the blocks keep their walk-up use (mounting, placing, taking,
 * packing), with a message saying why. Items and work are never stored in the library, so no mode loses any.</p>
 */
public final class DiegesisCompat {
    public static final String MOD_ID = "diegesis";

    /** What Jewelry uses Diegesis for, each on only when everything it needs is there. */
    public enum Feature {
        /** The two stations: registration, the station logic's lifecycle hooks. Everything else needs it. */
        STATIONS(null, false),
        /** The bench's supply jars and staging drawer as library slots bound to the bench's own arrays (a {@code StationHost}). */
        NATIVE_STORAGE(STATIONS, false),
        /** The client half: behaviours, scenes, focus queries. */
        CLIENT(STATIONS, true),
        /** Station keys through the library's {@code onKey} (else Forge's screen events). */
        KEY_HOOKS(CLIENT, true),
        /** The working side ({@code mirrorCamera}). */
        CAMERA_MIRROR(CLIENT, true),
        /** The drawer's sliding surfaces and their visibility. */
        DRAWER_MOTION(CLIENT, true),
        /** The drawer's free-room query before a shift-click. */
        ROOM_QUERY(CLIENT, true),
        /** Jewelry's particle styles (grinding slurry, dust, chips, glints, flecks) and the library's splash: looks only. */
        PARTICLES(CLIENT, true);

        @Nullable
        final Feature needs;
        final boolean client;

        Feature(@Nullable Feature needs, boolean client) {
            this.needs = needs;
            this.client = client;
        }
    }

    private static final String API = "com.pockyl.diegesis.api.";
    private static final String MC = "net.minecraft.";

    /** One member Jewelry uses: a class (always), and a method, a field or an exact set of abstract methods in it. */
    record Member(Feature feature, String type, @Nullable String name, String[] params, Kind kind) {
        enum Kind { CLASS, METHOD, CONSTRUCTOR, FIELD, ABSTRACTS }

        String describe() {
            String simple = type.substring(type.lastIndexOf('.') + 1);
            return switch (kind) {
                case CLASS -> simple;
                case FIELD -> simple + "." + name;
                case CONSTRUCTOR -> "new " + simple + "(" + String.join(", ", Arrays.stream(params)
                        .map(p -> p.substring(p.lastIndexOf('.') + 1)).toList()) + ")";
                case ABSTRACTS -> simple + " (abstract methods " + Arrays.toString(params) + ")";
                case METHOD -> simple + "." + name + "(" + String.join(", ", Arrays.stream(params)
                        .map(p -> p.substring(p.lastIndexOf('.') + 1)).toList()) + ")";
            };
        }
    }

    private static Member method(Feature f, String type, String name, String... params) {
        return new Member(f, type, name, params, Member.Kind.METHOD);
    }

    private static Member constructor(Feature f, String type, String... params) {
        return new Member(f, type, "<init>", params, Member.Kind.CONSTRUCTOR);
    }

    private static Member field(Feature f, String type, String name) {
        return new Member(f, type, name, new String[0], Member.Kind.FIELD);
    }

    /** {@code type} declares exactly these abstract methods (names), so a class implementing them is still complete. */
    private static Member abstracts(Feature f, String type, String... names) {
        return new Member(f, type, null, names, Member.Kind.ABSTRACTS);
    }

    /** Every member of the library Jewelry touches, by feature. {@code DiegesisCompatTest} holds it to the tested release. */
    static final List<Member> MEMBERS;

    static {
        Feature S = Feature.STATIONS, N = Feature.NATIVE_STORAGE, C = Feature.CLIENT;
        String station = API + "station.", state = API + "state.", client = API + "client.", scene = API + "scene.";
        String ctx = station + "StationContext", player = MC + "world.entity.player.Player", stack = MC + "world.item.ItemStack";
        String rl = MC + "resources.ResourceLocation", tag = MC + "nbt.CompoundTag", view = client + "StationView";
        String builder = station + "StationType$Builder";
        List<Member> m = new ArrayList<>();
        // The stations.
        m.add(method(S, API + "Diegesis", "station", rl));
        m.add(method(S, API + "Diegesis", "close", MC + "server.level.ServerPlayer"));
        m.add(method(S, builder, "block", "java.util.function.Supplier"));
        m.add(method(S, builder, "schema", state + "StationSchema"));
        m.add(method(S, builder, "orientation", station + "StationType$Orientation"));
        m.add(method(S, builder, "reach", "double"));
        m.add(method(S, builder, "exclusive"));
        m.add(method(S, builder, "openWhen", station + "StationType$OpenCondition"));
        m.add(method(S, builder, "logic", station + "StationLogic$Factory"));
        m.add(method(S, builder, "register"));
        m.add(field(S, station + "StationType$Orientation", "BLOCK"));
        m.add(abstracts(S, station + "StationType$OpenCondition", "test"));
        m.add(abstracts(S, station + "StationLogic$Factory", "create"));
        m.add(abstracts(S, station + "StationLogic"));
        m.add(method(S, station + "StationLogic", "onOpen", ctx, player));
        m.add(method(S, station + "StationLogic", "onClose", ctx, player));
        m.add(method(S, station + "StationLogic", "onCustomAction", ctx, player, "java.lang.String", tag));
        m.add(method(S, ctx, "blockEntity"));
        m.add(method(S, ctx, "pos"));
        m.add(method(S, ctx, "state"));
        m.add(method(S, state + "StationSchema", "builder"));
        m.add(method(S, state + "StationSchema$Builder", "build"));
        // The bench's library slots, bound to its own arrays.
        m.add(method(N, state + "StationSchema$Builder", "slots", "java.lang.String", "int", state + "SlotRules"));
        m.add(field(N, state + "SlotRules", "DEFAULT"));
        m.add(method(N, state + "SlotRules", "kept"));
        m.add(method(N, state + "SlotRules", "placed"));
        m.add(method(N, state + "SlotRules", "accepts", "java.util.function.Predicate"));
        m.add(abstracts(N, state + "StationHost", "stationState"));
        m.add(abstracts(N, state + "SlotStorage", "size", "get", "set"));
        m.add(method(N, state + "SlotStorage", "set", "int", stack));
        m.add(method(N, state + "SlotStorage", "get", "int"));
        m.add(constructor(N, state + "StationState", state + "StationSchema"));
        m.add(method(N, state + "StationState", "bindSlots", "java.lang.String", state + "SlotStorage"));
        m.add(method(N, state + "StationState", "isBound", "java.lang.String"));
        m.add(method(N, state + "StationState", "getPose", state + "SlotRef"));
        m.add(method(N, state + "StationState", "setPose", state + "SlotRef", state + "ItemPose"));
        m.add(method(N, state + "StationState", "getItem", state + "SlotRef"));
        m.add(method(N, state + "StationState", "save"));
        m.add(method(N, state + "StationState", "load", tag));
        m.add(method(N, state + "SlotRef", "of", "java.lang.String", "int"));
        m.add(method(N, state + "SlotRef", "group"));
        m.add(method(N, state + "SlotRef", "index"));
        m.add(method(N, state + "ItemPose", "on"));
        m.add(method(N, state + "ItemPose", "y"));
        m.add(method(N, state + "ItemPose", "raised", "float"));
        m.add(method(N, state + "ItemPose", "standingOn", "int"));
        // Without these two the library would use its permissive defaults: never bind slots then.
        m.add(method(N, station + "StationLogic", "mayPlace", ctx, player, state + "SlotRef", stack));
        m.add(method(N, station + "StationLogic", "mayTake", ctx, player, state + "SlotRef"));
        // The client half.
        String dc = client + "DiegesisClient";
        m.add(method(C, dc, "registerBehavior", rl, client + "StationBehavior$Factory"));
        m.add(method(C, dc, "registerScene", rl, scene + "SceneDefinition"));
        m.add(method(C, dc, "registerChildScreen", "java.util.function.Predicate"));
        m.add(method(C, dc, "isFocused"));
        m.add(method(C, dc, "focusedStation"));
        m.add(method(C, dc, "station", MC + "core.BlockPos"));
        m.add(method(C, dc, "isStationScreen", MC + "client.gui.screens.Screen"));
        m.add(method(C, dc, "hoveredStack"));
        m.add(method(C, dc, "isSceneDrawn", MC + "core.BlockPos"));
        m.add(field(C, scene + "SceneDefinition", "CODEC"));
        m.add(abstracts(C, client + "StationBehavior$Factory", "create"));
        m.add(abstracts(C, client + "StationBehavior"));
        m.add(abstracts(C, client + "ScreenOverlay", "render"));
        m.add(abstracts(C, client + "NodePainter", "paint"));
        String sb = client + "StationBehavior", event = API + "event.SceneEvent";
        m.add(method(C, sb, "onSceneReady", view));
        m.add(method(C, sb, "onOpen", view));
        m.add(method(C, sb, "onClose", view));
        m.add(method(C, sb, "frame", view, "float"));
        m.add(method(C, sb, "onEvent", view, event));
        m.add(method(C, sb, "onItemClick", view, "java.lang.String", stack, "int", "boolean"));
        m.add(method(C, sb, "onScroll", view, "java.lang.String", "double"));
        m.add(method(C, sb, "onInventoryShiftClick", view, "int", stack));
        m.add(method(C, client + "ScreenOverlay", "mouseClicked", "double", "double", "int"));
        m.add(method(C, client + "ScreenOverlay", "mouseScrolled", "double", "double", "double"));
        m.add(method(C, client + "ScreenOverlay", "onClosed"));
        m.add(method(C, view, "node", "java.lang.String"));
        m.add(method(C, view, "showOverlay", client + "ScreenOverlay"));
        m.add(method(C, view, "showCaption", MC + "network.chat.Component", "float"));
        m.add(method(C, view, "spawnParticles", rl, "org.joml.Vector3fc", "org.joml.Vector3fc", "float", "int", "int"));
        m.add(method(C, view, "loopSound", "java.lang.String", MC + "sounds.SoundEvent", "float", "float"));
        m.add(method(C, view, "playSound", MC + "sounds.SoundEvent", "java.lang.String", "float", "float"));
        m.add(method(C, view, "glance", "java.lang.String"));
        m.add(method(C, view, "glanceBack"));
        m.add(method(C, view, "currentGlance"));
        m.add(method(C, view, "age"));
        m.add(method(C, view, "level"));
        m.add(method(C, view, "pos"));
        m.add(method(C, view, "isInUse"));
        m.add(method(C, view, "isFocused"));
        m.add(method(C, view, "holdStroke"));
        m.add(method(C, view, "setInventoryFilter", "java.util.function.Predicate"));
        m.add(method(C, client + "NodeView", "moveTo", "float", "float", "float", "float"));
        m.add(method(C, client + "NodeView", "setTooltip", MC + "network.chat.Component"));
        m.add(method(C, client + "NodeView", "setPainter", client + "NodePainter"));
        m.add(method(C, client + "NodeView", "shake", "float", "float"));
        m.add(method(C, client + "NodeView", "setEnabled", "boolean"));
        m.add(method(C, event + "$Hover", "node"));
        m.add(method(C, event + "$Click", "node"));
        m.add(method(C, event + "$Click", "button"));
        m.add(method(C, event + "$Stroke", "node"));
        m.add(method(C, event + "$Stroke", "phase"));
        m.add(method(C, event + "$Stroke", "button"));
        m.add(method(C, event + "$Stroke", "point"));
        String phase = event + "$Stroke$Phase";
        for (String constant : new String[]{"START", "MOVE", "END", "HOVER"}) m.add(field(C, phase, constant));
        m.add(method(C, phase, "values"));
        m.add(method(C, phase, "ordinal"));
        // Optional client features.
        m.add(method(Feature.KEY_HOOKS, sb, "onKey", view, "int", "int", "int"));
        m.add(method(Feature.KEY_HOOKS, sb, "onChar", view, "char", "int"));
        m.add(method(Feature.CAMERA_MIRROR, view, "mirrorCamera", "float"));
        m.add(method(Feature.CAMERA_MIRROR, view, "cameraMirror"));
        m.add(method(Feature.DRAWER_MOTION, view, "setSurfaceTransform", "int", "org.joml.Matrix4fc"));
        m.add(method(Feature.DRAWER_MOTION, view, "setSurfaceVisible", "int", "boolean"));
        m.add(method(Feature.DRAWER_MOTION, view, "setPlacingVisible", "boolean"));
        m.add(method(Feature.ROOM_QUERY, view, "hasRoomFor", stack));
        String style = client + "ParticleStyle", styleBuilder = style + "$Builder";
        m.add(method(Feature.PARTICLES, dc, "registerParticle", rl, style));
        m.add(method(Feature.PARTICLES, style, "builder", rl + "[]"));
        m.add(method(Feature.PARTICLES, styleBuilder, "build"));
        m.add(method(Feature.PARTICLES, styleBuilder, "color", "int"));
        m.add(method(Feature.PARTICLES, styleBuilder, "size", "float", "float"));
        m.add(method(Feature.PARTICLES, styleBuilder, "life", "float", "float"));
        m.add(method(Feature.PARTICLES, styleBuilder, "gravity", "float"));
        m.add(method(Feature.PARTICLES, styleBuilder, "drag", "float"));
        m.add(method(Feature.PARTICLES, styleBuilder, "wobble", "float"));
        m.add(method(Feature.PARTICLES, styleBuilder, "stretch"));
        m.add(method(Feature.PARTICLES, styleBuilder, "emissive"));
        m.add(method(Feature.PARTICLES, styleBuilder, "pop"));
        m.add(field(Feature.PARTICLES, client + "DiegesisParticles", "SPLASH"));
        MEMBERS = List.copyOf(m);
    }

    /** What a probe found. */
    public record Report(Set<Feature> available, Map<Feature, List<String>> missing) {
        public boolean has(Feature feature) {
            return available.contains(feature);
        }
    }

    private static volatile Report report;
    private static final Set<Feature> disabled = Collections.synchronizedSet(EnumSet.noneOf(Feature.class));
    private static final Set<String> reported = Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * Probes the loaded Diegesis once (later calls return the same report) and logs what is on. The client features are
     * probed on a client only: their members name client classes a dedicated server does not have.
     */
    public static synchronized Report probe() {
        if (report != null) return report;
        Report r;
        if (!ModList.get().isLoaded(MOD_ID)) {
            r = new Report(EnumSet.noneOf(Feature.class), Map.of(Feature.STATIONS, List.of("the Diegesis mod")));
        } else {
            r = probe(name -> Class.forName(name, false, DiegesisCompat.class.getClassLoader()), FMLEnvironment.dist.isClient());
        }
        report = r;
        log(r);
        return r;
    }

    /** A class lookup that may fail ({@link ClassNotFoundException}, any {@link LinkageError}). */
    @FunctionalInterface
    public interface Lookup {
        Class<?> find(String name) throws ClassNotFoundException;
    }

    /** The probe itself, against any class lookup (tests pass one with members taken away). */
    public static Report probe(Lookup lookup, boolean client) {
        Map<Feature, List<String>> missing = new EnumMap<>(Feature.class);
        for (Member member : MEMBERS) {
            if (member.feature().client && !client) continue;
            String problem = check(lookup, member);
            if (problem != null) missing.computeIfAbsent(member.feature(), f -> new ArrayList<>()).add(problem);
        }
        EnumSet<Feature> on = EnumSet.noneOf(Feature.class);
        for (Feature f : Feature.values()) {
            if (f.client && !client) continue;
            if (!missing.containsKey(f) && (f.needs == null || on.contains(f.needs))) on.add(f);
        }
        return new Report(Collections.unmodifiableSet(on), Collections.unmodifiableMap(missing));
    }

    @Nullable
    private static String check(Lookup lookup, Member member) {
        try {
            Class<?> type = lookup.find(member.type());
            switch (member.kind()) {
                case CLASS -> {
                }
                case FIELD -> type.getField(member.name());
                case METHOD, CONSTRUCTOR -> {
                    Class<?>[] params = new Class<?>[member.params().length];
                    for (int i = 0; i < params.length; i++) params[i] = primitive(lookup, member.params()[i]);
                    if (member.kind() == Member.Kind.METHOD) type.getMethod(member.name(), params);
                    else type.getConstructor(params);
                }
                case ABSTRACTS -> {
                    Set<String> found = new LinkedHashSet<>();
                    for (Method method : type.getMethods()) {
                        if (Modifier.isAbstract(method.getModifiers()) && method.getDeclaringClass() != Object.class) found.add(method.getName());
                    }
                    if (!found.equals(new LinkedHashSet<>(Arrays.asList(member.params())))) {
                        return member.describe() + " now has " + found;
                    }
                }
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return member.describe();
        }
    }

    private static Class<?> primitive(Lookup lookup, String name) throws ClassNotFoundException {
        return switch (name) {
            case "int" -> int.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "boolean" -> boolean.class;
            case "char" -> char.class;
            case "long" -> long.class;
            default -> name.endsWith("[]") ? lookup.find(name.substring(0, name.length() - 2)).arrayType() : lookup.find(name);
        };
    }

    // ------------------------------------------------------------------------------------------------ use

    /** Whether a feature can be used now: the probe found it and nothing turned it off since. */
    public static boolean has(Feature feature) {
        Report r = probe();
        if (!r.has(feature)) return false;
        for (Feature f = feature; f != null; f = f.needs) if (disabled.contains(f)) return false;
        return true;
    }

    /**
     * Turns a feature off for the rest of the session because using it failed ({@code error}: usually a {@link LinkageError}
     * from a changed signature the probe could not see). Logged once per feature and place.
     */
    public static void disable(Feature feature, String where, Throwable error) {
        disabled.add(feature);
        if (reported.add(feature + "@" + where)) {
            ImmersiveJewelry.LOGGER.error("Immersive Jewelry: Diegesis {} does not provide what {} needs ({}); turned {} off for this session."
                    + " Items and work are not affected.", installedVersion(), where, error.toString(), feature);
        }
    }

    /** Runs {@code action} if the feature is on; a failure turns the feature off instead of escaping. */
    public static boolean run(Feature feature, String where, Runnable action) {
        if (!has(feature)) return false;
        try {
            action.run();
            return true;
        } catch (LinkageError e) {
            disable(feature, where, e);
            return false;
        }
    }

    /** The version of Diegesis loaded, or "none". */
    public static String installedVersion() {
        return ModList.get().getModContainerById(MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("none");
    }

    /**
     * The release Jewelry was built and tested against: the lower bound of the Diegesis range it declares (the range lives in
     * {@code gradle.properties} only). Empty when it cannot be read.
     */
    public static String testedVersion() {
        return ModList.get().getModContainerById(ImmersiveJewelry.MOD_ID).flatMap(c -> c.getModInfo().getDependencies().stream()
                .filter(d -> d.getModId().equals(MOD_ID)).findFirst()).map(DiegesisCompat::lowerBound).orElse("");
    }

    static String lowerBound(IModInfo.ModVersion dependency) {
        for (Restriction r : dependency.getVersionRange().getRestrictions()) {
            ArtifactVersion lower = r.getLowerBound();
            if (lower != null) return lower.toString();
        }
        return "";
    }

    private static void log(Report r) {
        String installed = installedVersion();
        String tested = testedVersion();
        boolean untested = !tested.isEmpty() && !tested.equals(installed);
        List<String> on = new ArrayList<>(), off = new ArrayList<>();
        for (Feature f : Feature.values()) {
            if (f.client && !FMLEnvironment.dist.isClient()) continue;
            (r.has(f) ? on : off).add(f.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (off.isEmpty()) {
            if (untested) {
                ImmersiveJewelry.LOGGER.warn("Immersive Jewelry: Diegesis {} is newer than the tested {}; every API it uses is present ({}).",
                        installed, tested, String.join(", ", on));
            } else {
                ImmersiveJewelry.LOGGER.info("Immersive Jewelry: Diegesis {}: {}.", installed, String.join(", ", on));
            }
            return;
        }
        ImmersiveJewelry.LOGGER.warn("Immersive Jewelry: Diegesis {} (tested: {}). On: {}. Off: {}. Items and work are not affected; the parts"
                + " that are off fall back as described in docs/ARCHITECTURE.md.", installed, tested.isEmpty() ? "?" : tested,
                on.isEmpty() ? "none" : String.join(", ", on), String.join(", ", off));
        r.missing().forEach((f, members) -> ImmersiveJewelry.LOGGER.warn("Immersive Jewelry:   {} needs {}", f, String.join("; ", members)));
    }

    /** For diagnostics: one line per feature. */
    public static List<String> status() {
        Report r = probe();
        List<String> out = new ArrayList<>();
        for (Feature f : Feature.values()) {
            if (f.client && !FMLEnvironment.dist.isClient()) continue;
            boolean on = has(f);
            List<String> missing = r.missing().getOrDefault(f, List.of());
            out.add(f.name().toLowerCase(java.util.Locale.ROOT) + ": " + (on ? "on" : "off")
                    + (missing.isEmpty() ? (on || r.has(f) ? "" : " (needs " + (f.needs == null ? "?" : f.needs.name().toLowerCase(java.util.Locale.ROOT)) + ")")
                    : " (missing " + String.join("; ", missing) + ")") + (r.has(f) && !on ? " (failed at run time)" : ""));
        }
        return out;
    }

    /** Test seam: forget the probe (unit tests only). */
    static void reset() {
        report = null;
        disabled.clear();
        reported.clear();
    }

    private DiegesisCompat() {}
}
