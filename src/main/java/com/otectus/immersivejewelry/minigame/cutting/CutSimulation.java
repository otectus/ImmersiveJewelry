package com.otectus.immersivejewelry.minigame.cutting;

import com.otectus.immersivejewelry.minigame.StepClock;

/**
 * The gem-cutting minigame (spec 10), run identically by client and server. One call to {@link #step} advances 20 ms
 * with the input held during that step: where the stone is pressed against the wheel (a point on the magnified
 * stone, 12-bit fixed point) and whether the player is grinding.
 *
 * <p>Rules. Facets are worked one at a time in {@link CutParams#order()}. While grinding, pressure rises over
 * {@code ramp} seconds; the contact point wobbles more under pressure. Grinding inside the active facet's region
 * deepens it (faster near its centre line); grinding elsewhere on the stone scratches whatever is there. Releasing
 * the grind with the facet inside its depth window locks it; grinding past the window overcuts it (locked at once,
 * costing size). Heat builds with pressure and bleeds off between passes; heat past the chip threshold chips the
 * stone (size, a quality cap and accuracy on the active facet). Only {@link StrictMath} is used, so results are
 * bit-identical on every JVM.</p>
 */
public final class CutSimulation {
    public static final int COORD_MAX = 4095;
    public static final float COORD_RANGE = 1.25F;

    /** Things that happened in the last step, for sounds and particles. */
    public static final int EVENT_FACET = 1, EVENT_OVERCUT = 2, EVENT_CHIP = 4, EVENT_SCRATCH = 8, EVENT_GRIND = 16, EVENT_DONE = 32;

    private final CutParams p;
    private final int k;
    private final float[] progress;
    private final float[] base;
    private final boolean[] locked;
    private final float[] scratch;
    private final float[] offCenter;
    private final float[] ground;
    private final float[] chipMark;
    private int active;
    private int step;
    private float pressure;
    private float heat;
    private int chips;
    private float sizeLoss;
    private float tableScratch;
    private boolean lastGrinding;
    private boolean complete;
    private boolean timedOut;
    private float contactX;
    private float contactY;
    private int events;

    public CutSimulation(CutParams params) {
        this.p = params;
        this.k = params.facets();
        progress = new float[k];
        base = new float[k];
        locked = new boolean[k];
        scratch = new float[k];
        offCenter = new float[k];
        ground = new float[k];
        chipMark = new float[k];
    }

    public static float toStone(int fixed) {
        return Math.max(0, Math.min(COORD_MAX, fixed)) / (float) COORD_MAX * (2F * COORD_RANGE) - COORD_RANGE;
    }

    public static int toFixed(float stone) {
        return Math.max(0, Math.min(COORD_MAX, Math.round((stone + COORD_RANGE) / (2F * COORD_RANGE) * COORD_MAX)));
    }

    /** Advances one step. Returns the events of this step (a bit set of {@code EVENT_*}). */
    public int step(int fx, int fy, boolean grinding) {
        events = 0;
        if (complete) return 0;
        float t = step * StepClock.DT;
        float dt = StepClock.DT;
        if (grinding) pressure = Math.min(1F, pressure + dt / p.ramp());
        else pressure = Math.max(0F, pressure - dt / 0.25F);

        float amp = p.drift() * (0.3F + 0.7F * pressure);
        float[] f = p.freqs();
        float[] ph = p.phases();
        float dx = amp * (float) (StrictMath.sin(t * f[0] + ph[0]) + 0.6 * StrictMath.sin(t * f[1] + ph[1])) / 1.6F;
        float dy = amp * (float) (StrictMath.cos(t * f[2] + ph[2]) + 0.6 * StrictMath.cos(t * f[3] + ph[3])) / 1.6F;
        contactX = toStone(fx) + dx;
        contactY = toStone(fy) + dy;

        heat += dt * pressure * pressure * p.heatRate();
        heat -= dt * p.coolRate() * (grinding ? 0.15F : 1F);
        heat = Math.max(0F, Math.min(1.3F, heat));

        int facet = p.order()[active];
        if (grinding && pressure > 0.05F) {
            float r = (float) StrictMath.sqrt(contactX * contactX + contactY * contactY);
            if (r <= 1.02F) {
                float angle = CutParams.wrap((float) StrictMath.atan2(contactY, contactX));
                float rate = p.grindRate() * pressure * dt;
                float d = angularDistance(angle, p.angles()[facet]);
                float w = p.halfWidths()[facet];
                if (d <= w && r >= p.innerRadius()) {
                    float rel = d / w;
                    progress[facet] += rate * (1F - 0.5F * rel * rel);
                    offCenter[facet] += rate * rel;
                    ground[facet] += rate;
                    events |= EVENT_GRIND;
                } else {
                    int other = facetAt(angle, r);
                    if (other >= 0 && other != facet) scratch[other] += rate * 0.6F;
                    else if (other < 0) tableScratch += rate * 0.25F;
                    events |= EVENT_SCRATCH;
                }
            }
            if (heat >= p.chipThreshold()) {
                chips++;
                sizeLoss += 0.05F;
                chipMark[facet] = Math.min(0.6F, chipMark[facet] + 0.25F);
                heat = 0.5F;
                events |= EVENT_CHIP;
            }
        }

        float lo = p.windowCenter()[facet] - p.windowHalf();
        float hi = p.windowCenter()[facet] + p.windowHalf();
        if (progress[facet] > hi) {
            float over = (progress[facet] - hi) / p.windowHalf();
            base[facet] = Math.max(0.05F, 0.4F - over * 0.3F);
            sizeLoss += (progress[facet] - hi) * 0.4F;
            lock(facet);
            events |= EVENT_OVERCUT;
        } else if (!grinding && lastGrinding && progress[facet] >= lo) {
            base[facet] = 1F - 0.5F * Math.abs(progress[facet] - p.windowCenter()[facet]) / p.windowHalf();
            lock(facet);
            events |= EVENT_FACET;
        }
        lastGrinding = grinding;
        step++;
        if (!complete && step >= p.maxSteps()) finishByTimeout();
        return events;
    }

    private void lock(int facet) {
        locked[facet] = true;
        active++;
        if (active >= k) {
            active = k - 1;
            complete = true;
            events |= EVENT_DONE;
        }
    }

    private void finishByTimeout() {
        for (int i = 0; i < k; i++) {
            if (locked[i]) continue;
            float lo = p.windowCenter()[i] - p.windowHalf();
            base[i] = 0.35F * Math.min(1F, progress[i] / lo);
            locked[i] = true;
        }
        timedOut = true;
        complete = true;
        events |= EVENT_DONE;
    }

    /** The facet whose contact region holds the point, or -1 for the table or a gap. */
    private int facetAt(float angle, float r) {
        if (r < p.innerRadius()) return -1;
        for (int i = 0; i < k; i++) {
            if (angularDistance(angle, p.angles()[i]) <= p.halfWidths()[i]) return i;
        }
        return -1;
    }

    static float angularDistance(float a, float b) {
        float d = Math.abs(a - b) % (float) (2 * StrictMath.PI);
        return d > StrictMath.PI ? (float) (2 * StrictMath.PI) - d : d;
    }

    /** Final accuracy of one facet: its lock accuracy, less scratches, off-centre grinding and chips. */
    public float facetAccuracy(int i) {
        float offRatio = ground[i] > 0F ? offCenter[i] / ground[i] : 0F;
        float value = base[i] * (1F - Math.min(0.6F, scratch[i] * 1.5F)) * (1F - 0.4F * offRatio) * (1F - chipMark[i]);
        return Math.max(0F, Math.min(1F, value));
    }

    /** Cut quality 0..1 (meaningful once complete): mean facet accuracy, symmetry, chips and table scratches. */
    public float quality() {
        float sum = 0F;
        float[] acc = new float[k];
        for (int i = 0; i < k; i++) {
            acc[i] = facetAccuracy(i);
            sum += acc[i];
        }
        float mean = sum / k;
        float var = 0F;
        for (float a : acc) var += (a - mean) * (a - mean);
        float symmetry = 1F - 0.5F * (float) StrictMath.sqrt(var / k);
        float chipFactor = (float) StrictMath.pow(0.9, chips);
        return Math.max(0F, Math.min(1F, mean * symmetry * chipFactor * (1F - Math.min(0.3F, tableScratch))));
    }

    public CutParams params() { return p; }
    public boolean isComplete() { return complete; }
    public boolean timedOut() { return timedOut; }
    public int step() { return step; }
    public int activeFacet() { return p.order()[active]; }
    public int lockedCount() { int n = 0; for (boolean b : locked) if (b) n++; return n; }
    public boolean isLocked(int facet) { return locked[facet]; }
    public float progress(int facet) { return progress[facet]; }
    public float scratch(int facet) { return scratch[facet]; }
    public float pressure() { return pressure; }
    public float heat() { return heat; }
    public int chips() { return chips; }
    /** Share of size lost to chips and overcuts, before severity. */
    public float sizeLoss() { return sizeLoss; }
    public float contactX() { return contactX; }
    public float contactY() { return contactY; }
    public int lastEvents() { return events; }
}
