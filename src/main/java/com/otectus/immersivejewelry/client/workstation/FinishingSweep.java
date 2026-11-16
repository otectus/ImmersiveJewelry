package com.otectus.immersivejewelry.client.workstation;

/** A continuous assisted stroke around the frame and independently across its full width. */
public final class FinishingSweep {
    private double phase = Double.NaN;

    public record Point(float u, float v) {}

    public void reset() { phase = Double.NaN; }

    public Point step(float u, float v, float seconds) {
        // Start at the manually placed tool, so switching to assistance does not snap across the work.
        if (Double.isNaN(phase)) phase = Math.acos(Math.max(-1, Math.min(1, 1 - 2 * v))) / (2 * Math.PI);
        phase = (phase + 0.04 * seconds) % 1;
        return new Point((u + 0.22F * seconds) % 1F, (float) (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI)));
    }
}
