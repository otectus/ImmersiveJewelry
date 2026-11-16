package com.otectus.immersivejewelry.client.presentation;

import net.minecraftforge.fml.ModList;

/**
 * The optional client libraries (since 1.3.0): Ponder (with the Flywheel it needs) for the demonstrations, playerAnimator for
 * the jeweler's body poses. This class names neither, so it loads with or without them; every path into
 * {@code client/compat/ponder} and {@code client/presentation/anim}'s playerAnimator classes ({@code JewelerPoses},
 * {@code JewelerLayer}) asks it first, so a missing library's classes are never loaded.
 *
 * <p>Without Ponder the handbook's Watch buttons are greyed out (their tooltip says why) and the help key opens the step's page
 * instead of its lesson. Without playerAnimator nobody's body is posed at the stations, and a demonstration's jeweler stands
 * unposed. Nothing else changes: work, items, rigs, the handbook and the worn jewelry do not depend on either.</p>
 */
public final class OptionalLibraries {
    private static final boolean PONDER = ModList.get().isLoaded("ponder");
    private static final boolean PLAYER_ANIMATOR = ModList.get().isLoaded("playeranimator");

    /** Whether Ponder runs: the demonstrations exist. */
    public static boolean ponder() {
        return PONDER;
    }

    /** Whether playerAnimator runs: the jeweler's body poses exist. */
    public static boolean bodyPoses() {
        return PLAYER_ANIMATOR;
    }

    private OptionalLibraries() {}
}
