package com.otectus.immersivejewelry.testing;

import com.otectus.immersivejewelry.minigame.cutting.CutParams;
import com.otectus.immersivejewelry.minigame.cutting.CutSimulation;

/**
 * A reference cutter for tests: it plays a local copy of the simulation, aims the contact point at the centre of
 * the active facet (compensating the last step's wobble), grinds in bursts to keep the stone cool, and releases to
 * lock each facet near the middle of its window. {@link #careless} instead grinds the middle of the stone non-stop.
 */
public final class CutBot {
    private final CutSimulation sim;
    private final boolean careless;
    private float driftX;
    private float driftY;
    private boolean cooling;

    public CutBot(CutParams params, boolean careless) {
        this.sim = new CutSimulation(params);
        this.careless = careless;
    }

    /** One step's input: {x, y, grinding (0/1)}. Advances the bot's own simulation. */
    public int[] next() {
        CutParams p = sim.params();
        int facet = sim.activeFacet();
        int fx;
        int fy;
        boolean grind;
        if (careless) {
            fx = CutSimulation.toFixed(0F);
            fy = CutSimulation.toFixed(0F);
            grind = true;
        } else {
            float r = (p.innerRadius() + 1F) / 2F;
            float a = p.angles()[facet];
            float tx = (float) Math.cos(a) * r;
            float ty = (float) Math.sin(a) * r;
            fx = CutSimulation.toFixed(tx - driftX);
            fy = CutSimulation.toFixed(ty - driftY);
            float lo = p.windowCenter()[facet] - p.windowHalf();
            float target = p.windowCenter()[facet] - p.windowHalf() * 0.2F;
            if (sim.heat() > p.chipThreshold() - 0.25F) cooling = true;
            if (cooling && sim.heat() < p.chipThreshold() - 0.55F) cooling = false;
            float progress = sim.progress(facet);
            // Cool only while releasing cannot lock the facet early; near the window, stop and lock instead.
            grind = progress < target && !(cooling && progress < lo);
        }
        float before = CutSimulation.toStone(fx);
        float beforeY = CutSimulation.toStone(fy);
        sim.step(fx, fy, grind);
        driftX = sim.contactX() - before;
        driftY = sim.contactY() - beforeY;
        return new int[]{fx, fy, grind ? 1 : 0};
    }

    public CutSimulation sim() {
        return sim;
    }
}
