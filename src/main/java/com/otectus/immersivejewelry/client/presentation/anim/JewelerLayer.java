package com.otectus.immersivejewelry.client.presentation.anim;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Optional;

/**
 * One player's Jewelry layer: the current clip, fades between clips, and a bounded turn of the arms toward the station's
 * work, outermost. Clips key only the arms, the torso and lightly the head; legs and movement stay with the game. First
 * person is {@link FirstPersonMode#DISABLED}: at a station the focused view draws the operator's own hands and tools, so
 * this layer never adds a second pair.
 *
 * <p>The correction turns both arms toward the station by at most {@value #MAX_YAW_DEGREES} degrees and fades out with
 * distance. It is presentation only and implies no reach.</p>
 */
final class JewelerLayer extends ModifierLayer<IAnimation> {
    static final float MAX_YAW_DEGREES = 20F;

    private final WeakReference<AbstractClientPlayer> player;
    @Nullable
    private Vec3 target;
    @Nullable
    private JewelerClip clip;

    JewelerLayer(AbstractClientPlayer player) {
        super(null);
        this.player = new WeakReference<>(player);
        addModifierLast(new AdjustmentModifier(this::correction));
    }

    void play(JewelerClip next, KeyframeAnimation animation, int fadeIn, @Nullable Vec3 at) {
        KeyframeAnimationPlayer keyframes = new KeyframeAnimationPlayer(animation, 0).setFirstPersonMode(FirstPersonMode.DISABLED);
        clip = next;
        target = at;
        if (fadeIn > 0) replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeIn, Ease.INOUTSINE), keyframes, true);
        else setAnimation(keyframes);
    }

    void stop(int fadeOut) {
        clip = null;
        target = null;
        replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeOut, Ease.INOUTSINE), null);
    }

    /** At once (death, logout, a level change): the clip and any blend in progress go. */
    void stopNow() {
        clip = null;
        target = null;
        while (size() > 1) removeModifier(size() - 1);
        setAnimation(null);
    }

    @Nullable
    JewelerClip clip() {
        return clip;
    }

    void retarget(@Nullable Vec3 at) {
        target = at;
    }

    private Optional<AdjustmentModifier.PartModifier> correction(String part) {
        AbstractClientPlayer jeweler = player.get();
        Vec3 at = target;
        if (jeweler == null || at == null || clip == null) return Optional.empty();
        if (!part.equals("rightArm") && !part.equals("leftArm")) return Optional.empty();
        double dx = at.x - jeweler.getX(), dz = at.z - jeweler.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float weight = (float) Mth.clamp((3.5 - horizontal) / 1.5, 0.0, 1.0);
        if (weight <= 0F) return Optional.empty();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yaw = Mth.clamp(Mth.wrapDegrees(targetYaw - jeweler.yBodyRot), -MAX_YAW_DEGREES, MAX_YAW_DEGREES) * Mth.DEG_TO_RAD;
        return Optional.of(new AdjustmentModifier.PartModifier(new Vec3f(0F, yaw * weight, 0F), Vec3f.ZERO));
    }
}
