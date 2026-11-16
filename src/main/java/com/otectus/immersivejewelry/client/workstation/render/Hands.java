package com.otectus.immersivejewelry.client.workstation.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.immersivejewelry.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Rigid vanilla arms, composited after the station's glass, liquid and effects. */
public final class Hands {
    private static final float PX = 1F / 16F;
    private static final List<Arm> PENDING = new ArrayList<>();
    // A private buffer avoids flushing or changing another renderer's translucent batches.
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(4096));
    private static ModelPart classic, slim;

    private record Arm(Matrix4f pose, Matrix3f normal, ResourceLocation skin, boolean slim, boolean right,
                       boolean sleeve, float opacity, int light) {}

    /** The hand centre is the anchor. Translation and rotation never change the 4x12x4 (3x12x4 slim) geometry. */
    private static void poseArm(PoseStack pose, Vec3 grip, Vec3 toward, boolean slim, boolean right) {
        Vec3 direction = toward.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, -1) : toward.normalize();
        pose.translate(grip.x, grip.y, grip.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                new Vector3f((float) -direction.x, (float) -direction.y, (float) -direction.z)));
        // Diegesis painters use pixels; ModelPart uses blocks. This conversion is fixed for every task.
        pose.scale(16F, 16F, 16F);
        pose.translate((right ? 1 : -1) * (slim ? .5F : 1F) * PX, -10F * PX, 0);
    }

    @Nullable
    private static UUID operator;

    public static void operator(@Nullable UUID player) { operator = player; }

    /** Mirror the tool and the arm together for the operator's preferred hand. */
    public static Vector3f toolBack(float x, float y, float z) {
        var mc = Minecraft.getInstance();
        var player = operator != null && mc.level != null ? mc.level.getPlayerByUUID(operator) : null;
        return new Vector3f(player != null && player.getMainArm() == HumanoidArm.LEFT ? -x : x, y, z).normalize();
    }

    /** A rigid vanilla arm, anchored at the hand; toward points from the grip towards the shoulder. */
    public static void hold(Geo geo, Vector3f grip, Vector3f toward, boolean primary) {
        float opacity = (float) ClientConfig.get(ClientConfig.WORKSTATION_ARM_OPACITY);
        if (!ClientConfig.get(ClientConfig.WORKSTATION_HANDS) || opacity <= 0 || operator == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getPlayerByUUID(operator) instanceof AbstractClientPlayer player)
                || player.isInvisible()) return;
        boolean right = (player.getMainArm() == HumanoidArm.RIGHT) == primary;
        boolean slim = player.getModelName().equals("slim");
        boolean sleeve = player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
        // Reach from the appropriate side without changing the tool's grip or the arm's dimensions.
        Vec3 direction = new Vec3(right ? -Math.abs(toward.x) : Math.abs(toward.x), toward.y, toward.z);
        PoseStack pose = geo.pose();
        pose.pushPose();
        try {
            poseArm(pose, new Vec3(grip.x, grip.y, grip.z), direction, slim, right);
            PENDING.add(new Arm(new Matrix4f(pose.last().pose()), new Matrix3f(pose.last().normal()),
                    player.getSkinTextureLocation(), slim, right, sleeve, opacity, geo.light()));
        } finally {
            pose.popPose();
        }
    }

    private static ModelPart model(boolean narrow) {
        ModelPart root = narrow ? slim : classic;
        if (root == null) {
            root = Minecraft.getInstance().getEntityModels().bakeLayer(narrow ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER);
            for (String name : List.of("right_arm", "left_arm", "right_sleeve", "left_sleeve")) {
                ModelPart part = root.getChild(name);
                part.setPos(0, 0, 0);
                part.setRotation(0, 0, 0);
            }
            if (narrow) slim = root; else classic = root;
        }
        return root;
    }

    private static void draw(PoseStack pose, MultiBufferSource buffers, ResourceLocation skin, boolean slim,
                             boolean right, boolean sleeve, float opacity, int light) {
        ModelPart root = model(slim);
        String side = right ? "right" : "left";
        // Cull back faces so a translucent box does not accumulate four overlapping skin layers.
        VertexConsumer vertices = buffers.getBuffer(RenderType.itemEntityTranslucentCull(skin));
        if (opacity < 1) vertices = new ShoulderFade(vertices, pose.last().pose());
        root.getChild(side + "_arm").render(pose, vertices, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, opacity);
        if (sleeve) root.getChild(side + "_sleeve").render(pose, vertices, light, OverlayTexture.NO_OVERLAY,
                1, 1, 1, opacity * .55F);
    }

    /** Feather the unattached shoulder into the view, without lengthening, tapering or cropping the vanilla mesh. */
    private static final class ShoulderFade implements VertexConsumer {
        private final VertexConsumer inner;
        private final Matrix4f inverse;
        private final Vector3f local = new Vector3f();
        private float fade;

        private ShoulderFade(VertexConsumer inner, Matrix4f transform) {
            this.inner = inner;
            inverse = new Matrix4f(transform).invert();
        }

        @Override public VertexConsumer vertex(double x, double y, double z) {
            inverse.transformPosition(local.set((float) x, (float) y, (float) z));
            fade = Math.max(0, Math.min(1, (local.y * 16 + 2) / 12));
            inner.vertex(x, y, z);
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { inner.color(r, g, b, Math.round(a * fade)); return this; }
        @Override public VertexConsumer uv(float u, float v) { inner.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { inner.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { inner.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { inner.normal(x, y, z); return this; }
        @Override public void endVertex() { inner.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { inner.defaultColor(r, g, b, Math.round(a * fade)); }
        @Override public void unsetDefaultColor() { inner.unsetDefaultColor(); }
    }

    public static void renderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            PENDING.clear();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES && !PENDING.isEmpty()) {
            // Depth testing still lets solid apparatus occlude the arm. Drawing after world translucency means the
            // arm's depth cannot erase the liquid or glass which the player needs to see through it. In Fabulous,
            // use the same item/entity target as those materials, before the final fullscreen composite writes depth.
            PoseStack pose = new PoseStack();
            try {
                for (Arm arm : PENDING) {
                    pose.last().pose().set(arm.pose);
                    pose.last().normal().set(arm.normal);
                    draw(pose, BUFFERS, arm.skin, arm.slim, arm.right, arm.sleeve, arm.opacity, arm.light);
                }
                BUFFERS.endBatch();
            } finally {
                PENDING.clear();
            }
        }
    }

    public static void clear() {
        PENDING.clear();
        operator = null;
        classic = slim = null;
    }

    private Hands() {}
}
