package com.otectus.immersivejewelry.client.presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * The one clock of the stations' presentation (rig clips, settle animations): ticks with the partial tick. In the world
 * it is the level's game time, which stops when the game is paused; in a demonstration (Ponder) it is the scene's own
 * time, which follows the scene's pause, replay and speed. Gameplay never reads it: it only says where a cosmetic motion
 * that the synced state already decided is in its course.
 */
public final class PresentationClock {
    private static final List<Function<Level, OptionalDouble>> SOURCES = new ArrayList<>();

    /** A level kind with a time of its own (a demonstration level): consulted before the game time. */
    public static void register(Function<Level, OptionalDouble> source) {
        SOURCES.add(source);
    }

    /** Now, in ticks, for things in {@code level}. */
    public static double now(@Nullable Level level, float partialTick) {
        if (level == null) return 0;
        for (Function<Level, OptionalDouble> source : SOURCES) {
            OptionalDouble time = source.apply(level);
            if (time.isPresent()) return time.getAsDouble();
        }
        return level.getGameTime() + (double) partialTick;
    }

    /** Now, with the frame's partial tick. */
    public static double now(@Nullable Level level) {
        return now(level, Minecraft.getInstance().getFrameTime());
    }

    /** Whether {@code level} is a demonstration's own level (not the world being played). */
    public static boolean isPreview(@Nullable Level level) {
        if (level == null) return false;
        for (Function<Level, OptionalDouble> source : SOURCES) {
            if (source.apply(level).isPresent()) return true;
        }
        return false;
    }

    private PresentationClock() {}
}
