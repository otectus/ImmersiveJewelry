package com.otectus.immersivejewelry.api.event;

import com.otectus.immersivejewelry.api.gem.GemData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Posted when a cut finishes at a Lapidary Wheel, before the stone is placed back in the wheel. {@code after} is null
 * when the stone shattered. Since 1.1.0 listeners may refine the result with {@link #setAfter}: the same stone
 * (gem, cut, size, damage) at another cut quality. A shattered stone stays shattered and a cut one cannot be
 * un-cut. Server side.
 */
public class GemCutEvent extends PlayerEvent {
    private final GemData before;
    @Nullable
    private GemData after;
    private final BlockPos wheel;
    private int extraDust;

    public GemCutEvent(ServerPlayer player, GemData before, @Nullable GemData after, BlockPos wheel) {
        super(player);
        this.before = before;
        this.after = after;
        this.wheel = wheel;
    }

    public GemData getBefore() { return before; }
    @Nullable public GemData getAfter() { return after; }
    public BlockPos getWheel() { return wheel; }

    /**
     * Replaces the cut stone. Accepted only while the stone did not shatter and {@code replacement} is the same gem,
     * cut, size and damage as the current result; anything else is ignored and returns false.
     */
    public boolean setAfter(GemData replacement) {
        if (after == null || replacement == null) return false;
        if (!after.gem().equals(replacement.gem()) || after.cut() == null || !after.cut().equals(replacement.cut())) return false;
        if (after.size() != replacement.size() || after.damage() != replacement.damage()) return false;
        this.after = replacement;
        return true;
    }

    /** Convenience for the common case: the same stone at {@code quality} (clamped by {@link GemData}). */
    public boolean setCutQuality(float quality) {
        return after != null && setAfter(after.withCutQuality(quality));
    }

    /** Extra gem dust the wheel leaves when the stone shattered, on top of its own one or two. Ignored for a cut stone. */
    public int getExtraDust() { return extraDust; }

    /** Adds gem dust to a shattered stone's remains; the total extra is kept within 0..62. */
    public void addExtraDust(int count) {
        this.extraDust = Math.max(0, Math.min(62, extraDust + count));
    }
}
