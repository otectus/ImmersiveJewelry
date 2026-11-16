package com.otectus.immersivejewelry.client.compat.ponder;

import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.workstation.sim.FacetParams;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SimIO;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;

/**
 * The demonstrating jeweler's hands: scripted control samples for the stations' own simulations (the same
 * {@link FacetSim}, {@link SettingSim} and {@link SurfaceSim} the server steps), so a demonstration shows what the work
 * really does: careful hands reach every window, heavy hands scratch, overheat and bend. Deterministic: a fresh set of
 * hands is made each time an instruction starts, so a replay or a seek produces the same samples and the same stone.
 * They only ever drive a demonstration's own level; nothing here reaches a real station or the network.
 */
final class DemoHands {
    enum Style { CAREFUL, HEAVY, STILL }

    private final Style style;
    private int tick;
    private boolean cooling;
    private int working = -1;
    private float travel;
    private int gripSocket = -1, gripPoint = -1, rest;

    DemoHands(Style style) {
        this.style = style;
    }

    // ------------------------------------------------------------------------------------------------ the wheel

    WorkSample facet(FacetSim sim) {
        tick++;
        FacetParams p = sim.params();
        // A careful jeweler corrects the dop's turn with the fine trim; heavy hands do not bother.
        int aux = WorkSample.TRIM_BIAS + (style == Style.CAREFUL ? -Math.round(p.dopOffset()) : 0);
        int region = working >= 0 && sim.depth(working) < stopAt(p, working) ? working : sim.recommended();
        if (region < 0) return lifted(sim.region(), aux);
        working = region;
        if (sim.region() != region) return lifted(region, aux);
        if (style == Style.CAREFUL) {
            if (sim.heat() > p.chipThreshold() * 0.6F) cooling = true;
            if (cooling && sim.heat() < p.chipThreshold() * 0.25F) cooling = false;
            if (cooling) return lifted(region, aux);
        }
        if (sim.depth(region) >= stopAt(p, region)) {
            working = -1;
            return lifted(nextAfter(sim, region), aux);
        }
        float force = style == Style.HEAVY ? 1F : 0.6F;
        float sweep = switch (style) {
            case CAREFUL -> (float) Math.sin(tick * 0.035) * 0.82F;
            case HEAVY -> (tick / 6) % 2 == 0 ? 0.9F : -0.9F;
            case STILL -> 0F;
        };
        float height = FacetSim.CONTACT_HEIGHT * (1F - force);
        return new WorkSample(SimIO.toSigned12(sweep), SimIO.toUnit12(height), 0, region, aux, 0);
    }

    /** The stone dipped in the cooling cup. */
    static WorkSample dip(FacetSim sim) {
        return new WorkSample(SimIO.toSigned12(0F), 4095, 0, sim.region(), WorkSample.TRIM_BIAS, WorkSample.FLAG_DIP);
    }

    static WorkSample lifted(FacetSim sim) {
        return lifted(sim.region(), WorkSample.TRIM_BIAS);
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

    private static WorkSample lifted(int region, int aux) {
        return new WorkSample(SimIO.toSigned12(0F), 4095, 0, region, aux, 0);
    }

    // ------------------------------------------------------------------------------------------------ the bench: setting

    WorkSample setting(SettingSim sim) {
        tick++;
        if (rest > 0) {
            rest--;
            return release(sim);
        }
        boolean removal = sim.mode() == SettingSim.Mode.REMOVE;
        int socket = -1, point = -1;
        if (gripSocket >= 0 && sim.params().style() == SettingStyle.PRONG) {
            socket = gripSocket;
            point = gripPoint;
        }
        for (int s = 0; s < sim.params().sockets() && socket < 0; s++) {
            if (!sim.seated(s)) continue;
            int q = sim.recommended(s);
            if (q >= 0) {
                socket = s;
                point = q;
            }
        }
        if (socket < 0) return release(sim);
        if (sim.params().style() == SettingStyle.BEZEL) {
            float theta = (tick * 0.045F) % (float) (Math.PI * 2);
            float push = removal ? 0.7F : style == Style.HEAVY ? 1F : 0.74F;
            if (style == Style.HEAVY) theta = 0.4F;
            int flags = WorkSample.FLAG_CONTACT | (removal ? WorkSample.FLAG_PRY : 0);
            return new WorkSample(SimIO.toUnit12(push), SimIO.toUnit12(theta / (float) (Math.PI * 2)), 0, socket * 16, SettingSim.TOOL_PUSHER, flags);
        }
        if (gripSocket != socket || gripPoint != point) {
            gripSocket = socket;
            gripPoint = point;
            travel = 0F;
        }
        float c = sim.closure(socket, point);
        float tol = sim.params().tolerance();
        boolean enough = removal ? c < SettingSim.OPEN_BELOW * 0.5F
                : style == Style.HEAVY ? c >= 1F + tol * 3.5F || travel >= 1F : c >= 1F + tol * 0.3F;
        if (enough) {
            rest = 3;
            gripSocket = -1;
            return release(sim);
        }
        travel = Math.min(1F, travel + (style == Style.HEAVY ? 0.06F : 0.02F));
        int flags = WorkSample.FLAG_CONTACT | (removal ? WorkSample.FLAG_PRY : 0);
        return new WorkSample(SimIO.toUnit12(travel), 0, 0, socket * 16 + point, SettingSim.TOOL_PLIERS, flags);
    }

    static WorkSample release(SettingSim sim) {
        int tool = sim.params().style() == SettingStyle.BEZEL ? SettingSim.TOOL_PUSHER : SettingSim.TOOL_PLIERS;
        return new WorkSample(0, 0, 0, 0, tool, 0);
    }

    static boolean settled(SettingSim sim) {
        if (sim.gripping()) return false;
        for (int s = 0; s < sim.params().sockets(); s++) {
            if (!sim.seated(s)) continue;
            if (sim.mode() == SettingSim.Mode.REMOVE ? !sim.opened(s) : !sim.secured(s)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ the bench: surface

    WorkSample surface(SurfaceSim sim) {
        tick++;
        int tool = sim.params().mode().tool(sim.phase());
        if (sim.complete()) return new WorkSample(0, 0, 0, 0, tool, 0);
        if (style == Style.STILL) {
            float u = 0.25F + 0.004F * (float) Math.sin(tick * 0.5);
            return new WorkSample(SimIO.toUnit12(u), SimIO.toUnit12(0.5F), SimIO.toUnit12(0.6F), 0, tool, WorkSample.FLAG_CONTACT);
        }
        float u = (tick * 0.011F) % 1F;
        int band = (tick / 91) % 3;
        float v = band == 0 ? 0.15F : band == 1 ? 0.5F : 0.85F;
        return new WorkSample(SimIO.toUnit12(u), SimIO.toUnit12(v), SimIO.toUnit12(0.6F), 0, tool, WorkSample.FLAG_CONTACT);
    }

    static WorkSample lift(SurfaceSim sim) {
        return new WorkSample(0, 0, 0, 0, sim.params().mode().tool(sim.phase()), 0);
    }
}
