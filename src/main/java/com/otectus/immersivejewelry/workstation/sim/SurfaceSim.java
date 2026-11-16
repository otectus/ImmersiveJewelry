package com.otectus.immersivejewelry.workstation.sim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Finishing and repair as surface coverage, step by fixed 20 ms step on both sides. The tool's footprint covers the
 * cells under it; only newly covered surface counts (a cell saturates at 1), so rubbing one spot never finishes the
 * piece and nothing depends on how often input arrives. Polish needs the cloth to keep moving, burnish and repair
 * count travel along the piece (smooth passes), a patina is dabbed on anywhere and then its high points are wiped.
 */
public final class SurfaceSim {
    public static final int VERSION = 1;
    public static final float DT = FacetSim.DT;
    public static final float MAX_MOVE_STEP = 0.06F;
    private static final float FOOT_U = 1.6F, FOOT_V = 1.2F;

    public static final int EVENT_RUB = 1, EVENT_PHASE = 2, EVENT_DONE = 4, EVENT_TOUCH = 8, EVENT_LIFT = 16;

    private final SurfaceParams p;
    private final float[] cover = new float[SurfaceParams.CELLS];
    /** The first phase's coverage (the patina), kept for the look once the wipe starts. */
    private final float[] under = new float[SurfaceParams.CELLS];
    private int phase;
    private boolean complete;
    private float u;
    private float v = 0.5F;
    private boolean contact;
    private int steps;
    private int events;

    public SurfaceSim(SurfaceParams params) {
        this.p = params;
    }

    public int step(WorkSample in) {
        events = 0;
        float lastU = u;
        float lastV = v;
        float wantU = SimIO.unit12(in.a());
        // Round the piece the shorter way.
        float du = wantU - u;
        if (du > 0.5F) du -= 1F;
        if (du < -0.5F) du += 1F;
        du = SimIO.clamp(du, -MAX_MOVE_STEP, MAX_MOVE_STEP);
        u = u + du;
        if (u < 0F) u += 1F;
        if (u >= 1F) u -= 1F;
        v = SimIO.approach(v, SimIO.unit12(in.b()), MAX_MOVE_STEP);
        float pressure = SimIO.unit12(in.c());
        boolean was = contact;
        contact = !complete && in.has(WorkSample.FLAG_CONTACT) && in.aux() == p.mode().tool(phase) && pressure > 0.08F;
        if (contact && !was) events |= EVENT_TOUCH;
        if (!contact && was) events |= EVENT_LIFT;
        if (contact) rub(Math.abs(du) * SurfaceParams.U, Math.abs(v - lastV) * SurfaceParams.V, pressure);
        steps++;
        return events;
    }

    private void rub(float alongCells, float acrossCells, float pressure) {
        float press = SimIO.clamp(pressure / 0.3F, 0F, 1F) * (pressure > 0.92F ? 0.85F : 1F);
        float gain = switch (p.mode()) {
            case POLISH -> p.rate() * DT * press * (alongCells + acrossCells > 0.02F ? 1F : 0.2F);
            case BURNISH, REPAIR -> p.stroke() * alongCells * press;
            case PATINA -> phase == 0 ? p.rate() * DT * press : p.rate() * DT * press * (alongCells + acrossCells > 0.02F ? 1F : 0.2F);
        };
        if (gain <= 0F) return;
        float cu = u * SurfaceParams.U;
        float cv = v * (SurfaceParams.V - 1);
        boolean changed = false;
        for (int iu = (int) Math.floor(cu - FOOT_U); iu <= (int) Math.ceil(cu + FOOT_U); iu++) {
            int wu = Math.floorMod(iu, SurfaceParams.U);
            float dU = Math.abs(iu - cu);
            if (dU >= FOOT_U) continue;
            for (int iv = Math.max(0, (int) Math.floor(cv - FOOT_V)); iv <= Math.min(SurfaceParams.V - 1, (int) Math.ceil(cv + FOOT_V)); iv++) {
                float dV = Math.abs(iv - cv);
                if (dV >= FOOT_V) continue;
                int i = wu * SurfaceParams.V + iv;
                if (!counts(i)) continue;
                float w = (1F - dU / FOOT_U) * (1F - dV / FOOT_V);
                float before = cover[i];
                cover[i] = Math.min(1F, cover[i] + gain * w);
                changed |= cover[i] != before;
            }
        }
        if (changed) events |= EVENT_RUB;
        if (progress() >= p.threshold()) {
            if (phase + 1 < p.mode().phases()) {
                System.arraycopy(cover, 0, under, 0, cover.length);
                java.util.Arrays.fill(cover, 0F);
                phase++;
                contact = false;
                events |= EVENT_PHASE;
            } else {
                complete = true;
                contact = false;
                events |= EVENT_DONE;
            }
        }
    }

    /** Whether cell {@code i} is part of the current phase's work. */
    public boolean counts(int i) {
        if (!p.target()[i]) return false;
        return p.mode() != SurfaceParams.Mode.PATINA || phase == 0 || p.high()[i];
    }

    /** Share of the current phase's cells covered, 0..1. */
    public float progress() {
        float sum = 0F;
        int n = 0;
        for (int i = 0; i < SurfaceParams.CELLS; i++) {
            if (!counts(i)) continue;
            sum += cover[i];
            n++;
        }
        return n == 0 ? 1F : sum / n;
    }

    /** How even the finish came out, 0..1 (cosmetic only). */
    public float evenness() {
        float mean = 0F;
        int n = 0;
        for (int i = 0; i < SurfaceParams.CELLS; i++) if (counts(i)) { mean += cover[i]; n++; }
        if (n == 0) return 1F;
        mean /= n;
        float var = 0F;
        for (int i = 0; i < SurfaceParams.CELLS; i++) if (counts(i)) var += (cover[i] - mean) * (cover[i] - mean);
        return SimIO.clamp(1F - 2F * (float) StrictMath.sqrt(var / n), 0F, 1F);
    }

    public SurfaceParams params() { return p; }
    public float cover(int i) { return cover[i]; }
    public float under(int i) { return under[i]; }
    public int phase() { return phase; }
    public boolean complete() { return complete; }
    public float u() { return u; }
    public float v() { return v; }
    public boolean contact() { return contact; }
    public boolean started() { return steps > 0 && (phase > 0 || progress() > 0F); }
    public int steps() { return steps; }
    public int lastEvents() { return events; }

    public void neutral() {
        contact = false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("V", VERSION);
        SimIO.putFloats(tag, "Cover", cover);
        SimIO.putFloats(tag, "Under", under);
        tag.putInt("Phase", phase);
        tag.putBoolean("Complete", complete);
        tag.putFloat("U", u);
        tag.putFloat("Vpos", v);
        tag.putBoolean("Contact", contact);
        tag.putInt("Steps", steps);
        return tag;
    }

    public static SurfaceSim load(SurfaceParams params, CompoundTag tag) {
        SurfaceSim sim = new SurfaceSim(params);
        System.arraycopy(SimIO.getFloats(tag, "Cover", SurfaceParams.CELLS, 0F, 0F, 1F), 0, sim.cover, 0, SurfaceParams.CELLS);
        System.arraycopy(SimIO.getFloats(tag, "Under", SurfaceParams.CELLS, 0F, 0F, 1F), 0, sim.under, 0, SurfaceParams.CELLS);
        sim.phase = SimIO.clamp(tag.getInt("Phase"), 0, params.mode().phases() - 1);
        sim.complete = tag.getBoolean("Complete");
        sim.u = SimIO.finite(tag.getFloat("U"), 0F, 0.9999F);
        sim.v = tag.contains("Vpos") ? SimIO.finite(tag.getFloat("Vpos"), 0F, 1F) : 0.5F;
        sim.contact = tag.getBoolean("Contact");
        sim.steps = Math.max(0, tag.getInt("Steps"));
        return sim;
    }

    public SurfaceSim copy() {
        return load(p, save());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }
}
