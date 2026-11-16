package com.otectus.immersivejewelry.workstation.sim;

import com.otectus.immersivejewelry.data.cut.CutPattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Grinding a stone on the lap, step by fixed 20 ms step, identically on the client (prediction) and the server
 * (authority). There is no clock: nothing happens without a step of input, so reading, inspecting or leaving never
 * costs anything, and a silent client simply pauses.
 *
 * <p>The quill: its index collar turns the stone so that one region faces the lap ({@code target}); the handle lowers
 * it. Below half height the stone touches the lap and the travel past that point is the force. Only a validated
 * contact removes material, and only from the region facing the lap: lifting stops it at once. Force inside the
 * comfortable window bites; pressing harder cuts no faster but heats the stone and scratches the face. Moving the
 * handle sideways sweeps the stone across the lap, which keeps a face flat (domes need the sweep to round at all);
 * jerky sideways motion rocks the stone and rounds its edges. Heat bleeds off with the quill lifted, faster in the
 * basin; heat past the stone's threshold chips the face in contact. Regions are finished by depth: a window around
 * the planned depth, a soft cue before it, overcut past it (costing size). The cut ends only when the player
 * finishes it, never by time.</p>
 *
 * <p>Handle motion is limited per step (a hand cannot teleport a quill), the collar refuses to turn with the stone
 * on the lap, and every value is clamped, so the server's replay of a client's samples is always physically
 * plausible. Arithmetic is float and {@link StrictMath} only: bit-identical on every JVM.</p>
 */
public final class FacetSim {
    public static final int VERSION = 1;
    public static final float DT = 0.02F;
    public static final float CONTACT_HEIGHT = 0.5F;
    public static final float MIN_FORCE = 0.03F;
    public static final float MAX_SWEEP_STEP = 0.09F;
    public static final float MAX_HEIGHT_STEP = 0.12F;
    public static final float MAX_DEPTH = 1.4F;
    /** How far before a region's window the sound and the guide start to change. */
    public static final float NEAR_MARGIN = 0.12F;

    public static final int EVENT_GRIND = 1, EVENT_NEAR = 2, EVENT_ENTER = 4, EVENT_OVER = 8, EVENT_CHIP = 16, EVENT_HOT = 32,
            EVENT_SCRATCH = 64, EVENT_ROCK = 128, EVENT_TOUCH = 256, EVENT_LIFT = 512, EVENT_DIP = 1024, EVENT_INDEX = 2048;

    private final FacetParams p;
    private final int k;
    private final int b;
    private final float[] depth;
    private final float[] bins;
    private final float[] scratch;
    private final float[] rock;
    private final float[] chip;
    private final float[] flat;
    private final float[] azSum;
    private final float[] azWeight;
    private float heat;
    private int chips;
    private float sizeLoss;
    private float sweep;
    private float height = 1F;
    private int region;
    private int trim;
    private boolean contact;
    private boolean hot;
    private boolean touched;
    private int steps;
    private int events;

    public FacetSim(FacetParams params) {
        this.p = params;
        this.k = params.regions();
        this.b = params.bins();
        depth = new float[k];
        bins = new float[k * b];
        scratch = new float[k];
        rock = new float[k];
        chip = new float[k];
        flat = new float[k];
        azSum = new float[k];
        azWeight = new float[k];
        region = params.order()[0];
    }

    /** Advances one step. Returns this step's events, a bit set of {@code EVENT_*}. */
    public int step(WorkSample in) {
        events = 0;
        float lastSweep = sweep;
        sweep = SimIO.approach(sweep, SimIO.signed12(in.a()), MAX_SWEEP_STEP);
        height = SimIO.approach(height, SimIO.unit12(in.b()), MAX_HEIGHT_STEP);
        int wantRegion = in.target() < k ? in.target() : region;
        int wantTrim = SimIO.clamp(in.aux() - WorkSample.TRIM_BIAS, -FacetParams.MAX_TRIM, FacetParams.MAX_TRIM);
        // The collar turns only with the stone off the lap.
        if ((wantRegion != region || wantTrim != trim) && !contact) {
            region = wantRegion;
            trim = wantTrim;
            events |= EVENT_INDEX;
        }
        boolean dip = in.has(WorkSample.FLAG_DIP);
        boolean was = contact;
        float force = 0F;
        contact = false;
        if (!dip && height < CONTACT_HEIGHT && (events & EVENT_INDEX) == 0) {
            force = (CONTACT_HEIGHT - height) / CONTACT_HEIGHT;
            contact = force >= MIN_FORCE;
        }
        if (contact && !was) events |= EVENT_TOUCH;
        if (!contact && was) events |= EVENT_LIFT;

        if (contact) {
            grind(force, Math.abs(sweep - lastSweep) / DT);
            heat += DT * p.heatRate() * force * force * (force > p.forceHigh() ? 2.2F : 1F);
            heat -= DT * p.coolRate() * 0.2F;
        } else {
            heat -= DT * p.coolRate() * (dip ? 4F : 1.6F);
            if (dip) events |= EVENT_DIP;
        }
        heat = SimIO.clamp(heat, 0F, 1.3F);
        float hotAt = p.chipThreshold() * 0.72F;
        if (!hot && heat >= hotAt) {
            hot = true;
            events |= EVENT_HOT;
        } else if (hot && heat < hotAt * 0.8F) {
            hot = false;
        }
        if (contact && heat >= p.chipThreshold()) {
            chips++;
            chip[region] = Math.min(0.6F, chip[region] + 0.2F);
            sizeLoss += 0.04F;
            heat = 0.5F;
            events |= EVENT_CHIP;
        }
        steps++;
        return events;
    }

    private void grind(float force, float speed) {
        int r = region;
        touched = true;
        float eff = Math.min(force, p.forceHigh()) / p.forceHigh();
        if (force < p.forceLow()) eff *= 0.5F + 0.5F * force / p.forceLow();
        float before = depth[r];
        depth[r] = Math.min(MAX_DEPTH, depth[r] + p.rate() * eff * DT);
        float removed = depth[r] - before;
        // The face is covered by a comfortable sweep: the outer eighth of the handle's range adds nothing new.
        int bin = SimIO.clamp((int) ((sweep * 1.15F + 1F) * 0.5F * b), 0, b - 1);
        bins[r * b + bin] += removed;
        float az = Math.abs(p.dopOffset() + trim);
        azSum[r] += az * removed;
        azWeight[r] += removed;
        if (p.pattern() == CutPattern.DOME) {
            if (speed < p.domeSpeed()) flat[r] += removed;
        } else if (speed > p.sweepTolerance()) {
            rock[r] = Math.min(1F, rock[r] + (speed - p.sweepTolerance()) / p.sweepTolerance() * force * DT * 0.25F);
            events |= EVENT_ROCK;
        }
        if (force > p.forceHigh() + 0.12F) {
            scratch[r] = Math.min(1F, scratch[r] + (force - p.forceHigh() - 0.12F) * DT * 1.2F);
            events |= EVENT_SCRATCH;
        }
        float c = p.target()[r];
        float w = p.window();
        if (before < c - w - NEAR_MARGIN && depth[r] >= c - w - NEAR_MARGIN) events |= EVENT_NEAR;
        if (before < c - w && depth[r] >= c - w) events |= EVENT_ENTER;
        if (depth[r] > c + w) {
            sizeLoss += (depth[r] - Math.max(before, c + w)) * 0.4F;
            if (before <= c + w) events |= EVENT_OVER;
        }
        events |= EVENT_GRIND;
    }

    // ------------------------------------------------------------------------------------------------ evaluation

    /** How well one region came out, 0..1: depth, flatness, rocking, scratches, chips and the dop's turn. */
    public float regionAccuracy(int i) {
        float c = p.target()[i];
        float w = p.window();
        float d = depth[i];
        float base;
        if (d < c - w) {
            base = 0.35F * SimIO.clamp(d / (c - w), 0F, 1F);
        } else if (d <= c + w) {
            float x = (d - c) / w;
            base = 1F - 0.3F * x * x;
        } else {
            base = 0.7F - 0.6F * Math.min(1F, (d - c - w) / (2F * w));
        }
        float flatness = 1F - Math.min(p.pattern() == CutPattern.DOME ? 0.4F : 0.3F,
                (p.pattern() == CutPattern.DOME ? 0.3F : 0.18F) * unevenness(i));
        float spots = p.pattern() == CutPattern.DOME ? 1F - Math.min(0.4F, flat[i] / Math.max(0.1F, d)) : 1F;
        float azimuth = 1F - Math.min(0.12F, Math.max(0F, azimuthError(i) - p.azimuthTolerance()) * 0.06F);
        float value = base * flatness * spots * (1F - Math.min(0.5F, rock[i])) * (1F - Math.min(0.6F, scratch[i] * 1.5F))
                * (1F - chip[i]) * azimuth;
        return SimIO.clamp(value, 0F, 1F);
    }

    /** Coefficient of variation of the removal across the face (0 = perfectly even). */
    public float unevenness(int i) {
        float sum = 0F;
        for (int j = 0; j < b; j++) sum += bins[i * b + j];
        if (sum <= 1.0E-4F) return 0F;
        float mean = sum / b;
        float var = 0F;
        for (int j = 0; j < b; j++) {
            float d = bins[i * b + j] - mean;
            var += d * d;
        }
        return (float) StrictMath.sqrt(var / b) / mean;
    }

    /** The removal-weighted error of the dop's turn while region {@code i} was ground, in trim notches. */
    public float azimuthError(int i) {
        return azWeight[i] > 1.0E-4F ? azSum[i] / azWeight[i] : 0F;
    }

    /** Cut quality 0..1: weighted region accuracy, symmetry and chips. */
    public float quality() {
        float sum = 0F;
        float weights = 0F;
        float[] acc = new float[k];
        for (int i = 0; i < k; i++) {
            acc[i] = regionAccuracy(i);
            sum += acc[i] * p.weight()[i];
            weights += p.weight()[i];
        }
        float mean = sum / Math.max(1.0E-4F, weights);
        float var = 0F;
        for (float a : acc) var += (a - mean) * (a - mean);
        float symmetry = 1F - 0.5F * (float) StrictMath.sqrt(var / k);
        if (p.pattern() == CutPattern.RADIAL && k % 2 == 0) {
            float diff = 0F;
            for (int i = 0; i < k / 2; i++) diff += Math.abs(depth[i] - depth[i + k / 2]);
            symmetry *= 1F - Math.min(0.15F, diff / (k / 2F) * 0.8F);
        }
        float chipFactor = (float) StrictMath.pow(0.9, chips);
        return SimIO.clamp(mean * symmetry * chipFactor, 0F, 1F);
    }

    /** Every region reached its window: the cut can be finished without accepting a rough result. */
    public boolean ready() {
        for (int i = 0; i < k; i++) if (depth[i] < p.target()[i] - p.window()) return false;
        return true;
    }

    public boolean inWindow(int i) {
        return depth[i] >= p.target()[i] - p.window() && depth[i] <= p.target()[i] + p.window();
    }

    public boolean overcut(int i) {
        return depth[i] > p.target()[i] + p.window();
    }

    /** The next region in the recommended order that has not reached its window, or -1. */
    public int recommended() {
        for (int i : p.order()) if (depth[i] < p.target()[i] - p.window()) return i;
        return -1;
    }

    /** Share of the planned removal done over all regions, 0..1 (for display only). */
    public float overallProgress() {
        float sum = 0F;
        for (int i = 0; i < k; i++) sum += Math.min(1F, depth[i] / p.target()[i]);
        return sum / k;
    }

    // ------------------------------------------------------------------------------------------------ state

    public FacetParams params() { return p; }
    public float depth(int i) { return depth[i]; }
    public float scratch(int i) { return scratch[i]; }
    public float rock(int i) { return rock[i]; }
    public float chip(int i) { return chip[i]; }
    public float flatSpot(int i) { return flat[i]; }
    public float heat() { return heat; }
    public boolean hot() { return hot; }
    public int chips() { return chips; }
    /** Share of the stone's size lost to chips and overcutting, before severity. */
    public float sizeLoss() { return sizeLoss; }
    public float sweep() { return sweep; }
    public float height() { return height; }
    public int region() { return region; }
    public int trim() { return trim; }
    public boolean contact() { return contact; }
    /** Whether any validated contact happened: from then on the stone has changed. */
    public boolean touched() { return touched; }
    public int steps() { return steps; }
    public int lastEvents() { return events; }

    /** The force of the current contact, 0..1. */
    public float force() {
        return contact ? (CONTACT_HEIGHT - height) / CONTACT_HEIGHT : 0F;
    }

    /** Lifts the quill off the lap at once (lease lost, window unfocused, damage): no grinding continues. */
    public void neutral() {
        height = 1F;
        contact = false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("V", VERSION);
        SimIO.putFloats(tag, "Depth", depth);
        SimIO.putFloats(tag, "Bins", bins);
        SimIO.putFloats(tag, "Scratch", scratch);
        SimIO.putFloats(tag, "Rock", rock);
        SimIO.putFloats(tag, "Chip", chip);
        SimIO.putFloats(tag, "Flat", flat);
        SimIO.putFloats(tag, "AzSum", azSum);
        SimIO.putFloats(tag, "AzWeight", azWeight);
        tag.putFloat("Heat", heat);
        tag.putInt("Chips", chips);
        tag.putFloat("SizeLoss", sizeLoss);
        tag.putFloat("Sweep", sweep);
        tag.putFloat("Height", height);
        tag.putInt("Region", region);
        tag.putInt("Trim", trim);
        tag.putBoolean("Contact", contact);
        tag.putBoolean("Hot", hot);
        tag.putBoolean("Touched", touched);
        tag.putInt("Steps", steps);
        return tag;
    }

    /** A simulation with {@code params} in the state {@link #save()} wrote. */
    public static FacetSim load(FacetParams params, CompoundTag tag) {
        FacetSim sim = new FacetSim(params);
        int k = sim.k;
        System.arraycopy(SimIO.getFloats(tag, "Depth", k, 0F, 0F, MAX_DEPTH), 0, sim.depth, 0, k);
        System.arraycopy(SimIO.getFloats(tag, "Bins", k * sim.b, 0F, 0F, MAX_DEPTH), 0, sim.bins, 0, k * sim.b);
        System.arraycopy(SimIO.getFloats(tag, "Scratch", k, 0F, 0F, 1F), 0, sim.scratch, 0, k);
        System.arraycopy(SimIO.getFloats(tag, "Rock", k, 0F, 0F, 1F), 0, sim.rock, 0, k);
        System.arraycopy(SimIO.getFloats(tag, "Chip", k, 0F, 0F, 0.6F), 0, sim.chip, 0, k);
        System.arraycopy(SimIO.getFloats(tag, "Flat", k, 0F, 0F, MAX_DEPTH), 0, sim.flat, 0, k);
        System.arraycopy(SimIO.getFloats(tag, "AzSum", k, 0F, 0F, 100F), 0, sim.azSum, 0, k);
        System.arraycopy(SimIO.getFloats(tag, "AzWeight", k, 0F, 0F, 100F), 0, sim.azWeight, 0, k);
        sim.heat = SimIO.finite(tag.getFloat("Heat"), 0F, 1.3F);
        sim.chips = SimIO.clamp(tag.getInt("Chips"), 0, 1000);
        sim.sizeLoss = SimIO.finite(tag.getFloat("SizeLoss"), 0F, 10F);
        sim.sweep = SimIO.finite(tag.getFloat("Sweep"), -1F, 1F);
        sim.height = tag.contains("Height") ? SimIO.finite(tag.getFloat("Height"), 0F, 1F) : 1F;
        sim.region = SimIO.clamp(tag.getInt("Region"), 0, k - 1);
        sim.trim = SimIO.clamp(tag.getInt("Trim"), -FacetParams.MAX_TRIM, FacetParams.MAX_TRIM);
        sim.contact = tag.getBoolean("Contact");
        sim.hot = tag.getBoolean("Hot");
        sim.touched = tag.getBoolean("Touched");
        sim.steps = Math.max(0, tag.getInt("Steps"));
        return sim;
    }

    public FacetSim copy() {
        return load(p, save());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static FacetSim read(FacetParams params, FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return load(params, tag == null ? new CompoundTag() : tag);
    }
}
