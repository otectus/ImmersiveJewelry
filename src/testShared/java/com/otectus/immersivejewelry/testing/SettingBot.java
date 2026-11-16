package com.otectus.immersivejewelry.testing;

import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SimIO;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;

/**
 * A reference player at the bench's setting work, shared by the tests. Prongs: grips each open prong in the recommended
 * opposite order and squeezes until it meets the stone (careful) or well past it (heavy), then lets go. Bezels: sweeps
 * the pusher round the rim at a moderate push (careful) or jabs one spot (heavy). Removal: pries each point open. Only
 * control samples, read from what the work looks like.
 */
public final class SettingBot {
    public enum Style { CAREFUL, HEAVY }

    private final Style style;
    private int tick;
    private float travel;
    private int gripSocket = -1;
    private int gripPoint = -1;
    private int rest;

    public SettingBot(Style style) {
        this.style = style;
    }

    public WorkSample next(SettingSim sim) {
        tick++;
        if (rest > 0) {
            rest--;
            return release(sim);
        }
        boolean removal = sim.mode() == SettingSim.Mode.REMOVE;
        int socket = -1;
        int point = -1;
        if (gripSocket >= 0 && sim.params().style() == SettingStyle.PRONG) {
            // Stay on the point in hand until it is done: a worker does not let go half way.
            socket = gripSocket;
            point = gripPoint;
        }
        for (int s = 0; s < sim.params().sockets() && socket < 0; s++) {
            if (!sim.seated(s)) continue;
            int q = sim.recommended(s);
            if (q < 0 && style == Style.CAREFUL && !removal && sim.params().style() == SettingStyle.BEZEL && !rimEven(sim, s)) q = 0;
            if (q >= 0) {
                socket = s;
                point = q;
            }
        }
        if (socket < 0) return release(sim);
        if (sim.params().style() == SettingStyle.BEZEL) return bezel(sim, socket, removal);
        if (gripSocket != socket || gripPoint != point) {
            gripSocket = socket;
            gripPoint = point;
            travel = 0F;
        }
        float c = sim.closure(socket, point);
        float tol = sim.params().tolerance();
        boolean enough = removal ? c < SettingSim.OPEN_BELOW * 0.5F
                : style == Style.CAREFUL ? c >= 1F + tol * 0.3F : c >= 1F + tol * 3.5F || travel >= 1F;
        if (enough) {
            rest = 3;
            gripSocket = -1;
            return release(sim);
        }
        travel = Math.min(1F, travel + (style == Style.CAREFUL ? 0.02F : 0.06F));
        int flags = WorkSample.FLAG_CONTACT | (removal ? WorkSample.FLAG_PRY : 0);
        return new WorkSample(SimIO.toUnit12(travel), 0, 0, socket * 16 + point, SettingSim.TOOL_PLIERS, flags);
    }

    private WorkSample bezel(SettingSim sim, int socket, boolean removal) {
        float theta = (tick * 0.045F) % (float) (Math.PI * 2);
        float push = style == Style.CAREFUL ? 0.74F : 1F;
        if (style == Style.HEAVY) theta = 0.4F;
        if (removal) push = 0.7F;
        int flags = WorkSample.FLAG_CONTACT | (removal ? WorkSample.FLAG_PRY : 0);
        return new WorkSample(SimIO.toUnit12(push), SimIO.toUnit12(theta / (float) (Math.PI * 2)), 0, socket * 16, SettingSim.TOOL_PUSHER, flags);
    }

    private static boolean rimEven(SettingSim sim, int s) {
        float tol = sim.params().tolerance();
        for (int q = 0; q < sim.params().points(); q++) if (sim.closure(s, q) < 1F - tol * 0.5F) return false;
        return true;
    }

    private static WorkSample release(SettingSim sim) {
        int tool = sim.params().style() == SettingStyle.BEZEL ? SettingSim.TOOL_PUSHER : SettingSim.TOOL_PLIERS;
        return new WorkSample(0, 0, 0, 0, tool, 0);
    }

    /** Runs until every stone is secured (or opened, in removal) or {@code maxSteps}; returns the steps taken. */
    public int run(SettingSim sim, int maxSteps) {
        int steps = 0;
        while (steps < maxSteps && !(style == Style.CAREFUL ? even(sim) : finished(sim))) {
            sim.step(next(sim));
            steps++;
        }
        for (int i = 0; i < 5; i++) sim.step(release(sim));
        return steps;
    }

    /** A careful worker's standard: every point not just held but close to the stone (within half the band). */
    public static boolean even(SettingSim sim) {
        if (!finished(sim) || sim.mode() == SettingSim.Mode.REMOVE) return finished(sim);
        float tol = sim.params().tolerance();
        for (int s = 0; s < sim.params().sockets(); s++) {
            if (!sim.seated(s)) continue;
            for (int q = 0; q < sim.params().points(); q++) if (sim.closure(s, q) < 1F - tol * 0.5F) return false;
        }
        return true;
    }

    public static boolean finished(SettingSim sim) {
        if (sim.gripping()) return false;
        for (int s = 0; s < sim.params().sockets(); s++) {
            if (!sim.seated(s)) continue;
            if (sim.mode() == SettingSim.Mode.REMOVE ? !sim.opened(s) : !sim.secured(s)) return false;
        }
        return true;
    }
}
