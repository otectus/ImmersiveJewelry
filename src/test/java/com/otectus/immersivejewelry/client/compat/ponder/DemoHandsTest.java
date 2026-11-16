package com.otectus.immersivejewelry.client.compat.ponder;

import com.otectus.immersivejewelry.data.cut.CutPattern;
import com.otectus.immersivejewelry.workstation.sim.FacetParams;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demonstrations' hands are deterministic (a replay or a seek rebuilds the same stone) and drive the real simulation
 * to the outcomes the captions describe: careful hands finish a cut cleanly, heavy hands scratch and overheat it.
 */
class DemoHandsTest {
    private static FacetParams params() {
        return FacetParams.create(new FacetParams.Inputs(6, CutPattern.RADIAL, 0.8F, 0.7F, 7F, 0.4F, 0.15F, 1.2F, 1.12F, 1.15F, 1.2F, 1.1F, 1F,
                false, false, FacetParams.Tuning.DEFAULT), 42L);
    }

    private static FacetSim run(DemoHands.Style style, int steps) {
        FacetSim sim = new FacetSim(params());
        DemoHands hands = new DemoHands(style);
        for (int i = 0; i < steps; i++) sim.step(hands.facet(sim));
        return sim;
    }

    @Test
    void replayingAStretchRebuildsTheSameStone() {
        FacetSim a = run(DemoHands.Style.CAREFUL, 1500);
        FacetSim b = run(DemoHands.Style.CAREFUL, 1500);
        assertEquals(a.save(), b.save(), "two replays of the same stretch give the same work");
    }

    @Test
    void carefulHandsFinishACutAndHeavyHandsDamageIt() {
        FacetSim careful = run(DemoHands.Style.CAREFUL, 20_000);
        assertTrue(careful.ready(), "careful hands reach every window");
        FacetSim heavy = run(DemoHands.Style.HEAVY, 1500);
        float scratches = 0F;
        for (int i = 0; i < heavy.params().regions(); i++) scratches += heavy.scratch(i) + heavy.rock(i);
        assertTrue(scratches > 0F || heavy.chips() > 0, "heavy hands leave marks");
        assertTrue(careful.quality() > heavy.quality(), "and a lower quality");
    }

    @Test
    void depthsAreSaved() {
        FacetSim sim = run(DemoHands.Style.CAREFUL, 500);
        float[] depth = new float[sim.params().regions()];
        for (int i = 0; i < depth.length; i++) depth[i] = sim.depth(i);
        FacetSim again = run(DemoHands.Style.CAREFUL, 500);
        float[] other = new float[depth.length];
        for (int i = 0; i < other.length; i++) other[i] = again.depth(i);
        assertArrayEquals(depth, other);
    }
}
