package com.otectus.immersivejewelry.client.presentation.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

import java.util.function.Consumer;

/**
 * Draws one GeckoLib rig inside a painter's frame (scene pixels, north-facing station space, or a tool's own frame): no
 * translation or facing of its own, so the rig lines up with the procedural work, the pick zones and the contact points.
 * The painter hands over the pose for this one draw ({@link #draw}); nothing is remembered between draws, so one rig serves
 * every station, every Ponder preview and every tool without a shared controller.
 *
 * <p>One bone may be tinted (the lap's tier colour) and the texture may be swapped per draw (the Fine Pliers). A rig whose
 * model is missing (a broken resource pack) is reported once and {@link #draw} returns false, so the caller draws its
 * procedural fallback and the station keeps working.</p>
 */
public final class RigRenderer extends GeoObjectRenderer<GeoAnimatable> {
    private final Model rig;
    private boolean failed;
    @Nullable
    private String tintBone;
    private int tint = 0xFFFFFF;

    public RigRenderer(String name) {
        this(new Model(name));
    }

    private RigRenderer(Model model) {
        super(model);
        this.rig = model;
    }

    public ResourceLocation animations() {
        return rig.animations;
    }

    /**
     * Draws the rig at the pose stack's origin, one painter pixel per model pixel (the stack is scaled to pixels).
     *
     * @param texture   null for the rig's own texture
     * @param tintBone  a bone whose cubes take {@code tint} (rgb), or null
     * @param pose      sets the bones for this draw (after {@link RigClip#rest})
     * @return false when the rig cannot be drawn (the caller draws its fallback)
     */
    public boolean draw(PoseStack stack, MultiBufferSource buffers, int light, GeoAnimatable owner, @Nullable ResourceLocation texture,
                        @Nullable String tintBone, int tint, Consumer<BakedGeoModel> pose) {
        if (failed) return false;
        if (GeckoLibCache.getBakedModels().get(rig.model) == null) {
            failed = true;
            ImmersiveJewelry.LOGGER.error("The GeckoLib rig {} is missing; its parts are drawn the plain way, the work is unaffected", rig.model);
            return false;
        }
        rig.textureOverride = texture;
        rig.pose = pose;
        this.tintBone = tintBone;
        this.tint = tint;
        stack.pushPose();
        // The painter's stack is in pixels; GeckoLib's baked model is in blocks.
        stack.scale(16F, 16F, 16F);
        try {
            defaultRender(stack, owner, buffers, null, null, 0F, Minecraft.getInstance().getFrameTime(), light);
            return true;
        } catch (RuntimeException e) {
            failed = true;
            ImmersiveJewelry.LOGGER.error("The GeckoLib rig {} could not be drawn and is replaced by plain parts", rig.model, e);
            return false;
        } finally {
            stack.popPose();
            rig.pose = null;
            this.tintBone = null;
            rig.textureOverride = null;
        }
    }

    /** No offset: the painter's frame is the rig's frame. */
    @Override
    public void preRender(PoseStack poseStack, GeoAnimatable animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        this.objectRenderTranslations = new org.joml.Matrix4f(poseStack.last().pose());
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (tintBone != null && tintBone.equals(bone.getName())) {
            red *= ((tint >> 16) & 0xFF) / 255F;
            green *= ((tint >> 8) & 0xFF) / 255F;
            blue *= (tint & 0xFF) / 255F;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** Whether this rig has drawn without failing so far (the client checks). */
    public boolean working() {
        return !failed;
    }

    /** After a resource reload a rig that failed is tried again. */
    public void reset() {
        failed = false;
    }

    private static final class Model extends GeoModel<GeoAnimatable> {
        final ResourceLocation model;
        final ResourceLocation texture;
        final ResourceLocation animations;
        @Nullable
        ResourceLocation textureOverride;
        @Nullable
        Consumer<BakedGeoModel> pose;

        Model(String name) {
            this.model = ImmersiveJewelry.id("geo/" + name + ".geo.json");
            this.texture = ImmersiveJewelry.id("textures/rig/" + name + ".png");
            this.animations = ImmersiveJewelry.id("animations/" + name + ".animation.json");
        }

        @Override
        public ResourceLocation getModelResource(GeoAnimatable animatable) {
            return model;
        }

        @Override
        public ResourceLocation getTextureResource(GeoAnimatable animatable) {
            return textureOverride != null ? textureOverride : texture;
        }

        @Override
        public ResourceLocation getAnimationResource(GeoAnimatable animatable) {
            return animations;
        }

        /** No controller pass (GeckoLib's clock is model-wide): the painter's pose is applied on every render. */
        @Override
        public void handleAnimations(GeoAnimatable animatable, long instanceId, AnimationState<GeoAnimatable> state) {
            Consumer<BakedGeoModel> current = pose;
            if (current != null) current.accept(getBakedModel(model));
        }
    }
}
