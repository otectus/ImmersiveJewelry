package com.otectus.immersivejewelry.testing;

import com.otectus.immersivejewelry.workstation.sim.SimIO;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;

/**
 * A reference player finishing or repairing a piece: works the right tool round the piece in overlapping passes, a
 * band at a time across its width, and lifts when the work is done. Only control samples.
 */
public final class SurfaceBot {
    private int tick;
    private final boolean scrubOneSpot;

    public SurfaceBot() {
        this(false);
    }

    /** {@code scrubOneSpot}: rubs one place forever (to show that it never finishes the piece). */
    public SurfaceBot(boolean scrubOneSpot) {
        this.scrubOneSpot = scrubOneSpot;
    }

    public WorkSample next(SurfaceSim sim) {
        tick++;
        int tool = sim.params().mode().tool(sim.phase());
        if (sim.complete()) return new WorkSample(0, 0, 0, 0, tool, 0);
        if (scrubOneSpot) {
            float u = 0.25F + 0.004F * (float) Math.sin(tick * 0.5);
            return new WorkSample(SimIO.toUnit12(u), SimIO.toUnit12(0.5F), SimIO.toUnit12(0.6F), 0, tool, WorkSample.FLAG_CONTACT);
        }
        float u = (tick * 0.011F) % 1F;
        int band = (tick / 91) % 3;
        float v = band == 0 ? 0.15F : band == 1 ? 0.5F : 0.85F;
        return new WorkSample(SimIO.toUnit12(u), SimIO.toUnit12(v), SimIO.toUnit12(0.6F), 0, tool, WorkSample.FLAG_CONTACT);
    }

    public int run(SurfaceSim sim, int maxSteps) {
        int steps = 0;
        while (!sim.complete() && steps < maxSteps) {
            sim.step(next(sim));
            steps++;
        }
        return steps;
    }
}
