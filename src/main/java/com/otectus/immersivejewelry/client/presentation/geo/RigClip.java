package com.otectus.immersivejewelry.client.presentation.geo;

import com.eliotlash.mclib.math.IValue;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.EasingType;
import software.bernie.geckolib.core.keyframe.AnimationPoint;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.core.keyframe.KeyframeStack;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates an authored GeckoLib clip at an explicit phase and writes it onto a baked model's bones.
 *
 * <p>GeckoLib 4.8.4 runs every {@code AnimationController} of a model on one model-wide clock that advances with the
 * frame, which cannot give a demonstration's scene time, a paused game or a late observer's phase. The rigs therefore keep
 * GeckoLib for its models, baked animations, easings and renderer, and sample the baked keyframes here at the phase the
 * station's synced state names: the jaws from the pliers' opening, the vise from the time since the clamp changed, the lap
 * from the time since it was put on. Values are GeckoLib's baked values (radians, game axes), added to each bone's rest
 * pose exactly as a controller would add them.</p>
 *
 * <p>Bones are shared by every render of a baked model, so a rig sets every bone it animates on every render: call
 * {@link #rest} first, then {@link #apply}. Missing files or clips are reported once and leave the bones at rest.</p>
 */
public final class RigClip {
    private static final Set<String> REPORTED = new HashSet<>();

    /** Puts the named bones back to their rest pose (as authored in the geometry), visible. */
    public static void rest(BakedGeoModel model, String... bones) {
        for (String name : bones) {
            Optional<GeoBone> bone = model.getBone(name);
            if (bone.isEmpty()) continue;
            BoneSnapshot rest = bone.get().getInitialSnapshot();
            GeoBone b = bone.get();
            b.setRotX(rest.getRotX());
            b.setRotY(rest.getRotY());
            b.setRotZ(rest.getRotZ());
            b.setPosX(rest.getOffsetX());
            b.setPosY(rest.getOffsetY());
            b.setPosZ(rest.getOffsetZ());
            b.setScaleX(rest.getScaleX());
            b.setScaleY(rest.getScaleY());
            b.setScaleZ(rest.getScaleZ());
            b.setHidden(false);
        }
    }

    /**
     * Applies {@code clip} of {@code animations} at {@code tick} (clamped into the clip, or wrapped when {@code loop}),
     * scaled by {@code weight}, on top of the bones' current values. Returns whether the clip exists.
     */
    public static boolean apply(BakedGeoModel model, ResourceLocation animations, String clip, double tick, float weight, boolean loop) {
        Animation animation = find(animations, clip);
        if (animation == null || weight <= 0F) return animation != null;
        double length = Math.max(1e-3, animation.length());
        double t = loop ? ((tick % length) + length) % length : Math.max(0, Math.min(length, tick));
        for (BoneAnimation boneAnimation : animation.boneAnimations()) {
            Optional<GeoBone> found = model.getBone(boneAnimation.boneName());
            if (found.isEmpty()) continue;
            GeoBone bone = found.get();
            double[] rotation = sample(boneAnimation.rotationKeyFrames(), t);
            if (rotation != null) {
                bone.setRotX(bone.getRotX() + (float) rotation[0] * weight);
                bone.setRotY(bone.getRotY() + (float) rotation[1] * weight);
                bone.setRotZ(bone.getRotZ() + (float) rotation[2] * weight);
            }
            double[] position = sample(boneAnimation.positionKeyFrames(), t);
            if (position != null) {
                bone.setPosX(bone.getPosX() + (float) position[0] * weight);
                bone.setPosY(bone.getPosY() + (float) position[1] * weight);
                bone.setPosZ(bone.getPosZ() + (float) position[2] * weight);
            }
            double[] scale = sample(boneAnimation.scaleKeyFrames(), t);
            if (scale != null) {
                bone.setScaleX(lerp(bone.getScaleX(), (float) scale[0] * bone.getScaleX(), weight));
                bone.setScaleY(lerp(bone.getScaleY(), (float) scale[1] * bone.getScaleY(), weight));
                bone.setScaleZ(lerp(bone.getScaleZ(), (float) scale[2] * bone.getScaleZ(), weight));
            }
        }
        return true;
    }

    /** Applies a clip at a progress of 0..1 through its length. */
    public static boolean applyProgress(BakedGeoModel model, ResourceLocation animations, String clip, float progress, float weight) {
        double length = length(animations, clip);
        return apply(model, animations, clip, length * Math.max(0F, Math.min(1F, progress)), weight, false);
    }

    /** A clip's length in ticks, or 0 when it is missing. */
    public static double length(ResourceLocation animations, String clip) {
        Animation animation = find(animations, clip);
        return animation == null ? 0 : animation.length();
    }

    @Nullable
    public static Animation find(ResourceLocation animations, String clip) {
        BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(animations);
        Animation animation = baked == null ? null : baked.getAnimation(clip);
        if (animation == null && REPORTED.add(animations + "#" + clip)) {
            ImmersiveJewelry.LOGGER.error("GeckoLib clip {} in {} is missing; that motion stays at rest", clip, animations);
        }
        return animation;
    }

    /** After a resource reload: a missing clip of the new resources is reported again. */
    public static void reloaded() {
        REPORTED.clear();
    }

    @Nullable
    private static double[] sample(KeyframeStack<Keyframe<IValue>> stack, double tick) {
        if (stack == null || (stack.xKeyframes().isEmpty() && stack.yKeyframes().isEmpty() && stack.zKeyframes().isEmpty())) return null;
        return new double[]{axis(stack.xKeyframes(), tick), axis(stack.yKeyframes(), tick), axis(stack.zKeyframes(), tick)};
    }

    /** Walks one axis' keyframes (each with its own length) to the one that contains {@code tick}. */
    private static double axis(List<Keyframe<IValue>> frames, double tick) {
        if (frames.isEmpty()) return 0;
        double remaining = tick;
        for (Keyframe<IValue> frame : frames) {
            if (remaining <= frame.length() || frame == frames.get(frames.size() - 1)) {
                double start = frame.startValue().get(), end = frame.endValue().get();
                if (frame.length() <= 0) return end;
                double progress = Math.min(remaining, frame.length());
                return EasingType.lerpWithOverride(new AnimationPoint(frame, progress, frame.length(), start, end), null);
            }
            remaining -= frame.length();
        }
        return frames.get(frames.size() - 1).endValue().get();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private RigClip() {}
}
