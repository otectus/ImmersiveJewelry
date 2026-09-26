package com.otectus.immersivejewelry.minigame;

import com.otectus.immersivejewelry.config.ServerConfig;

/**
 * Server-side checks of the step stream a client reports. Steps may arrive late (latency) but never earlier than
 * real time allows: a client can not play faster than the clock, whatever it sends.
 */
public final class SessionTiming {

    /** How many of {@code count} steps starting at {@code firstStep} are plausible at {@code elapsedMs} into the session. */
    public static int acceptable(int firstStep, int count, long elapsedMs, int latencyMs) {
        long allowedSteps = (elapsedMs + ServerConfig.get(ServerConfig.TIMING_TOLERANCE_MS)) / StepClock.STEP_MS + 1;
        long room = allowedSteps - firstStep;
        return (int) Math.max(0, Math.min(count, room));
    }

    /** Extra time the server waits past the end of a session for honest late inputs. */
    public static long graceMs(int latencyMs) {
        int compensation = Math.min(Math.max(latencyMs, 0), ServerConfig.get(ServerConfig.MAX_LATENCY_COMPENSATION_MS));
        return compensation + ServerConfig.get(ServerConfig.TIMING_TOLERANCE_MS) + 1500L;
    }

    private SessionTiming() {}
}
