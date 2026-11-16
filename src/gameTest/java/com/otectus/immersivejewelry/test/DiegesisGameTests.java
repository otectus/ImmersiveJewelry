package com.otectus.immersivejewelry.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.workstation.station.JewelryStations;
import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.scene.InventoryDrawer;
import com.pockyl.diegesis.api.scene.NodeDefinition;
import com.pockyl.diegesis.api.scene.SceneDefinition;
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
 * scenes parse with the library's own strict scene codec and declare what the behaviours rely on, and both stations stay
 * Jewelry-run (server authority, no vanilla menu, no library slots, one operator, the block left in place).
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
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theStationScenesParseWithTheLibrarysCodec(GameTestHelper h) {
        SceneDefinition wheel = scene(h, JewelryStations.WHEEL_ID);
        SceneDefinition bench = scene(h, JewelryStations.BENCH_ID);
        // Every node a carried stack is placed on by code takes items (the behaviours' onItemClick), and nothing else does.
        h.assertTrue(takers(wheel).equals(Set.of("handle", "stone", "lap")), "wheel placement nodes: " + takers(wheel));
        Set<String> benchPlaces = new HashSet<>(Set.of("piece", "dock"));
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) benchPlaces.add("tray_" + i);
        for (int i = 0; i < JewelersBenchBlockEntity.SUPPLIES; i++) benchPlaces.add("supply_" + i);
        h.assertTrue(takers(bench).equals(benchPlaces), "bench placement nodes: " + takers(bench));
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

    @GameTest(template = "empty")
    public static void theStationsStayJewelryRun(GameTestHelper h) {
        for (ResourceLocation id : List.of(JewelryStations.WHEEL_ID, JewelryStations.BENCH_ID)) {
            StationType type = Diegesis.stationType(id).orElse(null);
            h.assertTrue(type != null, id + " is registered");
            h.assertTrue(type.authority() == Authority.SERVER && type.menuBinding() == null, id + ": server authority, no vanilla menu");
            h.assertTrue(!type.schema().hasItems(), id + ": no library slots, every item move is a Jewelry operation");
            h.assertTrue(!type.shared() && type.reach() == JewelryStations.REACH, id + ": one operator within reach");
            h.assertTrue(!type.hidesBlock() && type.hiddenBlocks(h.getLevel(), h.absolutePos(TestSupport.STATION)).isEmpty(),
                    id + ": the block stays in the world");
        }
        h.succeed();
    }

    /** A station's authored scene, read from the mod's own resources and parsed exactly as the client parses it. */
    private static SceneDefinition scene(GameTestHelper h, ResourceLocation station) {
        Path file = ModList.get().getModFileById(ImmersiveJewelry.MOD_ID).getFile()
                .findResource("assets", station.getNamespace(), "diegesis", "stations", station.getPath() + ".json");
        JsonElement json;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
        var result = SceneDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        h.assertTrue(result.error().isEmpty(), station + " parses: " + result.error().map(e -> e.message()).orElse(""));
        return result.result().orElseThrow();
    }

    private static Set<String> takers(SceneDefinition scene) {
        return scene.nodes().stream().filter(NodeDefinition::takesItems).map(NodeDefinition::id).collect(Collectors.toSet());
    }
}
