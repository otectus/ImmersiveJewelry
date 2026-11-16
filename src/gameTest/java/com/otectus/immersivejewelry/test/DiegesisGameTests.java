package com.otectus.immersivejewelry.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.workstation.station.JewelryStations;
import com.otectus.immersivejewelry.workstation.station.SceneComfort;
import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.scene.InventoryDrawer;
import com.pockyl.diegesis.api.scene.NodeDefinition;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.scene.PlacingPhysics;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import com.pockyl.diegesis.api.scene.SlotBinding;
import com.pockyl.diegesis.api.station.Authority;
import com.pockyl.diegesis.api.station.StationType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Diegesis adapter against the library actually loaded: it is a version the mod declares it accepts, both station
 * scenes parse with the library's own strict scene codec (as authored and after every comfort transform) and declare what the
 * behaviours rely on, and both stations stay Jewelry-run (server authority, no vanilla menu, one operator, the block left in
 * place). Since 1.3 the bench has exactly two library groups, its supply jars and its staging drawer; the wheel has none.
 */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiegesisGameTests {

    @GameTest(template = "empty")
    public static void theLoadedDiegesisIsOneTheModAccepts(GameTestHelper h) {
        IModInfo diegesis = ModList.get().getModContainerById(Diegesis.MOD_ID).orElseThrow().getModInfo();
        IModInfo.ModVersion dependency = ModList.get().getModContainerById(ImmersiveJewelry.MOD_ID).orElseThrow().getModInfo()
                .getDependencies().stream().filter(d -> d.getModId().equals(Diegesis.MOD_ID)).findFirst().orElse(null);
        h.assertTrue(dependency != null && dependency.isMandatory() && dependency.getSide() == IModInfo.DependencySide.BOTH,
                "Diegesis is a required dependency on both sides");
        h.assertTrue(dependency.getVersionRange().containsVersion(diegesis.getVersion()),
                "Diegesis " + diegesis.getVersion() + " is within " + dependency.getVersionRange());
        // Forge refuses to start with an older library instead of loading scenes it cannot parse.
        h.assertFalse(dependency.getVersionRange().containsVersion(new DefaultArtifactVersion("1.0.0-beta.9")),
                "1.0.0-beta.9 is refused: its strict scene parser rejects takes_items and carry_size");
        if (!compatRun()) {
            h.assertFalse(dependency.getVersionRange().containsVersion(new DefaultArtifactVersion("1.0.0-beta.10")),
                    "1.0.0-beta.10 is refused: its strict scene parser rejects placing.physics, counts and the camera-facing slot counts");
        }
        // A Diegesis update never stops the game from starting: newer releases are accepted and probed (DiegesisCompat).
        for (String newer : List.of("1.0.0-beta.12", "1.0.0-rc.1", "1.0.0", "1.4.2", "2.0.0")) {
            h.assertTrue(dependency.getVersionRange().containsVersion(new DefaultArtifactVersion(newer)), newer + " is accepted");
        }
        h.succeed();
    }

    /** Run against another Diegesis than the tested one ({@code -PdiegesisRuntimeFile}): only the compatibility guarantees hold. */
    static boolean compatRun() {
        return Boolean.getBoolean("immersive_jewelry.compatRun");
    }

    /** The first fallback level of a station's scene the loaded library reads, as the client's loader looks for it. */
    private static SceneComfort.Fallback firstReadable(ResourceLocation station) {
        for (SceneComfort.Fallback level : SceneComfort.Fallback.values()) {
            JsonObject json = SceneComfort.fallback(SceneComfort.apply(json(station).getAsJsonObject(), SceneComfort.Options.AUTHORED), level);
            var result = SceneDefinition.CODEC.parse(JsonOps.INSTANCE, json);
            if (result.result().isPresent() && result.error().isEmpty()) return level;
        }
        return null;
    }

    @GameTest(template = "empty")
    public static void theStationScenesParseWithTheLibrarysCodec(GameTestHelper h) {
        if (compatRun()) {
            // Another Diegesis: some level of each scene must be readable, so the stations still open.
            for (ResourceLocation id : List.of(JewelryStations.WHEEL_ID, JewelryStations.BENCH_ID)) {
                SceneComfort.Fallback level = firstReadable(id);
                h.assertTrue(level != null, id + " is readable at some fallback level");
                com.otectus.immersivejewelry.ImmersiveJewelry.LOGGER.info("compat run: {} reads at {}", id, level);
            }
            h.succeed();
            return;
        }
        SceneDefinition wheel = scene(h, JewelryStations.WHEEL_ID);
        SceneDefinition bench = scene(h, JewelryStations.BENCH_ID);
        // Every node a carried stack is placed on by code takes items (the behaviours' onItemClick), and nothing else does.
        h.assertTrue(takers(wheel).equals(Set.of("handle", "stone", "lap")), "wheel placement nodes: " + takers(wheel));
        // The supply jars are library slots now (their click is the library's transfer), so they no longer take items by code.
        Set<String> benchPlaces = new HashSet<>(Set.of("piece", "dock"));
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) benchPlaces.add("tray_" + i);
        h.assertTrue(takers(bench).equals(benchPlaces), "bench placement nodes: " + takers(bench));
        for (int i = 0; i < JewelersBenchBlockEntity.SUPPLIES; i++) {
            int index = i;
            SlotBinding slot = bench.nodes().stream().filter(n -> n.id().equals("supply_" + index)).findFirst().flatMap(NodeDefinition::slot)
                    .orElse(null);
            h.assertTrue(slot != null && slot.slot().group().equals(JewelryStations.SUPPLIES) && slot.slot().index() == index,
                    "supply_" + i + " shows supplies[" + i + "]");
            h.assertTrue(slot.counts() == SlotBinding.Counts.ALWAYS && slot.countFacing() == SlotBinding.CountFacing.CAMERA,
                    "the jars' counts are always shown, facing the camera");
        }
        // The staging drawer: the staging group on two walled surfaces, gentle physics that never throws a stack out.
        PlacingOptions placing = bench.placing().orElse(null);
        h.assertTrue(placing != null && placing.group().equals(JewelryStations.STAGING) && placing.surfaces().size() == 2, "the drawer's placing");
        h.assertTrue(placing.surfaces().stream().allMatch(sf -> sf.open().isEmpty()), "closed lips on every side");
        h.assertTrue(placing.counts() == SlotBinding.Counts.HOVER && !placing.framePose() && placing.hoverLift() == 0F, "hover counts, upright, still");
        PlacingPhysics physics = placing.physics().orElse(null);
        h.assertTrue(physics != null && physics.toss() == 0F && !physics.handPushes() && physics.fallOff() == PlacingPhysics.FallOff.HELD
                && physics.bounce() == 0F, "no toss, no pushing hand, held-only fall-off, no bounce");
        h.assertTrue(wheel.placing().isEmpty(), "the wheel places nothing freely");
        for (SceneDefinition scene : List.of(wheel, bench)) {
            InventoryDrawer drawer = scene.inventoryDrawer().orElse(null);
            h.assertTrue(drawer != null && drawer.compact() && !drawer.alwaysOpen() && !drawer.keepsHotbar(),
                    "a compact drawer that opens on request, never a permanent grid");
            h.assertTrue(drawer.carrySize().isPresent(), "a stack carried into the scene has a set size, not the last one carried");
            h.assertTrue(!scene.clearView().blocks() && !scene.clearView().entities(), "nothing is cleared out of view");
            h.assertTrue(scene.camera().interactiveAt() == 1F, "clicks count once the camera has arrived");
            h.assertTrue(scene.nodes().stream().anyMatch(n -> n.id().equals("work") && !n.pickable()), "the painted 'work' node");
        }
        h.succeed();
    }

    /** What each client registers: the authored scene after the comfort transform, normal, calm and without the lamp. */
    @GameTest(template = "empty")
    public static void theComfortTransformedScenesParseToo(GameTestHelper h) {
        if (compatRun()) {
            h.succeed();
            return;
        }
        for (ResourceLocation id : List.of(JewelryStations.WHEEL_ID, JewelryStations.BENCH_ID)) {
            for (SceneComfort.Options options : List.of(SceneComfort.Options.AUTHORED, SceneComfort.Options.CALM,
                    new SceneComfort.Options(true, true, true, false, false))) {
                SceneDefinition scene = parse(h, id, SceneComfort.apply(json(id).getAsJsonObject(), options));
                h.assertTrue(!scene.clearView().blocks() && !scene.clearView().entities(), id + ": nothing cleared out of view");
                if (id.equals(JewelryStations.BENCH_ID)) {
                    PlacingOptions placing = scene.placing().orElseThrow();
                    h.assertTrue(placing.group().equals(JewelryStations.STAGING), "the same group whatever the options");
                    h.assertTrue(placing.physics().isPresent() != options.calmLooseItems(), "calm loose items: no physics");
                    boolean lit = scene.nodes().stream().anyMatch(n -> n.id().equals(SceneComfort.LAMP) && n.light().isPresent());
                    h.assertTrue(lit == options.workLamp(), "the work lamp follows its option");
                    scene.nodes().stream().filter(n -> n.id().equals(SceneComfort.LAMP)).flatMap(n -> n.light().stream())
                            .forEach(light -> h.assertTrue(light.flicker() == 0F, "a steady lamp"));
                }
            }
        }
        h.succeed();
    }

    /**
     * Every fallback level of both scenes parses with the tested library and keeps the stations usable: the painted work node and
     * every pick zone the behaviours name; the drawer's surfaces go at {@code NO_PLACING}, and from {@code NO_JAR_SLOTS} the jars are
     * pick zones that take items by Jewelry's commands.
     */
    @GameTest(template = "empty")
    public static void everyFallbackLevelOfTheScenesStaysUsable(GameTestHelper h) {
        if (compatRun()) {
            h.succeed();
            return;
        }
        Set<String> benchNodes = new HashSet<>(Set.of("work", "piece", "folio", "pouch", "dock", "loupe", "drawer"));
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) benchNodes.add("tray_" + i);
        for (int i = 0; i < JewelersBenchBlockEntity.SUPPLIES; i++) benchNodes.add("supply_" + i);
        Set<String> wheelNodes = Set.of("work", "handle", "stone", "lap", "collar", "cup", "loupe", "lever");
        for (SceneComfort.Fallback level : SceneComfort.Fallback.values()) {
            for (ResourceLocation id : List.of(JewelryStations.WHEEL_ID, JewelryStations.BENCH_ID)) {
                SceneDefinition scene = parse(h, id, SceneComfort.fallback(SceneComfort.apply(json(id).getAsJsonObject(),
                        SceneComfort.Options.AUTHORED), level));
                Set<String> ids = scene.nodes().stream().map(NodeDefinition::id).collect(Collectors.toSet());
                Set<String> needed = id.equals(JewelryStations.BENCH_ID) ? benchNodes : wheelNodes;
                h.assertTrue(ids.containsAll(needed), id + " at " + level + " keeps its nodes, missing: "
                        + needed.stream().filter(n -> !ids.contains(n)).toList());
                h.assertTrue(!scene.clearView().blocks() && !scene.clearView().entities(), id + " at " + level + ": nothing cleared");
                if (!id.equals(JewelryStations.BENCH_ID)) continue;
                boolean placing = scene.placing().isPresent();
                h.assertTrue(placing == level.ordinal() < SceneComfort.Fallback.NO_PLACING.ordinal(), "the drawer's surfaces at " + level);
                boolean jarSlots = scene.nodes().stream().anyMatch(n -> n.id().startsWith("supply_") && n.slot().isPresent());
                h.assertTrue(jarSlots == level.ordinal() < SceneComfort.Fallback.NO_JAR_SLOTS.ordinal(), "the jars' slots at " + level);
                if (!jarSlots && level.ordinal() < SceneComfort.Fallback.BARE.ordinal()) {
                    h.assertTrue(scene.nodes().stream().filter(n -> n.id().startsWith("supply_")).allMatch(NodeDefinition::takesItems),
                            "the jars take items by Jewelry's commands at " + level);
                }
            }
        }
        // And the client's options drop what its library cannot do, whatever the level.
        JsonObject noStorage = SceneComfort.apply(json(JewelryStations.BENCH_ID).getAsJsonObject(),
                new SceneComfort.Options(false, false, false, false, true, false, false));
        SceneDefinition plain = parse(h, JewelryStations.BENCH_ID, noStorage);
        h.assertTrue(plain.placing().isEmpty() && plain.nodes().stream().noneMatch(n -> n.slot().isPresent()), "no drawer surfaces, no jar slots");
        h.succeed();
    }

    /** The compatibility probe finds everything Jewelry uses in the tested library, and the stations follow what it found. */
    @GameTest(template = "empty")
    public static void theCompatibilityProbeAndTheStationsAgree(GameTestHelper h) {
        var on = com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.probe().available();
        com.otectus.immersivejewelry.ImmersiveJewelry.LOGGER.info("Diegesis {} compatibility: {}",
                com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.installedVersion(),
                com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.status());
        if (!compatRun()) {
            h.assertTrue(on.contains(com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.Feature.STATIONS)
                    && on.contains(com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.Feature.NATIVE_STORAGE),
                    "with the tested release every server feature is on: " + on);
        }
        h.assertTrue(JewelryStations.available() == on.contains(com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.Feature.STATIONS),
                "the stations are registered exactly when the library has what they need");
        h.setBlock(TestSupport.STATION, com.otectus.immersivejewelry.registry.ModBlocks.JEWELERS_BENCH.get());
        var bench = h.getLevel().getBlockEntity(h.absolutePos(TestSupport.STATION));
        h.assertTrue((bench instanceof com.otectus.immersivejewelry.blockentity.HostedBenchBlockEntity) == JewelryStations.nativeStorage(),
                "the world's bench is the library's host exactly when native storage is on");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theStationsStayJewelryRun(GameTestHelper h) {
        for (ResourceLocation id : List.of(JewelryStations.WHEEL_ID, JewelryStations.BENCH_ID)) {
            StationType type = Diegesis.stationType(id).orElse(null);
            h.assertTrue(type != null, id + " is registered");
            h.assertTrue(type.authority() == Authority.SERVER && type.menuBinding() == null, id + ": server authority, no vanilla menu");
            if (id.equals(JewelryStations.WHEEL_ID)) {
                h.assertTrue(!type.schema().hasItems(), id + ": no library slots, every item move is a Jewelry operation");
            } else {
                h.assertTrue(type.schema().slotGroups().keySet().equals(Set.of(JewelryStations.SUPPLIES, JewelryStations.STAGING))
                        && type.schema().outputs().isEmpty() && type.schema().controls().isEmpty() && type.schema().tanks().isEmpty(),
                        id + ": only the supply jars and the staging drawer are library slots");
                h.assertTrue(type.schema() == com.otectus.immersivejewelry.workstation.station.DiegesisStations.BENCH_SCHEMA, id + ": the schema the block entity's host state is made with");
            }
            h.assertTrue(!type.shared() && type.reach() == JewelryStations.REACH, id + ": one operator within reach");
            h.assertTrue(!type.hidesBlock() && type.hiddenBlocks(h.getLevel(), h.absolutePos(TestSupport.STATION)).isEmpty(),
                    id + ": the block stays in the world");
        }
        h.succeed();
    }

    /** A station's authored scene, read from the mod's own resources and parsed exactly as the client parses it. */
    private static SceneDefinition scene(GameTestHelper h, ResourceLocation station) {
        return parse(h, station, json(station));
    }

    private static JsonElement json(ResourceLocation station) {
        Path file = ModList.get().getModFileById(ImmersiveJewelry.MOD_ID).getFile()
                .findResource("assets", station.getNamespace(), "diegesis", "stations", station.getPath() + ".json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    private static SceneDefinition parse(GameTestHelper h, ResourceLocation station, JsonElement json) {
        var result = SceneDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        h.assertTrue(result.error().isEmpty(), station + " parses: " + result.error().map(e -> e.message()).orElse(""));
        return result.result().orElseThrow();
    }

    private static Set<String> takers(SceneDefinition scene) {
        return scene.nodes().stream().filter(NodeDefinition::takesItems).map(NodeDefinition::id).collect(Collectors.toSet());
    }
}
