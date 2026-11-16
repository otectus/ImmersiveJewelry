package com.otectus.immersivejewelry.client.compat.ponder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivejewelry.client.presentation.anim.JewelerClip;
import com.otectus.immersivejewelry.client.presentation.anim.JewelerPoses;
import com.otectus.immersivejewelry.config.ClientConfig;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The demonstrating jeweler: a player model standing in the demonstration's own level, posed (when playerAnimator runs; else it
 * stands as modelled) by the very playerAnimator clips
 * a real jeweler plays ({@link JewelerClip}), sampled at the scene's time, so pause, replay, speed and chapter jumps always
 * show the right frame and nothing runs while the scene is paused. There is no entity or player behind it, no layer is
 * registered on any real player, and nothing is sent anywhere. {@link #reset} restores the opening state before Ponder
 * replays the scene.
 */
final class JewelerActor extends AnimatedSceneElementBase {
    private static final ResourceLocation SKIN = DefaultPlayerSkin.getDefaultSkin();
    @Nullable
    private static PlayerModel<AbstractClientPlayer> model;

    private final Vec3 startPos;
    private final float startYaw;
    private Vec3 pos;
    private float yaw;
    private ItemStack main = ItemStack.EMPTY;
    @Nullable
    private JewelerClip clip;
    private int clipStart;
    @Nullable
    private JewelerClip held;

    JewelerActor(Vec3 pos, float yaw) {
        this.startPos = pos;
        this.startYaw = yaw;
        restore();
    }

    private void restore() {
        pos = startPos;
        yaw = startYaw;
        main = ItemStack.EMPTY;
        clip = held = null;
        clipStart = 0;
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        restore();
    }

    /** A one-shot from now (then back to the held pose). */
    void play(JewelerClip next, int now) {
        clip = next;
        clipStart = now;
    }

    /** The pose held between one-shots (a working loop or a ready stance), or null to stand plainly. */
    void hold(@Nullable JewelerClip loop) {
        held = loop;
    }

    void move(Vec3 to) {
        pos = to;
    }

    void face(float degrees) {
        yaw = degrees;
    }

    void item(ItemStack stack) {
        main = stack.copy();
    }

    @Override
    protected void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
        PonderScene scene = world.scene;
        if (scene == null) return;
        double now = scene.getCurrentTime() + pt;
        PlayerModel<AbstractClientPlayer> m = model();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
        pose.scale(-1F, -1F, 1F);
        pose.scale(0.9375F, 0.9375F, 0.9375F);
        pose.translate(0F, -1.501F, 0F);
        posture(m, now);
        int light = lightCoordsFromFade(fade);
        m.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(SKIN)), light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        if (!main.isEmpty()) {
            pose.pushPose();
            m.translateToHand(HumanoidArm.RIGHT, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90F));
            pose.mulPose(Axis.YP.rotationDegrees(180F));
            pose.translate(1F / 16F, 0.125F, -0.625F);
            Minecraft.getInstance().getItemRenderer().renderStatic(null, main, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffer,
                    world, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        pose.popPose();
    }

    private void posture(PlayerModel<AbstractClientPlayer> m, double now) {
        m.head.resetPose();
        m.body.resetPose();
        m.rightArm.resetPose();
        m.leftArm.resetPose();
        m.rightLeg.resetPose();
        m.leftLeg.resetPose();
        m.crouching = false;
        m.young = false;
        JewelerClip playing = clip;
        double elapsed = now - clipStart;
        if (playing != null && elapsed >= playing.length + 2) playing = null;
        if (playing == null && held != null) {
            playing = held;
            elapsed = now;
        }
        if (playing != null) apply(m, playing, elapsed);
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    /** Poses the model at the clip's frame; without playerAnimator the jeweler stands as modelled. */
    private static void apply(PlayerModel<AbstractClientPlayer> m, JewelerClip playing, double elapsed) {
        if (!com.otectus.immersivejewelry.client.presentation.OptionalLibraries.bodyPoses()) return;
        boolean reduced = ClientConfig.reducedTutorialMotion() || ClientConfig.bodyAnimation() == ClientConfig.BodyAnimation.REDUCED;
        JewelerPoses.pose(m, playing, elapsed, reduced);
    }

    private static PlayerModel<AbstractClientPlayer> model() {
        if (model == null) model = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return model;
    }

    /** A resource reload rebakes model layers: the actor's model is built again on next use. */
    static void reload() {
        model = null;
    }

    static float clampYaw(float yaw) {
        return Mth.wrapDegrees(yaw);
    }
}
