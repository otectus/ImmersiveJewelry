package com.otectus.immersivejewelry.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.event.GemCutEvent;
import com.otectus.immersivejewelry.api.event.JewelryCraftedEvent;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.block.StationBlock;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.client.screen.BenchScreen;
import com.otectus.immersivejewelry.client.screen.CuttingScreen;
import com.otectus.immersivejewelry.client.screen.MinigameScreen;
import com.otectus.immersivejewelry.client.screen.SettingScreen;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.minigame.SessionManager;
import com.otectus.immersivejewelry.minigame.StationInteractions;
import com.otectus.immersivejewelry.network.packet.SelectCutPacket;
import com.otectus.immersivejewelry.network.packet.StartSettingPacket;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.testing.CutBot;
import com.otectus.immersivejewelry.testing.SetBot;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Opt-in CurseForge screenshot capture, confined to the test agent and enabled only by
 * {@code -Dimmersive_jewelry.screenshots.output=<dir>}. It builds a jeweler's shop and plays a real cut and a real
 * setting through the station screens with the reference bots, saving framebuffer captures without HUD, chat or toasts.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class StoreScreenshots {
    private static final String OUTPUT = System.getProperty("immersive_jewelry.screenshots.output");
    private static final int WIDTH = 1920, HEIGHT = 1080;
    private static final ResourceLocation GOLD = new ResourceLocation("minecraft", "gold");
    private static final ResourceLocation SILVER = new ResourceLocation("forge", "silver");
    private static final Map<String, Object> EVIDENCE = new LinkedHashMap<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static CompletableFuture<Void> pending;
    private static int step, action, baseY;
    private static long entered, started;
    private static boolean finished, renamed;
    private static volatile float lockYaw = Float.NaN, lockPitch;
    private static double cursorX = 0.3, cursorY = 0.3;
    private static BlockPos wheel, wheel2, bench, bench2;
    private static CutBot cutBot;
    private static SetBot setBot;
    private static volatile GemCutEvent lastCut;
    private static volatile JewelryCraftedEvent lastCraft;

    static boolean enabled() { return OUTPUT != null; }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (enabled()) event.setCanceled(true);
    }

    /** The player profile is built from the session user when the world is created, so rename it on the title screen. */
    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!enabled() || renamed || !(event.getScreen() instanceof TitleScreen)) return;
        renamed = true;
        try {
            var name = net.minecraft.client.User.class.getDeclaredField("name");
            name.setAccessible(true);
            name.set(Minecraft.getInstance().getUser(), "Otectus");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not rename the showcase player", e);
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (enabled() && event.phase == TickEvent.Phase.START) lockView(Minecraft.getInstance());
    }

    // The integrated server posts these on the shared Forge bus.
    @SubscribeEvent
    public static void onCut(GemCutEvent event) {
        if (enabled()) lastCut = event;
    }

    @SubscribeEvent
    public static void onCraft(JewelryCraftedEvent event) {
        if (enabled()) lastCraft = event;
    }

    static void tick(Minecraft mc) throws Exception {
        if (finished) return;
        if (step == 0 && mc.screen != null) return;
        if (started == 0) started = Util.getMillis();
        if (Util.getMillis() - started > 300_000) throw new IllegalStateException("Store screenshots stalled at step " + step);
        mc.getToasts().clear();
        lockView(mc);
        if (mc.screen != null && !(mc.screen instanceof InventoryScreen)) placeCursor(mc);
        if (pending != null && !pending.isDone()) return;
        if (pending != null) { pending.join(); pending = null; }
        long age = Util.getMillis() - entered;
        switch (step) {
            case 0 -> {
                Files.createDirectories(Path.of(OUTPUT));
                baseY = mc.player.blockPosition().getY();
                wheel = new BlockPos(-1, baseY, -4);
                wheel2 = new BlockPos(-3, baseY, -4);
                bench = new BlockPos(1, baseY, -4);
                bench2 = new BlockPos(3, baseY, -4);
                mc.getWindow().setWindowed(WIDTH, HEIGHT);
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                mc.options.fov().set(70);
                mc.options.bobView().set(false);
                mc.options.renderDistance().set(12);
                mc.options.pauseOnLostFocus = false;
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0D);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
                mc.getTutorial().setStep(TutorialSteps.NONE);
                server(mc, StoreScreenshots::buildShop);
                advance(1);
            }
            case 1 -> {
                if (mc.getWindow().getWidth() != WIDTH && age > 1500 && action == 0) { mc.getWindow().toggleFullScreen(); action++; }
                if (age > 7000) {
                    EVIDENCE.put("framebuffer", mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height);
                    shot(mc, "shop");
                    server(mc, (level, player) -> look(player, -1.6, 1.55, -2.2, -1.9, 0.8, -4.2));
                    advance(2);
                }
            }
            case 2 -> {
                if (action == 0 && age > 3000) { shot(mc, "wheel"); action++; }
                if (action == 1 && age > 3300) {
                    server(mc, (level, player) -> {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        StationInteractions.useWheel(player, wheelAt(level, wheel), InteractionHand.MAIN_HAND);
                    });
                    action++;
                }
                if (action == 2 && mc.screen != null && age > 5000) { shot(mc, "cut_select"); action++; }
                if (action == 3 && age > 5300) {
                    cutBot = null;
                    lastCut = null;
                    MinigameScreen.scriptedInput = index -> {
                        if (cutBot == null && mc.screen instanceof CuttingScreen s) cutBot = new CutBot(s.params(), false);
                        return cutBot == null ? new int[]{0, 0, 0} : cutBot.next();
                    };
                    server(mc, (level, player) -> SessionManager.selectCut(player,
                            new SelectCutPacket(wheel, ImmersiveJewelry.id("brilliant"), false, false)));
                    advance(3);
                }
            }
            case 3 -> {
                if (!(mc.screen instanceof CuttingScreen screen)) break;
                if (action == 0 && age > 7000) { shot(mc, "cutting"); action++; }
                if (action == 1 && age > 12000) { shot(mc, "cutting_late"); action++; }
                if (action >= 1 && action < 10 && screen.finished() && lastCut != null) {
                    GemData after = lastCut.getAfter();
                    EVIDENCE.put("cut_quality", after == null ? -1F : after.cutQuality());
                    action = 10;
                    entered = Util.getMillis();
                    break;
                }
                if (action == 10 && age > 1500) {
                    shot(mc, "cut_result");
                    mc.setScreen(null);
                    MinigameScreen.scriptedInput = null;
                    server(mc, (level, player) -> {
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FINE_PLIERS.get()));
                        look(player, 1.5, 1.55, -2.2, 1.5, 0.8, -4.2);
                        StationInteractions.useBench(player, benchAt(level, bench), InteractionHand.MAIN_HAND);
                    });
                    advance(4);
                }
            }
            case 4 -> {
                if (!(mc.screen instanceof BenchScreen screen)) break;
                if (action == 0 && age > 1500) { clickPanel(screen, 456, 288, 166, 56); action++; }
                if (action == 1 && age > 2500) { shot(mc, "bench_assemble"); action++; }
                if (action == 2 && age > 2800) {
                    setBot = null;
                    lastCraft = null;
                    MinigameScreen.scriptedInput = index -> {
                        if (setBot == null && mc.screen instanceof SettingScreen s) setBot = new SetBot(s.params(), false);
                        return new int[]{0, 0, setBot != null && setBot.next() ? 1 : 0};
                    };
                    server(mc, (level, player) -> SessionManager.startSetting(player, new StartSettingPacket(bench,
                            ImmersiveJewelry.id("designs/solitaire_ring"), new int[]{gemSlot(player, "ruby")}, ImmersiveJewelry.id("polished"), false, false)));
                    advance(5);
                }
            }
            case 5 -> {
                if (!(mc.screen instanceof SettingScreen screen)) break;
                if (action == 0 && age > 5500) { shot(mc, "setting"); action++; }
                if (action == 1 && age > 8000) { shot(mc, "setting_late"); action++; }
                if (action >= 1 && action < 10 && screen.finished() && lastCraft != null) {
                    JewelryData data = lastCraft.getData();
                    EVIDENCE.put("setting_quality", data == null ? -1F : data.settingQuality());
                    action = 10;
                    entered = Util.getMillis();
                    break;
                }
                if (action == 10 && age > 1500) {
                    shot(mc, "setting_result");
                    mc.setScreen(null);
                    MinigameScreen.scriptedInput = null;
                    server(mc, (level, player) -> {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        StationInteractions.takeWorkpiece(player, benchAt(level, bench));
                        player.inventoryMenu.broadcastChanges();
                    });
                    advance(6);
                }
            }
            case 6 -> {
                if (action == 0 && age > 800) {
                    int slot = -1;
                    for (int i = 0; i < 36; i++) if (JewelryData.get(mc.player.getInventory().getItem(i)).isPresent()
                            && mc.player.getInventory().getItem(i).getHoverName().getString().contains("Ruby")) slot = i;
                    if (slot < 0) throw new IllegalStateException("The crafted ring did not reach the inventory");
                    EVIDENCE.put("ring", mc.player.getInventory().getItem(slot).getHoverName().getString());
                    hideJeiOverlay();
                    mc.options.guiScale().set(4);
                    mc.resizeDisplay();
                    final int s = slot;
                    mc.setScreen(new InventoryScreen(mc.player) {
                        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
                            int x = s < 9 ? leftPos + 8 + s * 18 + 8 : leftPos + 8 + (s % 9) * 18 + 8;
                            int y = s < 9 ? topPos + 142 + 8 : topPos + 84 + (s / 9 - 1) * 18 + 8;
                            super.render(g, x, y, partial);
                        }
                    });
                    action++;
                }
                if (action == 1 && age > 2400) {
                    shot(mc, "tooltip");
                    mc.setScreen(null);
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                    server(mc, (level, player) -> look(player, 2.9, 1.65, -1.4, -0.6, 0.7, -4.3));
                    advance(7);
                }
            }
            case 7 -> {
                if (age > 3500) {
                    shot(mc, "shop_outro");
                    EVIDENCE.put("shots", SHOTS);
                    EVIDENCE.put("pass", true);
                    Files.writeString(Path.of(OUTPUT, "result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(EVIDENCE));
                    finished = true;
                    mc.stop();
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static LapidaryWheelBlockEntity wheelAt(ServerLevel level, BlockPos pos) {
        return (LapidaryWheelBlockEntity) level.getBlockEntity(pos);
    }

    private static JewelersBenchBlockEntity benchAt(ServerLevel level, BlockPos pos) {
        return (JewelersBenchBlockEntity) level.getBlockEntity(pos);
    }

    private static int gemSlot(ServerPlayer player, String gem) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (CutGemItem.data(player.getInventory().items.get(i)).map(d -> d.gem().equals(ImmersiveJewelry.id(gem))).orElse(false)) return i;
        }
        return -1;
    }

    /** Clicks a point of a fitted panel, through the same pointer transform the screen uses. */
    private static void clickPanel(Screen screen, int w, int h, int x, int y) {
        float scale = Math.min(1F, Math.min((screen.width - 12F) / w, (screen.height - 12F) / h));
        screen.mouseClicked((screen.width - w * scale) / 2F + x * scale, (screen.height - h * scale) / 2F + y * scale, 0);
    }

    private static void shot(Minecraft mc, String name) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            String file = String.format("%02d_%s.png", SHOTS.size() + 1, name);
            image.writeToFile(Path.of(OUTPUT, file));
            SHOTS.add(file);
        } catch (Exception e) {
            throw new IllegalStateException("Screenshot " + name + " failed", e);
        }
    }

    /** JEI's item list is noise in a tooltip shot; its toggle is internal, so reach it reflectively and carry on without it. */
    private static void hideJeiOverlay() {
        try {
            Object state = Class.forName("mezz.jei.common.Internal").getMethod("getClientToggleState").invoke(null);
            if ((boolean) state.getClass().getMethod("isOverlayEnabled").invoke(state))
                state.getClass().getMethod("toggleOverlayEnabled").invoke(state);
            EVIDENCE.put("jei_overlay_hidden", true);
        } catch (Exception | LinkageError e) {
            EVIDENCE.put("jei_overlay_hidden", e.toString());
        }
    }

    /** Pins the view to the last scripted camera so pointer motion reaching the window cannot turn the shot. */
    private static void lockView(Minecraft mc) {
        var player = mc.player;
        if (player == null || Float.isNaN(lockYaw)) return;
        player.setYRot(lockYaw);
        player.setXRot(lockPitch);
        player.yRotO = lockYaw;
        player.xRotO = lockPitch;
        player.setYHeadRot(lockYaw);
        player.yHeadRotO = lockYaw;
        player.yBodyRot = lockYaw;
        player.yBodyRotO = lockYaw;
    }

    /** Keeps the pointer in a corner so no stray hover state shows; gamescope ignores cursor warps. */
    private static void placeCursor(Minecraft mc) {
        double scale = mc.getWindow().getGuiScale();
        try {
            setField(mc.mouseHandler, "xpos", cursorX * scale);
            setField(mc.mouseHandler, "ypos", cursorY * scale);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not place the cursor", e);
        }
    }

    private static void server(Minecraft mc, BiConsumer<ServerLevel, ServerPlayer> work) {
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("Store screenshots need an integrated server");
        pending = CompletableFuture.runAsync(() -> work.accept(server.overworld(), server.getPlayerList().getPlayers().get(0)), server);
    }

    /** Places the eye at (x, baseY + y, z) looking at (tx, baseY + ty, tz); flight keeps elevated cameras still. */
    private static void look(ServerPlayer player, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        lockYaw = yaw;
        lockPitch = pitch;
        player.teleportTo(player.serverLevel(), x, baseY + y - player.getEyeHeight(), z, yaw, pitch);
    }

    private static void cmd(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
    }

    private static void fill(MinecraftServer server, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        cmd(server, "fill " + x1 + " " + (baseY + y1) + " " + z1 + " " + x2 + " " + (baseY + y2) + " " + z2 + " " + block);
    }

    private static void set(MinecraftServer server, int x, int y, int z, String block) {
        cmd(server, "setblock " + x + " " + (baseY + y) + " " + z + " " + block);
    }

    private static void frame(ServerLevel level, int x, int y, int z, Direction facing, ItemStack item) {
        ItemFrame frame = new ItemFrame(level, new BlockPos(x, baseY + y, z), facing);
        frame.setItem(item, false);
        level.addFreshEntity(frame);
    }

    private static void buildShop(ServerLevel level, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        level.setDayTime(4000);
        level.setWeatherParameters(60000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, server);
        cmd(server, "kill @e[type=!player]");
        fill(server, -16, 0, -16, 16, 10, 12, "air");
        fill(server, -16, -1, -16, 16, -1, 12, "grass_block");
        RandomSource random = RandomSource.create(20260927L);
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 3; z++)
            set(server, x, -1, z, random.nextFloat() < .8F ? "dark_oak_planks" : "spruce_planks");
        // Plaster walls in a dark oak frame, windows onto the garden.
        fill(server, -8, 0, -8, 8, 4, -8, "calcite");
        fill(server, -8, 0, -8, -8, 4, 3, "calcite");
        fill(server, 8, 0, -8, 8, 4, 3, "calcite");
        fill(server, -8, 0, -8, 8, 0, -8, "stripped_dark_oak_log[axis=x]");
        fill(server, -8, 4, -8, 8, 4, -8, "stripped_dark_oak_log[axis=x]");
        for (int x : new int[]{-8, -5, 0, 5, 8}) fill(server, x, 0, -8, x, 4, -8, "dark_oak_log");
        for (int z : new int[]{-8, -4, 0, 3}) {
            fill(server, -8, 0, z, -8, 4, z, "dark_oak_log");
            fill(server, 8, 0, z, 8, 4, z, "dark_oak_log");
        }
        fill(server, -4, 2, -8, -1, 3, -8, "glass_pane");
        fill(server, 1, 2, -8, 4, 3, -8, "glass_pane");
        fill(server, -8, 2, -2, -8, 3, 1, "glass_pane");
        fill(server, -8, 5, -8, 8, 5, 3, "dark_oak_planks");
        for (int z : new int[]{-6, -2, 2}) fill(server, -7, 4, z, 7, 4, z, "dark_oak_log[axis=x]");
        for (int x : new int[]{-3, 3}) {
            set(server, x, 3, -2, "lantern[hanging=true]");
        }
        set(server, 0, 3, 2, "lantern[hanging=true]");
        // A counter of display cases along the right wall and shelves on the left.
        for (int z = -7; z <= -1; z++) {
            set(server, 7, 0, z, "dark_oak_slab[type=top]");
            set(server, 7, 1, z, "glass");
        }
        fill(server, -7, 0, -7, -7, 0, -1, "barrel[facing=east]");
        fill(server, -7, 1, -7, -7, 1, -1, "dark_oak_slab[type=bottom]");
        set(server, -7, 2, -5, "amethyst_cluster[facing=up]");
        set(server, -7, 2, -3, "potted_azure_bluet");
        set(server, -7, 2, -6, "candle[candles=3,lit=true]");
        set(server, -6, 0, -7, "amethyst_block");
        set(server, -6, 1, -7, "large_amethyst_bud[facing=up]");
        set(server, 6, 0, -7, "chest[facing=south]");
        set(server, 5, 0, -7, "budding_amethyst");
        set(server, 5, 1, -7, "amethyst_cluster[facing=up]");
        for (int x : new int[]{-5, -4, 4, 5}) set(server, x, 0, -7, "dark_oak_stairs[facing=south,half=top]");
        set(server, -5, 1, -7, "potted_red_tulip");
        set(server, 4, 1, -7, "candle[candles=2,lit=true]");
        // Garden outside the windows.
        int[][] trees = {{-6, -12}, {2, -13}, {9, -11}, {-12, -4}, {-13, 3}, {13, -3}};
        String[] kinds = {"birch", "fancy_oak", "birch", "oak", "birch", "oak"};
        for (int i = 0; i < trees.length; i++)
            cmd(server, "place feature minecraft:" + kinds[i] + " " + trees[i][0] + " " + baseY + " " + trees[i][1]);
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= -9; z++)
            if (random.nextFloat() < .25F) set(server, x, 0, z, random.nextFloat() < .7F ? "grass" : random.nextBoolean() ? "allium" : "azure_bluet");
        // Stations: wheels on the left, benches on the right.
        JewelryDefinitions defs = JewelryDefinitions.server();
        BlockState wheelState = ModBlocks.LAPIDARY_WHEEL.get().defaultBlockState().setValue(StationBlock.FACING, Direction.SOUTH);
        BlockState benchState = ModBlocks.JEWELERS_BENCH.get().defaultBlockState().setValue(StationBlock.FACING, Direction.SOUTH);
        level.setBlockAndUpdate(wheel, wheelState);
        level.setBlockAndUpdate(wheel2, wheelState);
        level.setBlockAndUpdate(bench, benchState);
        level.setBlockAndUpdate(bench2, benchState);
        wheelAt(level, wheel).swapLap(new ItemStack(ModItems.DIAMOND_LAP.get()));
        wheelAt(level, wheel).setStone(RoughGemItem.create(GemData.rough(ImmersiveJewelry.id("sapphire"), 1.2F, 0.82F, 0.08F)));
        wheelAt(level, wheel2).swapLap(new ItemStack(ModItems.EMERY_LAP.get()));
        wheelAt(level, wheel2).setStone(RoughGemItem.create(GemData.rough(ImmersiveJewelry.id("emerald"), 1.0F, 0.7F, 0.15F)));
        benchAt(level, bench).setWorkpiece(JewelryFactory.blank(ModItems.RING_BLANK.get(), JewelryFactory.metal(defs, GOLD), 0.9F));
        benchAt(level, bench2).setWorkpiece(ClientSmokeTest.piece(defs, "amulet", "emerald", "emerald", SILVER, "polished", 0.9F));
        // Finished work on display.
        frame(level, -4, 2, -7, Direction.SOUTH, ClientSmokeTest.piece(defs, "ring", "diamond", "brilliant", GOLD, "polished", 0.95F));
        frame(level, -3, 2, -7, Direction.SOUTH, ClientSmokeTest.piece(defs, "amulet", "sapphire", "round", SILVER, "polished", 0.9F));
        frame(level, -2, 2, -7, Direction.SOUTH, ClientSmokeTest.piece(defs, "circlet", "amethyst", "cabochon", GOLD, "antique", 0.85F));
        frame(level, 2, 2, -7, Direction.SOUTH, ClientSmokeTest.piece(defs, "bracelet", "topaz", "emerald", GOLD, "burnished", 0.8F));
        frame(level, 3, 2, -7, Direction.SOUTH, ClientSmokeTest.piece(defs, "ring", "emerald", "round", SILVER, "polished", 0.9F));
        frame(level, 4, 2, -7, Direction.SOUTH, ClientSmokeTest.piece(defs, "amulet", "ruby", "cabochon", GOLD, "antique", 0.88F));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(ModItems.JEWELERS_PLIERS.get()));
        player.getInventory().setItem(1, new ItemStack(ModItems.FINE_PLIERS.get()));
        player.getInventory().setItem(9, CutGemItem.create(JewelryFactory.sampleCut(defs.gem(ImmersiveJewelry.id("ruby")), defs.cut(ImmersiveJewelry.id("round")), 0.9F)));
        player.getInventory().setItem(10, CutGemItem.create(JewelryFactory.sampleCut(defs.gem(ImmersiveJewelry.id("diamond")), defs.cut(ImmersiveJewelry.id("brilliant")), 0.95F)));
        player.getInventory().setItem(11, CutGemItem.create(JewelryFactory.sampleCut(defs.gem(ImmersiveJewelry.id("sapphire")), defs.cut(ImmersiveJewelry.id("emerald")), 0.8F)));
        player.getInventory().setItem(12, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 8));
        player.getInventory().setItem(8, new ItemStack(ModItems.JEWELERS_HANDBOOK.get()));
        player.inventoryMenu.broadcastChanges();
        look(player, -2.9, 1.65, -1.4, 0.6, 0.7, -4.3);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        find(target.getClass(), name).set(target, value);
    }

    private static Field find(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void advance(int next) {
        step = next;
        action = 0;
        entered = Util.getMillis();
    }

    private StoreScreenshots() {}
}
