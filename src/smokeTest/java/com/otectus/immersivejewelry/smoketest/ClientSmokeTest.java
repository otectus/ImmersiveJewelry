package com.otectus.immersivejewelry.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.event.GemCutEvent;
import com.otectus.immersivejewelry.api.event.JewelryCraftedEvent;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.block.StationBlock;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.client.compat.jei.DesignCategory;
import com.otectus.immersivejewelry.client.compat.jei.GemCuttingCategory;
import com.otectus.immersivejewelry.client.compat.jei.ReferenceCategory;
import com.otectus.immersivejewelry.client.screen.BenchScreen;
import com.otectus.immersivejewelry.client.screen.CuttingScreen;
import com.otectus.immersivejewelry.client.screen.HandbookScreen;
import com.otectus.immersivejewelry.client.screen.MinigameScreen;
import com.otectus.immersivejewelry.client.screen.SettingScreen;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.ClientHooks;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.minigame.SessionManager;
import com.otectus.immersivejewelry.minigame.StationInteractions;
import com.otectus.immersivejewelry.minigame.setting.SettingParams;
import com.otectus.immersivejewelry.minigame.setting.SettingSession;
import com.otectus.immersivejewelry.network.packet.SelectCutPacket;
import com.otectus.immersivejewelry.network.packet.SettingStartPacket;
import com.otectus.immersivejewelry.network.packet.StartSettingPacket;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import com.otectus.immersivejewelry.testing.CutBot;
import com.otectus.immersivejewelry.testing.SetBot;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Drives a dev client from the title screen into a fresh flat world and walks the whole craft through the real
 * client and server code: stations in the world, the cut choice, a cut and a setting played through the minigame
 * screens by the reference bots (real input packets, real timing guard), reworking, the handbook, tooltips, an item
 * sheet and the JEI categories. Checks that each server verdict equals the bot's own replay of the same inputs.
 * Saves framebuffer screenshots to {@code run/screenshots/smoketest_*.png}, writes {@code smoketest-result.json}, quits.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static final long START = Util.getMillis();
    private static final ResourceLocation GOLD = new ResourceLocation("minecraft", "gold");
    private static final ResourceLocation SILVER = new ResourceLocation("forge", "silver");
    /** Minigames run in real time; a careful cut takes up to half a minute. */
    private static final int GATE_TIMEOUT_TICKS = 20 * 90;
    private static int titleTicks;
    private static int worldTicks = -1;
    private static boolean worldRequested;
    private static boolean finished;
    private static BlockPos base;
    private static BooleanSupplier gate;
    private static String gateName;
    private static int gateTicks;
    private static CutBot cutBot;
    private static SetBot setBot;
    private static int originalGuiScale;
    private static Boolean originalLargeMarkers;
    private static volatile GemCutEvent lastCut;
    private static volatile JewelryCraftedEvent lastCraft;

    private static BlockPos wheelA() { return base.offset(-2, 0, -2); }
    private static BlockPos wheelB() { return base.offset(-4, 0, -2); }
    private static BlockPos benchA() { return base.offset(2, 0, -2); }
    private static BlockPos benchB() { return base.offset(4, 0, -2); }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (OUTPUT == null || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen && ++titleTicks == 1) sizeWindow(mc);
                if (mc.screen instanceof TitleScreen && titleTicks == 40 && !worldRequested) {
                    worldRequested = true;
                    checks.put("title_ms", Util.getMillis() - START);
                    createWorld(mc);
                }
                return;
            }
            if (gate != null) {
                if (gate.getAsBoolean()) {
                    checks.put(gateName, true);
                    gate = null;
                } else if (++gateTicks > GATE_TIMEOUT_TICKS) {
                    checks.put(gateName, false);
                    gate = null;
                } else {
                    return;
                }
            }
            worldTicks++;
            if (worldTicks < 40) return;
            step(mc, worldTicks);
        } catch (Exception e) {
            ImmersiveJewelry.LOGGER.error("SMOKETEST failed at world tick {}", worldTicks, e);
            checks.put("agent_exception", e.toString());
            finish(mc, false, "agent exception");
        }
    }

    // The integrated server posts these on the shared Forge bus.
    @SubscribeEvent
    public static void onCut(GemCutEvent event) {
        lastCut = event;
    }

    @SubscribeEvent
    public static void onCraft(JewelryCraftedEvent event) {
        lastCraft = event;
    }

    private static void step(Minecraft mc, int t) {
        switch (t) {
            case 60 -> {
                checks.put("world_ms", Util.getMillis() - START);
                clientChecks(mc);
                stageWorld(mc);
            }
            case 72 -> camera(mc, 0.0, 4.5, 180F, 30F);
            case 110 -> {
                mc.options.hideGui = true;
                shot(mc, "smoketest_world");
            }
            case 112 -> camera(mc, -3.0, 0.2, 180F, 38F);
            case 135 -> shot(mc, "smoketest_wheel");
            case 137 -> camera(mc, 3.0, 0.2, 180F, 38F);
            case 160 -> shot(mc, "smoketest_bench_world");

            // Cutting: choose a cut, then the bot plays the cutting screen.
            case 162 -> {
                mc.options.hideGui = false;
                server(mc, (server, player) -> {
                    player.setGameMode(GameType.CREATIVE);
                    player.teleportTo(server.overworld(), base.getX() + 0.5, base.getY(), base.getZ() + 1.5, 180F, 30F);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    StationInteractions.useWheel(player, wheel(server, wheelA()), InteractionHand.MAIN_HAND);
                });
            }
            case 174 -> mc.getToasts().clear();
            case 176 -> shot(mc, "smoketest_cut_select");
            case 178 -> {
                cutBot = null;
                MinigameScreen.scriptedInput = index -> {
                    if (cutBot == null && mc.screen instanceof CuttingScreen s) cutBot = new CutBot(s.params(), false);
                    return cutBot == null ? new int[]{0, 0, 0} : cutBot.next();
                };
                lastCut = null;
                server(mc, (server, player) -> SessionManager.selectCut(player,
                        new SelectCutPacket(wheelA(), ImmersiveJewelry.id("brilliant"), false, false)));
            }
            case 200 -> checks.put("cutting_screen", mc.screen instanceof CuttingScreen);
            case 300 -> {
                checks.put("wheel_spins_client", mc.level.getBlockEntity(wheelA()) instanceof LapidaryWheelBlockEntity w && w.speed > 0.05F);
                shot(mc, "smoketest_cutting");
            }
            case 302 -> waitFor("cut_verdict", () -> mc.screen instanceof CuttingScreen s && s.finished() && lastCut != null);
            case 312 -> {
                shot(mc, "smoketest_cut_result");
                GemCutEvent cut = lastCut;
                GemData after = cut == null ? null : cut.getAfter();
                checks.put("cut_quality", after == null ? -1F : after.cutQuality());
                checks.put("cut_quality_ok", after != null && after.cutQuality() >= 0.7F);
                if (after != null && cutBot != null) {
                    float expected = Math.min(cutBot.sim().quality(), cut.getBefore().qualityCap());
                    checks.put("cut_lockstep", Math.abs(expected - after.cutQuality()) < 1e-4F);
                }
            }

            // Setting: choose a design and stone at the bench, then the bot plays the setting screen.
            case 314 -> {
                mc.setScreen(null);
                MinigameScreen.scriptedInput = null;
                server(mc, (server, player) -> {
                    checks.put("cut_stone_on_wheel", CutGemItem.data(wheel(server, wheelA()).stone()).isPresent());
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FINE_PLIERS.get()));
                    StationInteractions.useBench(player, bench(server, benchA()), InteractionHand.MAIN_HAND);
                });
            }
            case 326 -> {
                checks.put("bench_screen", mc.screen instanceof BenchScreen);
                if (mc.screen instanceof BenchScreen s) {
                    clickPanel(s, 456, 288, 166, 56); // first stone, through the fitted pointer transform
                    checks.put("bench_selection", readyButton(s));
                }
            }
            case 327 -> {
                mc.getToasts().clear();
                originalGuiScale = mc.options.guiScale().get();
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
            }
            case 329 -> {
                if (mc.screen instanceof BenchScreen s) {
                    clickPanel(s, 456, 288, 166, 56);
                    boolean cleared = !readyButton(s);
                    clickPanel(s, 456, 288, 166, 56);
                    checks.put("scaled_bench_selection", cleared && readyButton(s));
                    checks.put("scaled_bench_fits", s.children().stream().allMatch(child -> !(child instanceof net.minecraft.client.gui.components.AbstractWidget widget)
                            || widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= 456 && widget.getY() + widget.getHeight() <= 288));
                }
            }
            case 332 -> shot(mc, "smoketest_bench_scale3");
            case 333 -> {
                mc.options.guiScale().set(originalGuiScale);
                mc.resizeDisplay();
            }
            case 335 -> shot(mc, "smoketest_bench_assemble");
            case 336 -> {
                setBot = null;
                MinigameScreen.scriptedInput = index -> {
                    if (setBot == null && mc.screen instanceof SettingScreen s) setBot = new SetBot(s.params(), false);
                    return new int[]{0, 0, setBot != null && setBot.next() ? 1 : 0};
                };
                lastCraft = null;
                server(mc, (server, player) -> SessionManager.startSetting(player, new StartSettingPacket(benchA(),
                        ImmersiveJewelry.id("designs/solitaire_ring"), new int[]{rubySlot(player)}, ImmersiveJewelry.id("polished"), false, false)));
            }
            case 358 -> checks.put("setting_screen", mc.screen instanceof SettingScreen);
            case 400 -> shot(mc, "smoketest_setting");
            case 402 -> waitFor("setting_verdict", () -> mc.screen instanceof SettingScreen s && s.finished() && lastCraft != null);
            case 412 -> {
                shot(mc, "smoketest_setting_result");
                JewelryCraftedEvent craft = lastCraft;
                JewelryData data = craft == null ? null : craft.getData();
                checks.put("setting_quality", data == null ? -1F : data.settingQuality());
                checks.put("setting_quality_ok", data != null && data.settingQuality() >= 0.7F);
                if (data != null && setBot != null) checks.put("setting_lockstep", Math.abs(setBot.sim().quality() - data.settingQuality()) < 1e-4F);
            }
            case 414 -> {
                mc.setScreen(null);
                MinigameScreen.scriptedInput = null;
                server(mc, (server, player) -> {
                    checks.put("ring_on_bench", JewelryData.get(bench(server, benchA()).workpiece()).isPresent());
                    StationInteractions.useBench(player, bench(server, benchB()), InteractionHand.MAIN_HAND);
                });
            }
            case 426 -> mc.getToasts().clear();
            case 428 -> shot(mc, "smoketest_bench_rework");
            case 430 -> {
                mc.setScreen(null);
                camera(mc, 2.0, 0.6, 180F, 58F);
            }
            case 452 -> {
                mc.options.hideGui = true;
                shot(mc, "smoketest_bench_finished");
            }

            // Jade overlays on the stations (the crosshair on each, in creative).
            case 454 -> {
                mc.options.hideGui = false;
                lookAt(mc, wheelA());
            }
            case 470 -> {
                checks.put("jade_loaded", net.minecraftforge.fml.ModList.get().isLoaded("jade"));
                shot(mc, "smoketest_jade_wheel");
            }
            case 472 -> lookAt(mc, benchB());
            case 488 -> shot(mc, "smoketest_jade_bench");

            // Reference screens.
            case 494 -> {
                mc.getToasts().clear();
                HandbookScreen.open();
            }
            case 506 -> shot(mc, "smoketest_handbook");
            case 508 -> {
                if (mc.screen instanceof HandbookScreen s) for (int i = 0; i < 3; i++) s.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
            }
            case 518 -> shot(mc, "smoketest_handbook_cutting");
            case 520 -> mc.setScreen(new TooltipSheet(0));
            case 530 -> shot(mc, "smoketest_tooltips");
            case 532 -> mc.setScreen(new TooltipSheet(1));
            case 540 -> shot(mc, "smoketest_tooltips_2");
            case 542 -> {
                ClientHooks.shiftDown = () -> true;
                mc.setScreen(new TooltipSheet(2));
            }
            case 546 -> {
                shot(mc, "smoketest_tooltips_shift");
                ClientHooks.shiftDown = Screen::hasShiftDown;
            }
            case 548 -> mc.setScreen(new ItemSheet());
            case 560 -> shot(mc, "smoketest_items");
            case 562 -> mc.setScreen(new InventoryScreen(mc.player));
            case 574 -> shot(mc, "smoketest_inventory");
            case 576 -> {
                mc.setScreen(null);
                checks.put("jei_runtime", SmokeJeiPlugin.runtime != null);
                jei(GemCuttingCategory.TYPE);
            }
            case 592 -> shot(mc, "smoketest_jei_cutting");
            case 594 -> jei(DesignCategory.TYPE);
            case 610 -> shot(mc, "smoketest_jei_designs");
            case 612 -> jei(ReferenceCategory.FINISHING);
            case 628 -> shot(mc, "smoketest_jei_finishing");
            case 630 -> jei(ReferenceCategory.REWORKING);
            case 646 -> shot(mc, "smoketest_jei_reworking");
            case 648 -> {
                mc.setScreen(null);
                if (com.otectus.immersivejewelry.equipment.JewelryEquipment.builtinActive()) {
                    com.otectus.immersivejewelry.network.ModNetwork.CHANNEL.sendToServer(new com.otectus.immersivejewelry.network.packet.OpenJewelrySlotsPacket());
                }
            }
            case 658 -> {
                if (com.otectus.immersivejewelry.equipment.JewelryEquipment.builtinActive()) {
                    checks.put("equipment_screen", mc.screen instanceof com.otectus.immersivejewelry.client.screen.JewelrySlotsScreen);
                    shot(mc, "smoketest_jewelry_slots");
                }
            }
            case 660 -> {
                checks.put("art_models", ArtSheet.checkModels(mc));
                mc.setScreen(new ArtSheet());
            }
            case 676 -> shot(mc, "smoketest_art");
            case 680 -> {
                mc.setScreen(null);
                mc.options.hideGui = true;
                camera(mc, 0.0, 3.0, 180F, 35F);
                faceStations(mc, Direction.NORTH);
            }
            case 704 -> shot(mc, "smoketest_stations_north");
            case 708 -> faceStations(mc, Direction.EAST);
            case 732 -> shot(mc, "smoketest_stations_east");
            case 736 -> faceStations(mc, Direction.SOUTH);
            case 760 -> shot(mc, "smoketest_stations_south");
            case 764 -> faceStations(mc, Direction.WEST);
            case 788 -> shot(mc, "smoketest_stations_west");
            case 792 -> {
                mc.options.hideGui = false;
                originalLargeMarkers = ClientConfig.get(ClientConfig.LARGE_MINIGAME_MARKERS);
                ClientConfig.LARGE_MINIGAME_MARKERS.set(true);
                mc.setScreen(manyStonesPreview());
            }
            case 808 -> {
                checks.put("many_stones_screen", mc.screen instanceof SettingScreen s && s.params().stones() == 8 && s.params().points() == 96);
                shot(mc, "smoketest_setting_many_stones");
            }
            case 812 -> mc.setScreen(new ProfessionSheet(mc));
            case 828 -> shot(mc, "smoketest_professions");
            case 830 -> finish(mc, allPassed(), "complete");
            default -> {
            }
        }
    }

    /** Frozen presentation fixture at the supported limits; never starts or sends a server session. */
    private static SettingScreen manyStonesPreview() {
        JewelryDefinitions defs = JewelryDefinitions.client();
        List<GemData> stones = new ArrayList<>();
        String[] gems = {"ruby", "sapphire", "emerald", "diamond"};
        for (int i = 0; i < 8; i++) {
            stones.add(JewelryFactory.sampleCut(defs.gem(ImmersiveJewelry.id(gems[i % gems.length])),
                    defs.cut(ImmersiveJewelry.id("round")), 0.9F));
        }
        SettingParams params = SettingParams.create(new SettingParams.Inputs(SettingStyle.PRONG, 8, 12, 1F, 1F, 1F,
                0.3F, 1F, false, false, false, 6), 42L);
        ItemStack blank = JewelryFactory.blank(ModItems.RING_BLANK.get(), JewelryFactory.metal(defs, GOLD), 0.9F);
        return new SettingScreen(new SettingStartPacket(-1, base, SettingSession.Mode.SET, params, stones, blank, 0)) {
            @Override protected void advanceClock() {}
            @Override protected boolean counting() { return false; }
            @Override public void removed() {}
        };
    }

    private static void clickPanel(Screen screen, int w, int h, int x, int y) {
        float scale = Math.min(1F, Math.min((screen.width - 12F) / w, (screen.height - 12F) / h));
        screen.mouseClicked((screen.width - w * scale) / 2F + x * scale,
                (screen.height - h * scale) / 2F + y * scale, 0);
    }

    private static boolean readyButton(Screen screen) {
        return screen.children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.Button button
                && button.getMessage().getString().equals(Component.translatable("screen.immersive_jewelry.begin_setting").getString()) && button.active);
    }

    private static void waitFor(String name, BooleanSupplier condition) {
        gateName = name;
        gateTicks = 0;
        gate = condition;
    }

    private static void faceStations(Minecraft mc, Direction facing) {
        server(mc, (server, player) -> {
            for (BlockPos pos : List.of(wheelA(), wheelB(), benchA(), benchB())) {
                server.overworld().setBlockAndUpdate(pos, server.overworld().getBlockState(pos).setValue(StationBlock.FACING, facing));
            }
        });
    }

    private static void jei(mezz.jei.api.recipe.RecipeType<?> type) {
        if (SmokeJeiPlugin.runtime != null) SmokeJeiPlugin.runtime.getRecipesGui().showTypes(List.of(type));
    }

    // ------------------------------------------------------------------------------------------------ staging

    /**
     * A landscape 1280x720 window. A tiling Wayland compositor ignores the requested size, so on Hyprland this
     * client's own window (matched by process id) is floated and sized; nothing else on the desktop is touched.
     */
    private static void sizeWindow(Minecraft mc) {
        mc.getWindow().setWindowed(1280, 720);
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") == null) return;
        String target = "window = \"pid:" + ProcessHandle.current().pid() + "\"";
        Thread thread = new Thread(() -> {
            // Hyprland 0.55+ takes Lua dispatchers.
            for (String dispatch : new String[]{"hl.dsp.window.float({ action = \"enable\", " + target + " })",
                    "hl.dsp.window.resize({ x = 1280, y = 720, " + target + " })", "hl.dsp.window.move({ x = 40, y = 40, " + target + " })"}) {
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
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20260925L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void clientChecks(Minecraft mc) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        checks.put("client_gems", defs.gems().size());
        checks.put("client_gems_ok", defs.gems().size() >= 9);
        checks.put("client_cuts_ok", defs.cuts().size() == 4);
        checks.put("client_traits_ok", defs.traits().size() >= 10);
        checks.put("client_designs_ok", mc.level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get()).size() >= 10);
        checks.put("server_defs_match_client", JewelryDefinitions.server().gems().size() == defs.gems().size());
    }

    private static void stageWorld(Minecraft mc) {
        server(mc, (server, player) -> {
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            base = player.blockPosition();
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), (x + z) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                        : Blocks.DEEPSLATE_TILES.defaultBlockState());
                for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            JewelryDefinitions defs = JewelryDefinitions.server();
            BlockState wheelState = ModBlocks.LAPIDARY_WHEEL.get().defaultBlockState().setValue(StationBlock.FACING, Direction.SOUTH);
            BlockState benchState = ModBlocks.JEWELERS_BENCH.get().defaultBlockState().setValue(StationBlock.FACING, Direction.SOUTH);
            level.setBlockAndUpdate(wheelA(), wheelState);
            level.setBlockAndUpdate(wheelB(), wheelState);
            level.setBlockAndUpdate(benchA(), benchState);
            level.setBlockAndUpdate(benchB(), benchState);

            GemDefinition sapphire = defs.gem(ImmersiveJewelry.id("sapphire"));
            wheel(server, wheelA()).swapLap(new ItemStack(ModItems.DIAMOND_LAP.get()));
            wheel(server, wheelA()).setStone(RoughGemItem.create(GemData.rough(sapphire.id(), 1.2F, 0.82F, 0.08F)));
            wheel(server, wheelB()).swapLap(new ItemStack(ModItems.EMERY_LAP.get()));
            wheel(server, wheelB()).setStone(RoughGemItem.create(GemData.rough(ImmersiveJewelry.id("ruby"), 1.0F, 0.6F, 0.2F)));
            bench(server, benchA()).setWorkpiece(JewelryFactory.blank(ModItems.RING_BLANK.get(), JewelryFactory.metal(defs, GOLD), 0.86F));
            ItemStack worn = piece(defs, "amulet", "emerald", "emerald", SILVER, "antique", 0.7F);
            JewelryData.get(worn).ifPresent(d -> d.withIntegrity(0.55F).apply(worn));
            bench(server, benchB()).setWorkpiece(worn);

            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(ModItems.JEWELERS_PLIERS.get()));
            player.getInventory().add(CutGemItem.create(JewelryFactory.sampleCut(defs.gem(ImmersiveJewelry.id("ruby")), defs.cut(ImmersiveJewelry.id("round")), 0.88F)));
            player.getInventory().add(CutGemItem.create(JewelryFactory.sampleCut(defs.gem(ImmersiveJewelry.id("diamond")), defs.cut(ImmersiveJewelry.id("brilliant")), 0.95F)));
            player.getInventory().add(CutGemItem.create(JewelryFactory.sampleCut(sapphire, defs.cut(ImmersiveJewelry.id("emerald")), 0.7F)));
            player.getInventory().add(new ItemStack(ModItems.POLISHING_COMPOUND.get(), 8));
            player.getInventory().add(new ItemStack(Items.GUNPOWDER, 4));
            player.getInventory().add(new ItemStack(Items.DIAMOND, 3));
            player.getInventory().add(piece(defs, "ring", "ruby", "brilliant", GOLD, "polished", 0.93F));
            player.getInventory().add(piece(defs, "circlet", "amethyst", "cabochon", GOLD, "antique", 0.8F));
            player.getInventory().add(piece(defs, "bracelet", "topaz", "emerald", new ResourceLocation("minecraft", "copper"), "burnished", 0.62F));
            player.getInventory().add(new ItemStack(ModItems.JEWELERS_HANDBOOK.get()));
        });
    }

    static ItemStack piece(JewelryDefinitions defs, String form, String gem, String cut, ResourceLocation metal, String finish, float q) {
        GemData stone = JewelryFactory.sampleCut(defs.gem(ImmersiveJewelry.id(gem)), defs.cut(ImmersiveJewelry.id(cut)), q);
        MetalRef ref = JewelryFactory.metal(defs, metal);
        return JewelryFactory.jewelry(ImmersiveJewelry.id(form), ref, List.of(stone), null, ImmersiveJewelry.id(finish), q, q, "Otectus");
    }

    private static int rubySlot(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (CutGemItem.data(player.getInventory().items.get(i)).map(d -> d.gem().equals(ImmersiveJewelry.id("ruby"))).orElse(false)) return i;
        }
        return -1;
    }

    private static LapidaryWheelBlockEntity wheel(IntegratedServer server, BlockPos pos) {
        return (LapidaryWheelBlockEntity) server.overworld().getBlockEntity(pos);
    }

    private static JewelersBenchBlockEntity bench(IntegratedServer server, BlockPos pos) {
        return (JewelersBenchBlockEntity) server.overworld().getBlockEntity(pos);
    }

    private interface ServerTask {
        void run(IntegratedServer server, ServerPlayer player);
    }

    private static void server(Minecraft mc, ServerTask task) {
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

    /** Stands two blocks south of a station, in creative, with the crosshair on its middle. */
    private static void lookAt(Minecraft mc, BlockPos target) {
        server(mc, (server, player) -> {
            player.setGameMode(GameType.CREATIVE);
            double eye = 1.62 - 0.5;
            float pitch = (float) Math.toDegrees(Math.atan2(eye, 2.0));
            player.teleportTo(server.overworld(), target.getX() + 0.5, target.getY(), target.getZ() + 2.5, 180F, pitch);
        });
    }

    private static void camera(Minecraft mc, double dx, double dz, float yaw, float pitch) {
        server(mc, (server, player) -> {
            if (base == null) return;
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(server.overworld(), base.getX() + 0.5 + dx, base.getY() + 1.1, base.getZ() + 0.5 + dz, yaw, pitch);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> log("screenshot " + name));
    }

    private static boolean allPassed() {
        for (Object value : checks.values()) {
            if (value instanceof Boolean b && !b) return false;
        }
        return !checks.containsKey("agent_exception") && !checks.containsKey("server_exception");
    }

    private static void finish(Minecraft mc, boolean pass, String reason) {
        finished = true;
        MinigameScreen.scriptedInput = null;
        if (originalLargeMarkers != null) ClientConfig.LARGE_MINIGAME_MARKERS.set(originalLargeMarkers);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass);
        result.put("reason", reason);
        result.put("checks", checks);
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "smoketest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ImmersiveJewelry.LOGGER.error("SMOKETEST could not write result", e);
        }
        log("finished: pass=" + pass + " (" + reason + ")");
        mc.stop();
    }

    private static void log(String message) {
        ImmersiveJewelry.LOGGER.info("SMOKETEST {}", message);
    }

    /** Tooltips of representative pieces, two to a page (a page with Shift held shows the details). */
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
            ItemStack[] stacks = switch (page) {
                case 0 -> new ItemStack[]{piece(defs, "ring", "ruby", "brilliant", GOLD, "polished", 0.93F),
                        piece(defs, "amulet", "sapphire", "round", SILVER, "burnished", 0.8F)};
                case 1 -> new ItemStack[]{piece(defs, "bracelet", "onyx", "cabochon", new ResourceLocation("minecraft", "iron"), "antique", 0.55F),
                        piece(defs, "circlet", "diamond", "brilliant", GOLD, "polished", 0.97F)};
                default -> new ItemStack[]{piece(defs, "ring", "ruby", "brilliant", GOLD, "polished", 0.93F),
                        CutGemItem.create(JewelryFactory.sampleCut(defs.gem(ImmersiveJewelry.id("diamond")), defs.cut(ImmersiveJewelry.id("brilliant")), 0.95F))};
            };
            // Laid out by measured size, left to right and then down, so none overlaps another.
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
                g.renderComponentTooltip(font, lines, x - 12, y + 12); // tooltips draw at (+12, -12) from the point
                x += w + 10;
                rowHeight = Math.max(rowHeight, h);
            }
        }
    }

    /** Every form in several metals, gems, cuts and finishes, plus the stones, blanks, tools and stations, at 2x. */
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
                    {"minecraft:iron", "amethyst", "cabochon", "burnished"}, {"minecraft:gold", "onyx", "cabochon", "antique"},
                    {"forge:rose_gold", "topaz", "emerald", "polished"}, {"forge:silver", "lapis", "cabochon", "polished"}};
            for (String form : new String[]{"ring", "amulet", "bracelet", "circlet"}) {
                for (String[] c : combos) stacks.add(piece(defs, form, c[1], c[2], new ResourceLocation(c[0]), c[3], 0.85F));
            }
            for (GemDefinition gem : defs.gems()) {
                if (gem.auto()) continue;
                stacks.add(RoughGemItem.create(GemData.rough(gem.id(), 1F, 0.6F, 0.2F)));
                for (CutDefinition cut : defs.cutsFor(gem)) stacks.add(CutGemItem.create(JewelryFactory.sampleCut(gem, cut, 0.85F)));
            }
            for (Item blank : new Item[]{ModItems.RING_BLANK.get(), ModItems.AMULET_FRAME.get(), ModItems.BRACELET_BLANK.get(), ModItems.CIRCLET_FRAME.get()}) {
                stacks.add(JewelryFactory.blank(blank, JewelryFactory.metal(defs, GOLD), 0.8F));
                stacks.add(JewelryFactory.blank(blank, JewelryFactory.metal(defs, SILVER), 0.8F));
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
