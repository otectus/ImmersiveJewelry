package com.otectus.immersivejewelry;

import com.otectus.immersivejewelry.client.workstation.FinishingSweep;
import com.otectus.immersivejewelry.workstation.sim.SimIO;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinishingSweepTest {
    @Test
    void actualAssistedStrokeFinishesTheSurfaceInsteadOfRetracingOneBand() {
        for (SurfaceParams.Mode mode : SurfaceParams.Mode.values()) {
            SurfaceSim sim = new SurfaceSim(SurfaceParams.create(mode, 42L, 0.6F, 1F, false));
            FinishingSweep sweep = new FinishingSweep();
            float u = 0F, v = 0.5F;
            for (int i = 0; i < 3000 && !sim.complete(); i++) {
                FinishingSweep.Point next = sweep.step(u, v, SurfaceSim.DT);
                u = next.u(); v = next.v();
                sim.step(new WorkSample(SimIO.toUnit12(u), SimIO.toUnit12(v), SimIO.toUnit12(0.6F),
                        0, mode.tool(sim.phase()), WorkSample.FLAG_CONTACT));
            }
            assertTrue(sim.complete(), mode + " remained at " + sim.progress());
        }
    }

    @Test
    void resumingAssistanceStartsAtTheManuallyPlacedTool() {
        FinishingSweep sweep = new FinishingSweep();
        sweep.step(0F, 0.5F, 3F);
        sweep.reset();
        FinishingSweep.Point resumed = sweep.step(0.3F, 0.8F, 0F);
        assertEquals(0.3F, resumed.u(), 0.00001F);
        assertEquals(0.8F, resumed.v(), 0.00001F);
    }
}
