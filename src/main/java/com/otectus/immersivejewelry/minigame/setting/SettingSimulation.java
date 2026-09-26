package com.otectus.immersivejewelry.minigame.setting;

import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.minigame.StepClock;

import java.util.EnumSet;

/**
 * The stone-setting minigame (spec 12), identical on client and server. Points (prongs or bezel push points) are
 * worked in a balanced order. Holding squeezes: pressure climbs at {@code fillRate}; releasing sets the point at that
 * pressure. The target is the pressure already applied across the stone (so opposite points match: balance), or a
 * base target for the first of a pair. Released inside the window: secured. Over: a bent prong (and a brittle stone
 * may chip). Under: loose; one more squeeze may continue from there. In removal mode the targets are low: lifting
 * gently. {@link StrictMath} only.
 */
public final class SettingSimulation {
    public static final int EVENT_SET = 1, EVENT_OVER = 2, EVENT_LOOSE = 4, EVENT_CHIP = 8, EVENT_DONE = 16, EVENT_WINDOW = 32;
    private static final int MAX_PRESSES = 2;

    private final SettingParams p;
    private final int n;
    private final float[] pressure;
    private final float[] accuracy;
    private final boolean[] done;
    private final int[] presses;
    private final EnumSet<SettingDefect> defects = EnumSet.noneOf(SettingDefect.class);
    private int active;
    private int step;
    private boolean holding;
    private float current;
    private boolean inWindow;
    private boolean complete;
    private boolean timedOut;
    private boolean chipped;
    private int events;

    public SettingSimulation(SettingParams params) {
        this.p = params;
        this.n = params.points();
        pressure = new float[n];
        accuracy = new float[n];
        done = new boolean[n];
        presses = new int[n];
    }

    /** One 20 ms step with the squeeze held or not. Returns this step's events. */
    public int step(boolean squeezing) {
        events = 0;
        if (complete) return 0;
        int point = p.order()[active];
        if (squeezing && !holding) {
            holding = true;
            current = presses[point] > 0 ? pressure[point] : 0F;
        }
        if (holding && squeezing) {
            current = Math.min(1.3F, current + p.fillRate() * StepClock.DT * (1F + 0.35F * current));
            boolean now = Math.abs(current - target(point)) <= p.window();
            if (now && !inWindow) events |= EVENT_WINDOW;
            inWindow = now;
        }
        if (holding && !squeezing) {
            holding = false;
            inWindow = false;
            release(point);
        }
        step++;
        if (!complete && step >= p.maxSteps()) finishByTimeout();
        return events;
    }

    private void release(int point) {
        presses[point]++;
        pressure[point] = current;
        float target = target(point);
        float diff = current - target;
        if (Math.abs(diff) <= p.window()) {
            accuracy[point] = 1F - 0.5F * Math.abs(diff) / p.window();
            advance(point);
            events |= EVENT_SET;
        } else if (diff > 0F) {
            accuracy[point] = Math.max(0.1F, 0.4F - (diff - p.window()) * 1.5F);
            defects.add(p.removal() ? SettingDefect.SCRATCHED_METAL : SettingDefect.BENT_PRONG);
            if (diff > p.window() * 3F) defects.add(SettingDefect.SCRATCHED_METAL);
            if (current >= p.chipAbove()) {
                chipped = true;
                defects.add(SettingDefect.CHIPPED_STONE);
                events |= EVENT_CHIP;
            }
            advance(point);
            events |= EVENT_OVER;
        } else if (presses[point] >= MAX_PRESSES) {
            accuracy[point] = 0.45F * Math.max(0F, current / target);
            defects.add(SettingDefect.LOOSE);
            advance(point);
            events |= EVENT_LOOSE;
        } else {
            events |= EVENT_LOOSE; // one more squeeze continues from here
        }
    }

    private void advance(int point) {
        done[point] = true;
        active++;
        if (active >= n) {
            active = n - 1;
            complete = true;
            if (alignment() < 0.7F && !p.removal()) defects.add(SettingDefect.CROOKED);
            events |= EVENT_DONE;
        }
    }

    private void finishByTimeout() {
        for (int i = 0; i < n; i++) {
            if (done[i]) continue;
            accuracy[i] = 0.3F * Math.max(0F, pressure[i] / target(i));
            done[i] = true;
            defects.add(SettingDefect.LOOSE);
        }
        timedOut = true;
        complete = true;
        if (alignment() < 0.7F && !p.removal()) defects.add(SettingDefect.CROOKED);
        events |= EVENT_DONE;
    }

    /** The pressure a point should be released at: its opposite's, once set, else the base target. */
    public float target(int point) {
        int opp = p.opposite()[point];
        float t = opp >= 0 && done[opp] && !p.removal() ? pressure[opp] : p.base() + p.jitter()[point];
        return Math.max(0.2F, Math.min(1.0F, t));
    }

    /**
     * How level the stones sit: 1 when pressure is balanced around every stone. Uses the vector sum of each stone's
     * point pressures around their mean.
     */
    public float alignment() {
        float worst = 0F;
        for (int s = 0; s < p.stones(); s++) {
            float mean = 0F;
            int count = 0;
            for (int i = 0; i < n; i++) if (p.stoneOf()[i] == s && done[i]) { mean += pressure[i]; count++; }
            if (count == 0) continue;
            mean /= count;
            float x = 0F, y = 0F;
            for (int i = 0; i < n; i++) {
                if (p.stoneOf()[i] != s || !done[i]) continue;
                float w = pressure[i] - mean;
                x += w * (float) StrictMath.cos(p.angles()[i]);
                y += w * (float) StrictMath.sin(p.angles()[i]);
            }
            worst = Math.max(worst, (float) StrictMath.sqrt(x * x + y * y) / count);
        }
        return Math.max(0F, 1F - worst * 4F);
    }

    /** Quality 0..1 of the whole setting (or removal). */
    public float quality() {
        float sum = 0F;
        for (int i = 0; i < n; i++) sum += accuracy[i];
        float mean = sum / n;
        float q = mean * (0.7F + 0.3F * alignment());
        if (chipped) q *= 0.85F;
        return Math.max(0F, Math.min(1F, q));
    }

    public SettingParams params() { return p; }
    public boolean isComplete() { return complete; }
    public boolean timedOut() { return timedOut; }
    public boolean chipped() { return chipped; }
    public int step() { return step; }
    public int activePoint() { return p.order()[active]; }
    public boolean isDone(int point) { return done[point]; }
    public float pressure(int point) { return pressure[point]; }
    public float accuracy(int point) { return accuracy[point]; }
    public int presses(int point) { return presses[point]; }
    public float current() { return holding ? current : 0F; }
    public boolean holding() { return holding; }
    public int doneCount() { int c = 0; for (boolean b : done) if (b) c++; return c; }
    public EnumSet<SettingDefect> defects() { return EnumSet.copyOf(defects); }
    public int lastEvents() { return events; }
}
