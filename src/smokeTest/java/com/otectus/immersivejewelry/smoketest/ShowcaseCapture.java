package com.otectus.immersivejewelry.smoketest;

import com.otectus.immersivejewelry.config.ClientConfig;
import com.pockyl.diegesis.impl.client.render.SmoothPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in, real framebuffer captures for the project gallery. Never included in the distributable mod. */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class ShowcaseCapture {
    private static final boolean ENABLED = System.getenv("IJ_CURSEFORGE_SCREENSHOTS") != null;
    private static Field overlay;

    static boolean enabled() { return ENABLED; }
    static boolean overviewOnly() { return System.getenv("IJ_SHOWCASE_OVERVIEW_ONLY") != null; }

    static void prepare(Minecraft mc) {
        mc.options.hideGui = true;
        mc.options.fov().set(65);
        mc.options.gamma().set(.8D);
        mc.options.guiScale().set(3);
        mc.resizeDisplay();
        ClientConfig.WORK_HINTS.set(false);
        ClientConfig.VISUAL_CUES.set(false);
        ClientConfig.NUMERIC_GUIDE.set(false);
    }

    static void tick(Minecraft mc) {
        mc.options.hideGui = true;
        mc.gui.getChat().clearMessages(false);
    }

    /** Keep the actual world/scene render, while omitting the focus screen's inventory strip and hover labels. */
    @SubscribeEvent
    public static void cleanFocusFrame(ScreenEvent.Render.Pre event) {
        if (!ENABLED || !event.getScreen().getClass().getName().equals("com.pockyl.diegesis.impl.client.focus.FocusScreen")) return;
        try {
            if (overlay == null) {
                overlay = event.getScreen().getClass().getDeclaredField("overlay");
                overlay.setAccessible(true);
            }
            // The loupe and design folio are real features, so retain their interface when demonstrating them.
            if (overlay.get(null) != null) return;
            SmoothPass.draw();
            event.setCanceled(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to prepare the clean showcase frame", e);
        }
    }

    static void overview(Minecraft mc) { overview(mc, 0); }

    static void overview(Minecraft mc, int angle) {
        ClientSmokeTest.server(mc, (server, player) -> {
            var base = ClientSmokeTest.base;
            double[] eye = switch (angle) {
                case 1 -> new double[]{-4.2, 1.8, .7};
                case 2 -> new double[]{5.8, 1.8, .7};
                case 3 -> new double[]{.5, 1.75, 1.8};
                default -> new double[]{.3, 2.15, 2.4};
            };
            double x = base.getX() + eye[0], y = base.getY() + eye[1], z = base.getZ() + eye[2];
            double tx = base.getX() + .6, ty = base.getY() + .75, tz = base.getZ() - 1.8;
            double dx = tx - x, dy = ty - y, dz = tz - z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(server.overworld(), x, y - player.getEyeHeight(), z, yaw, pitch);
        });
    }

    static void shot(Minecraft mc, String name) {
        String destination = System.getenv("IJ_SHOWCASE_OUTPUT");
        Path output = destination == null ? mc.gameDirectory.toPath().resolve("showcase") : Path.of(destination);
        try {
            Files.createDirectories(output);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(output.resolve(name + ".png"));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save showcase capture " + name, e);
        }
    }

    private ShowcaseCapture() {}
}
