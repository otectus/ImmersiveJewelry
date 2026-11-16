package com.otectus.immersivejewelry.smoketest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.immersivejewelry.client.workstation.render.Geo;
import com.otectus.immersivejewelry.client.workstation.render.Hands;
import com.otectus.immersivejewelry.config.ClientConfig;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runtime regression probes for the baked vanilla arm meshes and the workstation arm presentation options. */
final class WorkstationArmChecks {
    private static final ResourceLocation SKIN = new ResourceLocation("minecraft", "textures/entity/steve.png");
    private static final boolean VISUALS = System.getenv("IJ_ARM_VISUALS") != null;
    private static GraphicsStatus savedGraphics;
    private static HumanoidArm savedHand;
    private static HumanoidArm savedPlayerHand;
    private static boolean savedHands;
    private static double savedOpacity;
    private static boolean visualStateSaved;

    static boolean visualsEnabled() { return VISUALS; }

    /** Capture vertices from Hands' actual baked-model draw path, including its transform and alpha wrapper. */
    static void checkGeometry(Minecraft mc, Map<String, Object> checks) {
        try {
            for (boolean slim : List.of(false, true)) {
                String shape = slim ? "slim" : "classic";
                float width = slim ? 3F : 4F;
                for (boolean right : List.of(false, true)) {
                    String side = right ? "right" : "left";
                    Capture arm = render(slim, right, false, 1F);
                    Capture sleeved = render(slim, right, true, 1F);
                    checks.put("arm_mesh_" + shape + "_" + side,
                            arm.vertices.size() == 24 && arm.extents(width, 12F, 4F));
                    checks.put("sleeve_mesh_" + shape + "_" + side,
                            sleeved.vertices.size() == 48 && sleeved.extents(width + .5F, 12.5F, 4.5F));
                }
            }

            Capture opaque = render(false, true, false, 1F);
            Capture translucent = render(false, true, false, .45F);
            Set<Integer> translucentAlpha = new HashSet<>(translucent.alpha);
            checks.put("arm_opaque_alpha", opaque.alpha.size() == 24 && opaque.alpha.stream().allMatch(a -> a == 255));
            checks.put("arm_translucent_shoulder_fade", translucent.alpha.size() == 24
                    && translucentAlpha.size() >= 2 && translucent.alpha.stream().allMatch(a -> a >= 0 && a <= 115)
                    && translucent.alpha.stream().anyMatch(a -> a == 0)
                    && translucent.alpha.stream().anyMatch(a -> a >= 110));

            boolean oldHands = ClientConfig.get(ClientConfig.WORKSTATION_HANDS);
            double oldOpacity = ClientConfig.get(ClientConfig.WORKSTATION_ARM_OPACITY);
            HumanoidArm oldHand = mc.options.mainHand().get();
            HumanoidArm oldPlayerHand = mc.player.getMainArm();
            try {
                Field pending = Hands.class.getDeclaredField("PENDING");
                pending.setAccessible(true);
                Hands.clear();
                Hands.operator(mc.player.getUUID());
                ClientConfig.WORKSTATION_HANDS.set(true);
                mc.options.mainHand().set(HumanoidArm.LEFT);
                mc.player.setMainArm(HumanoidArm.LEFT);
                ClientConfig.WORKSTATION_ARM_OPACITY.set(0D);
                hold();
                boolean zeroHidden = ((List<?>) pending.get(null)).isEmpty();
                ClientConfig.WORKSTATION_ARM_OPACITY.set(.45D);
                hold();
                List<?> queued = (List<?>) pending.get(null);
                boolean leftQueued = queued.size() == 1 && !armRight(queued.get(0));
                Hands.renderStage(new net.minecraftforge.client.event.RenderLevelStageEvent(
                        net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS,
                        mc.levelRenderer, new PoseStack(), new Matrix4f(), 0, 0F, null, null));
                checks.put("arm_opacity_zero_queues_nothing", zeroHidden);
                checks.put("left_main_hand_mirrors_arm_and_tool", leftQueued && Hands.toolBack(1, 0, 0).x < 0);
                checks.put("arm_waits_past_world_translucency", ((List<?>) pending.get(null)).size() == 1);
            } finally {
                Hands.clear();
                ClientConfig.WORKSTATION_HANDS.set(oldHands);
                ClientConfig.WORKSTATION_ARM_OPACITY.set(oldOpacity);
                mc.options.mainHand().set(oldHand);
                mc.player.setMainArm(oldPlayerHand);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            checks.put("arm_geometry_probe", e.toString());
            checks.put("arm_geometry_probe_passed", false);
        }
    }

    /** Four reviewable variants in the already-focused bench scene; enabled only with IJ_ARM_VISUALS=1. */
    static boolean visualTick(Minecraft mc, Map<String, Object> checks, int tick) {
        if (tick == 0) {
            savedGraphics = mc.options.graphicsMode().get();
            savedHand = mc.options.mainHand().get();
            savedPlayerHand = mc.player.getMainArm();
            savedHands = ClientConfig.get(ClientConfig.WORKSTATION_HANDS);
            savedOpacity = ClientConfig.get(ClientConfig.WORKSTATION_ARM_OPACITY);
            visualStateSaved = true;
            ClientConfig.WORKSTATION_HANDS.set(true);
            mc.options.mainHand().set(HumanoidArm.LEFT);
            mc.player.setMainArm(HumanoidArm.LEFT);
            mc.options.graphicsMode().set(GraphicsStatus.FANCY);
            ClientConfig.WORKSTATION_ARM_OPACITY.set(.45D);
            mc.levelRenderer.allChanged();
        }
        if (tick == 16) ClientSmokeTest.shot(mc, "smoketest_arm_left_fancy_045");
        if (tick == 24) ClientConfig.WORKSTATION_ARM_OPACITY.set(0D);
        if (tick == 40) ClientSmokeTest.shot(mc, "smoketest_arm_left_fancy_0");
        if (tick == 48) ClientConfig.WORKSTATION_ARM_OPACITY.set(1D);
        if (tick == 64) ClientSmokeTest.shot(mc, "smoketest_arm_left_fancy_1");
        if (tick == 72) {
            ClientConfig.WORKSTATION_ARM_OPACITY.set(.45D);
            mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);
            mc.levelRenderer.allChanged();
        }
        if (tick == 96) {
            checks.put("arm_visual_left_main_hand", mc.options.mainHand().get() == HumanoidArm.LEFT
                    && mc.player.getMainArm() == HumanoidArm.LEFT);
            checks.put("arm_visual_fabulous_mode", mc.options.graphicsMode().get() == GraphicsStatus.FABULOUS);
            ClientSmokeTest.shot(mc, "smoketest_arm_left_fabulous_045");
        }
        if (tick >= 104) {
            restore(mc);
            return true;
        }
        return false;
    }

    static void restore(Minecraft mc) {
        if (!visualStateSaved) return;
        ClientConfig.WORKSTATION_HANDS.set(savedHands);
        ClientConfig.WORKSTATION_ARM_OPACITY.set(savedOpacity);
        mc.options.mainHand().set(savedHand);
        mc.player.setMainArm(savedPlayerHand);
        mc.options.graphicsMode().set(savedGraphics);
        mc.levelRenderer.allChanged();
        visualStateSaved = false;
    }

    private static void hold() {
        MultiBufferSource buffers = type -> new Capture();
        Hands.hold(new Geo(new PoseStack(), buffers, 0), new Vector3f(), new Vector3f(1, 0, 0), true);
    }

    private static boolean armRight(Object arm) throws ReflectiveOperationException {
        Method right = arm.getClass().getDeclaredMethod("right");
        right.setAccessible(true);
        return (boolean) right.invoke(arm);
    }

    private static Capture render(boolean slim, boolean right, boolean sleeve, float opacity)
            throws ReflectiveOperationException {
        PoseStack pose = new PoseStack();
        Method poseArm = Hands.class.getDeclaredMethod("poseArm", PoseStack.class, Vec3.class, Vec3.class,
                boolean.class, boolean.class);
        poseArm.setAccessible(true);
        // This direction makes the rotation identity, so the extents are directly auditable in model pixels.
        poseArm.invoke(null, pose, Vec3.ZERO, new Vec3(0, -1, 0), slim, right);
        Capture capture = new Capture();
        MultiBufferSource buffers = type -> capture;
        Method draw = Hands.class.getDeclaredMethod("draw", PoseStack.class, MultiBufferSource.class,
                ResourceLocation.class, boolean.class, boolean.class, boolean.class, float.class, int.class);
        draw.setAccessible(true);
        draw.invoke(null, pose, buffers, SKIN, slim, right, sleeve, opacity, 0);
        return capture;
    }

    private static final class Capture implements VertexConsumer {
        private final List<Vector3f> vertices = new ArrayList<>();
        private final List<Integer> alpha = new ArrayList<>();

        boolean extents(float x, float y, float z) {
            if (vertices.isEmpty()) return false;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (Vector3f p : vertices) {
                minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
                maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
            }
            return close(maxX - minX, x) && close(maxY - minY, y) && close(maxZ - minZ, z);
        }

        private static boolean close(float actual, float expected) { return Math.abs(actual - expected) < 1.0e-4F; }

        @Override public VertexConsumer vertex(double x, double y, double z) {
            vertices.add(new Vector3f((float) x, (float) y, (float) z));
            return this;
        }
        @Override public VertexConsumer color(int red, int green, int blue, int alpha) { this.alpha.add(alpha); return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int red, int green, int blue, int alpha) {}
        @Override public void unsetDefaultColor() {}
    }

    private WorkstationArmChecks() {}
}
