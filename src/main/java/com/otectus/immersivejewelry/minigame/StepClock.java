package com.otectus.immersivejewelry.minigame;

/**
 * Both minigames run in fixed 20 ms steps. The client records its input at every step and streams those step inputs
 * to the server, which replays them through the same simulation: an honest client's outcome is reproduced exactly,
 * and nothing about the outcome is ever sent, only inputs.
 */
public final class StepClock {
    public static final int STEP_MS = 20;
    public static final float DT = STEP_MS / 1000F;

    private StepClock() {}
}
