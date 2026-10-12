package com.otectus.immersivejewelry.smoketest;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.block.StationBlock;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.client.JewelryColors;
import com.otectus.immersivejewelry.client.render.LapidaryWheelRenderer;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;

/** Test-only orientation/state/camera matrix and resource-pack reload check. Screenshots are evidence for review,
 * not an automated claim that every pixel is free of flicker. Enabled with IJ_STATION_VISUALS=1. */
final class WorkstationVisualChecks {
    private static final BlockPos BASE = new BlockPos(0, -60, 0);
    private static int tick;
    private static CompletableFuture<Void> reload;
    private static List<String> previousPacks;
    private static Object previousDisc;
    private static net.minecraftforge.client.ChunkRenderTypeSet previousLayers;

    static void checkTints(Map<String, Object> checks) {
        var defs = JewelryDefinitions.client();
        ItemStack ruby = piece("ruby"), sapphire = piece("sapphire");
        int[] expected = new int[5];
        for (int i = 0; i < 5; i++) expected[i] = JewelryColors.jewelry(ruby, i);
        boolean matches = true;
        try (var outer = JewelryColors.cacheForRender(ruby)) {
            for (int i = 0; i < 5; i++) matches &= expected[i] == JewelryColors.jewelry(ruby, i);
            try (var inner = JewelryColors.cacheForRender(sapphire)) {
                matches &= JewelryColors.jewelry(sapphire, 3) != expected[3];
            }
            matches &= JewelryColors.jewelry(ruby, 3) == expected[3];
        }
        checks.put("tint_scope_matches_and_nests", matches);
        JewelryData.get(sapphire).orElseThrow().apply(ruby); // same stack, changed NBT
        try (var changed = JewelryColors.cacheForRender(ruby)) {
            checks.put("tint_scope_sees_in_place_nbt_change", JewelryColors.jewelry(ruby, 3) != expected[3]);
        }
        String name = piece("ruby").getHoverName().getString();
        checks.put("localized_generated_name", name);
        if (Minecraft.getInstance().options.languageCode.equals("ru_ru")) checks.put("russian_name_grammar", name.equals("Кольцо с рубином"));
    }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws IOException {
        mc.setScreen(null);
        mc.mouseHandler.releaseMouse();
        net.minecraft.client.KeyMapping.releaseAll();
        mc.options.hideGui = true;
        int scene = tick / 100, frame = tick % 100;
        if (scene < 12) {
            if (frame == 0) stage(mc, scene);
            // One second stationary, one approaching, two circling, after a one-second settling period.
            double angle = frame < 60 ? 0 : (frame - 60) / 40D * Math.PI * 2;
            double radius = frame < 40 ? 5 : frame < 60 ? 5 - (frame - 40) / 20D * 2 : 4;
            mc.player.setPos(.5 + Math.sin(angle) * radius, BASE.getY() + 1, .5 + Math.cos(angle) * radius);
            mc.player.xo = mc.player.getX(); mc.player.yo = mc.player.getY(); mc.player.zo = mc.player.getZ();
            mc.player.setYRot(180 - (float) Math.toDegrees(angle)); mc.player.yRotO = mc.player.getYRot();
            mc.player.setXRot(24); mc.player.xRotO = 24;
            if (frame >= 20 && frame % 5 == 0) {
                String name = String.format(java.util.Locale.ROOT, "station_matrix_%02d_%02d", scene, frame);
                shot(mc, name);
            }
            if (frame == 99) {
                boolean occupied = scene % 3 != 0, working = scene % 3 == 2;
                var wheel = (LapidaryWheelBlockEntity) mc.level.getBlockEntity(BASE.offset(-1, 0, 0));
                var bench = (JewelersBenchBlockEntity) mc.level.getBlockEntity(BASE.offset(1, 0, 0));
                checks.put("station_matrix_" + scene + "_synced", wheel != null && bench != null
                        && wheel.hasStone() == occupied && bench.hasWorkpiece() == occupied
                        && wheel.isWorking() == working && bench.isWorking() == working
                        && wheel.getBlockState().getValue(StationBlock.FACING) == Direction.from2DDataValue(scene / 3));
            }
            tick++;
            return false;
        }
        if (tick == 1200) {
            prepareRenderPack(mc);
            previousDisc = mc.getModelManager().getModel(LapidaryWheelRenderer.DISC);
            previousLayers = mc.getModelManager().getModel(LapidaryWheelRenderer.DISC).getRenderTypes(
                    ModBlocks.LAPIDARY_WHEEL.get().defaultBlockState(), RandomSource.create(42), ModelData.EMPTY);
            previousPacks = List.copyOf(mc.getResourcePackRepository().getSelectedIds());
            mc.getResourcePackRepository().reload();
            List<String> selected = new ArrayList<>(previousPacks);
            if (mc.getResourcePackRepository().getAvailableIds().contains("file/ij-render-check")) selected.add("file/ij-render-check");
            checks.put("render_pack_present", selected.size() > previousPacks.size());
            mc.getResourcePackRepository().setSelected(selected);
            reload = mc.reloadResourcePacks();
            tick++;
        } else if (tick == 1201 && reload.isDone() && mc.getOverlay() == null) {
            var disc = mc.getModelManager().getModel(LapidaryWheelRenderer.DISC);
            checks.put("resource_reload_replaces_disc", disc != previousDisc && !reload.isCompletedExceptionally());
            checks.put("resource_pack_disc_layer", disc.getRenderTypes(ModBlocks.LAPIDARY_WHEEL.get().defaultBlockState(),
                    RandomSource.create(42), ModelData.EMPTY).contains(RenderType.cutoutMipped()));
            tick++;
        } else if (tick >= 1202 && tick < 1240) {
            if (tick == 1220) shot(mc, "station_resource_pack_cutout");
            tick++;
        } else if (tick == 1240) {
            mc.getResourcePackRepository().setSelected(previousPacks);
            reload = mc.reloadResourcePacks();
            tick++;
        } else if (tick == 1241 && reload.isDone() && mc.getOverlay() == null) {
            checks.put("resource_pack_restored", !reload.isCompletedExceptionally() && mc.getModelManager().getModel(LapidaryWheelRenderer.DISC)
                    .getRenderTypes(ModBlocks.LAPIDARY_WHEEL.get().defaultBlockState(), RandomSource.create(42), ModelData.EMPTY).asList().equals(previousLayers.asList()));
            checkTints(checks);
            shot(mc, "station_resource_pack_restored");
            tick++;
        } else if (tick == 1242) {
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                level.setDayTime(18000);
                level.setBlockAndUpdate(BASE.offset(0, 0, 2), Blocks.LANTERN.defaultBlockState());
            });
            tick++;
        } else if (tick > 1242 && tick < 1280) tick++;
        else if (tick == 1280) {
            checks.put("station_block_lighting", mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, BASE) > 0);
            shot(mc, "station_night_lighting");
            return true;
        }
        return false;
    }

    private static void stage(Minecraft mc, int scene) {
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            var level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
            var player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.SPECTATOR);
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                for (int y = 0; y < 4; y++) level.setBlockAndUpdate(BASE.offset(x, y, z), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(BASE.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(BASE).inflate(30))
                    .forEach(net.minecraft.world.entity.Entity::discard);
            Direction facing = Direction.from2DDataValue(scene / 3);
            BlockPos wp = BASE.offset(-1, 0, 0), bp = BASE.offset(1, 0, 0);
            level.setBlockAndUpdate(wp, ModBlocks.LAPIDARY_WHEEL.get().defaultBlockState().setValue(StationBlock.FACING, facing));
            level.setBlockAndUpdate(bp, ModBlocks.JEWELERS_BENCH.get().defaultBlockState().setValue(StationBlock.FACING, facing));
            var wheel = (LapidaryWheelBlockEntity) level.getBlockEntity(wp);
            var bench = (JewelersBenchBlockEntity) level.getBlockEntity(bp);
            var defs = JewelryDefinitions.server();
            ItemStack jewelry = ClientSmokeTest.piece(defs, "ring", "ruby", "brilliant", new ResourceLocation("minecraft", "gold"), "polished", .9F);
            ItemStack stone = CutGemItem.create(JewelryData.get(jewelry).orElseThrow().principal());
            if (scene % 3 != 0) { wheel.setStone(stone); bench.setWorkpiece(jewelry); }
            if (scene % 3 == 2) { wheel.lock(player.getUUID()); bench.beginSession(player.getUUID(), List.of(), stone); }
            player.teleportTo(level, .5, BASE.getY() + 1, 5.5, 180, 24);
        });
    }

    private static ItemStack piece(String gem) {
        return ClientSmokeTest.piece(JewelryDefinitions.client(), "ring", gem, "brilliant", new ResourceLocation("minecraft", "gold"), "polished", .9F);
    }

    private static void prepareRenderPack(Minecraft mc) throws IOException {
        Path root = mc.gameDirectory.toPath().resolve("resourcepacks/ij-render-check");
        Path models = root.resolve("assets/immersive_jewelry/models/block");
        Files.createDirectories(models);
        Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":15,\"description\":\"Test-only workstation rendering overrides\"}}");
        for (String name : List.of("lapidary_wheel_disc", "jewelers_bench")) {
            try (var input = mc.getResourceManager().getResource(ImmersiveJewelry.id("models/block/" + name + ".json"))
                    .orElseThrow(() -> new IOException("Missing model " + name)).open()) {
                var model = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (name.equals("lapidary_wheel_disc")) {
                    model.addProperty("render_type", "minecraft:cutout_mipped");
                    model.getAsJsonObject("textures").addProperty("lap", "minecraft:block/oak_leaves");
                } else {
                    model.getAsJsonObject("textures").addProperty("wood", "minecraft:block/quartz_block_side");
                    model.getAsJsonObject("textures").addProperty("top", "minecraft:block/quartz_block_side");
                }
                Files.writeString(models.resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(model));
            }
        }
    }

    private static void shot(Minecraft mc, String name) {
        String label = System.getenv("IJ_VISUAL_LABEL");
        if (label != null) name = label + "_" + name;
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private WorkstationVisualChecks() {}
}
