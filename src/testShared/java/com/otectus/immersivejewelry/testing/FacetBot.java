package com.otectus.immersivejewelry.testing;

import com.otectus.immersivejewelry.workstation.sim.FacetParams;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import com.otectus.immersivejewelry.workstation.sim.SimIO;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;

/**
 * A reference player at the Lapidary Wheel, shared by the unit tests, the GameTests and the client smoke test. It
 * watches the stone the way a person does (which face is next, how deep it is, whether it runs hot) and produces only
 * control samples: where the handle is, which detent the collar is on. It never touches an outcome.
 *
 * <ul>
 *   <li>{@link Style#CAREFUL}: moderate force, smooth sweeps, lifts just inside each window, cools when warm.</li>
 *   <li>{@link Style#HEAVY}: presses hard and jerks the handle: heat, scratches, rocking and overcuts.</li>
 *   <li>{@link Style#STILL}: grinds without sweeping: uneven faces, flat spots on domes.</li>
 * </ul>
 */
public final class FacetBot {
    public enum Style { CAREFUL, HEAVY, STILL }

    private final Style style;
    private final int trim;
    private int tick;
    private boolean cooling;
    private int working = -1;

    public FacetBot(Style style) {
        this(style, 0);
    }

    /** {@code trim}: the fine trim the bot sets on the collar (a careful worker corrects the dop's turn). */
    public FacetBot(Style style, int trim) {
        this.style = style;
        this.trim = trim;
    }

    /** The sample for the next step, given what the stone looks like now. */
    public WorkSample next(FacetSim sim) {
        tick++;
        FacetParams p = sim.params();
        int aux = trim + WorkSample.TRIM_BIAS;
        // Keep working the face in hand until it is where this style stops; only then take the next recommended one.
        int region = working >= 0 && sim.depth(working) < stopAt(p, working) ? working : sim.recommended();
        if (region < 0) return lifted(sim.region(), 0F, aux);
        working = region;
        float target = p.target()[region];
        float stopAt = stopAt(p, region);
        if (sim.region() != region) return lifted(region, 0F, aux);
        if (style == Style.CAREFUL) {
            if (sim.heat() > p.chipThreshold() * 0.6F) cooling = true;
            if (cooling && sim.heat() < p.chipThreshold() * 0.25F) cooling = false;
            if (cooling) return lifted(region, 0F, aux);
        }
        if (sim.depth(region) >= stopAt) {
            // Done with this face: lift and let the next call turn the collar.
            working = -1;
            return lifted(nextAfter(sim, region), 0F, aux);
        }
        float force = switch (style) {
            case CAREFUL -> 0.6F;
            case HEAVY -> 1F;
            case STILL -> 0.6F;
        };
        float sweep = switch (style) {
            // An unhurried sweep (peak speed about 1.3 handle widths a second): inside every face's tolerance.
            case CAREFUL -> (float) Math.sin(tick * 0.035) * 0.82F;
            case HEAVY -> (tick / 6) % 2 == 0 ? 0.9F : -0.9F;
            case STILL -> 0F;
        };
        float height = FacetSim.CONTACT_HEIGHT * (1F - force);
        return new WorkSample(SimIO.toSigned12(sweep), SimIO.toUnit12(height), 0, region, aux, 0);
    }

    private float stopAt(FacetParams p, int region) {
        float target = p.target()[region];
        return switch (style) {
            case CAREFUL -> target - p.window() * 0.2F;
            case HEAVY -> target + p.window() * 1.6F;
            case STILL -> target;
        };
    }

    private static int nextAfter(FacetSim sim, int current) {
        for (int i : sim.params().order()) {
            if (i != current && sim.depth(i) < sim.params().target()[i] - sim.params().window()) return i;
        }
        return current;
    }

    private static WorkSample lifted(int region, float sweep, int aux) {
        return new WorkSample(SimIO.toSigned12(sweep), 4095, 0, region, aux, 0);
    }

    /** Whether the bot has nothing left to do (every region reached its window, the quill lifted). */
    public static boolean done(FacetSim sim) {
        return sim.ready() && !sim.contact();
    }

    /** Runs a whole cut, returning the number of steps it took (stops at {@code maxSteps}). */
    public int run(FacetSim sim, int maxSteps) {
        int steps = 0;
        while (!done(sim) && steps < maxSteps) {
            sim.step(next(sim));
            steps++;
        }
        // Let go of the handle at the end.
        for (int i = 0; i < 12; i++) sim.step(lifted(sim.region(), 0F, trim + WorkSample.TRIM_BIAS));
        return steps;
    }
}
