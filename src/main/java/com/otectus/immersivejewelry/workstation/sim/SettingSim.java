package com.otectus.immersivejewelry.workstation.sim;

import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

import java.util.EnumSet;

/**
 * Seating and securing stones, step by fixed 20 ms step, identically on both sides.
 *
 * <p>Prongs: the pliers grip one prong ({@code target} = socket x 16 + prong) and the handle's travel closes it.
 * Metal stays where it is bent: easing the handle back opens nothing, and a release springs back a little. The last
 * tenth before the prong meets the stone resists the squeeze, so the secure band is easy to feel. Past the band the
 * prong is overbent and a brittle stone may chip; a gentle pry eases an overbent prong back once or twice at the
 * cost of marking the metal. Balance between opposite prongs decides whether the stone sits level.</p>
 *
 * <p>Bezels: the pusher touches the rim at an angle ({@code b}) and its travel sets how far that part of the rim can be
 * pushed; the push spreads to the neighbouring sectors and closes them over a moment. Sweeping round at a moderate
 * push closes the rim evenly; pushing hard in one place pinches it. A sector already closed gains nothing from more
 * sweeping. In removal the same tools open the points instead.</p>
 *
 * <p>Seating is a discrete action ({@link #seat}): where the tweezers let the stone go in its cup decides how well it
 * sits. A stone resting in an untouched socket can be lifted and seated again; once a point has been closed on it, it
 * is held.</p>
 */
public final class SettingSim {
    public static final int VERSION = 1;
    public static final float DT = FacetSim.DT;
    public static final float MAX_CLOSURE = 1.4F;
    /** Removal: below this a point no longer holds its stone. */
    public static final float OPEN_BELOW = 0.35F;
    public static final float MAX_TRAVEL_STEP = 0.07F;
    public static final int TOOL_NONE = 0, TOOL_PLIERS = 1, TOOL_PUSHER = 2;

    public static final int EVENT_GRIP = 1, EVENT_RELEASE = 2, EVENT_CONTACT = 4, EVENT_OVER = 8, EVENT_CHIP = 16, EVENT_OPENED = 32,
            EVENT_PUSH = 64, EVENT_EASE = 128, EVENT_STRAIN = 256, EVENT_SECURED = 512;

    public enum Mode { SET, RETIGHTEN, REMOVE }

    private final SettingParams p;
    private final Mode mode;
    private final int n;
    private final float[] closure;
    private final int[] eases;
    private final float[] seat;
    private final boolean[] chipped;
    private final float[] strain;
    private float travel;
    private boolean gripping;
    private boolean prying;
    private int gripSocket = -1;
    private int gripPoint = -1;
    private float gripStart;
    private float gripRaw;
    private float gripTravel;
    private int steps;
    private int events;

    public SettingSim(SettingParams params, Mode mode) {
        this.p = params;
        this.mode = mode;
        this.n = params.sockets() * params.points();
        closure = new float[n];
        eases = new int[n];
        seat = new float[params.sockets()];
        chipped = new boolean[params.sockets()];
        strain = new float[params.sockets()];
    }

    /** Advances one step. Returns this step's events. */
    public int step(WorkSample in) {
        events = 0;
        float lastTravel = travel;
        boolean contact = in.has(WorkSample.FLAG_CONTACT);
        // Letting go opens the tool at once; while held, the handle moves no faster than a hand.
        travel = contact ? SimIO.approach(travel, SimIO.unit12(in.a()), MAX_TRAVEL_STEP) : 0F;
        float speed = Math.abs(travel - lastTravel) / DT;
        int socket = in.target() >> 4;
        int point = in.target() & 15;
        boolean valid = socket < p.sockets() && seat[socket] > 0F;
        if (in.aux() == TOOL_PLIERS && p.style() == SettingStyle.PRONG && contact && valid && point < p.points()) {
            prong(socket, point, in.has(WorkSample.FLAG_PRY), speed);
        } else {
            release();
            if (in.aux() == TOOL_PUSHER && p.style() == SettingStyle.BEZEL && contact && valid) {
                float theta = SimIO.unit12(in.b()) * Mth.TWO_PI;
                bezel(socket, theta, in.has(WorkSample.FLAG_PRY) || mode == Mode.REMOVE, speed);
            }
        }
        steps++;
        return events;
    }

    private void prong(int socket, int point, boolean pry, float speed) {
        int i = socket * p.points() + point;
        if (!gripping || gripSocket != socket || gripPoint != point || prying != (pry || mode == Mode.REMOVE)) {
            // Taking hold of another point opens the jaws first: the squeeze starts again from an open handle.
            if (gripping) travel = 0F;
            release();
            gripping = true;
            prying = pry || mode == Mode.REMOVE;
            gripSocket = socket;
            gripPoint = point;
            gripStart = closure[i];
            gripRaw = unbend(closure[i]);
            gripTravel = travel;
            events |= EVENT_GRIP;
        }
        float before = closure[i];
        float pushed = Math.max(0F, travel - gripTravel) * p.gain();
        if (!prying) {
            closure[i] = Math.max(closure[i], Math.min(MAX_CLOSURE, bend(gripRaw + pushed)));
        } else {
            float floor = mode == Mode.REMOVE ? 0F : Math.max(0F, gripStart - p.pryLimit());
            closure[i] = Math.min(closure[i], Math.max(floor, gripStart - pushed));
            if (speed > 2.5F) {
                strain[socket] = Math.min(1F, strain[socket] + (speed - 2.5F) * DT * 0.6F);
                events |= EVENT_STRAIN;
            }
        }
        crossings(socket, before, closure[i]);
    }

    private void bezel(int socket, float theta, boolean open, float speed) {
        float cap = travel * MAX_CLOSURE;
        boolean any = false;
        for (int q = 0; q < p.points(); q++) {
            float d = angularDistance(theta, p.angle(q));
            if (d >= p.kernel()) continue;
            float w = 1F - d / p.kernel();
            int i = socket * p.points() + q;
            float before = closure[i];
            if (!open) {
                float limit = cap * (0.55F + 0.45F * w);
                if (closure[i] < limit) closure[i] = Math.min(limit, closure[i] + p.pushRate() * w * DT);
            } else {
                closure[i] = Math.max(0F, closure[i] - p.pushRate() * w * travel * DT);
                if (travel > 0.85F) {
                    strain[socket] = Math.min(1F, strain[socket] + (travel - 0.85F) * w * DT * 2F);
                    events |= EVENT_STRAIN;
                }
            }
            if (closure[i] != before) any = true;
            crossings(socket, before, closure[i]);
        }
        if (any) events |= EVENT_PUSH;
    }

    private void crossings(int socket, float before, float after) {
        float lo = 1F - p.tolerance();
        float hi = 1F + p.tolerance();
        if (before < lo && after >= lo) {
            events |= EVENT_CONTACT;
            if (secured(socket)) events |= EVENT_SECURED;
        }
        if (before <= hi && after > hi) events |= EVENT_OVER;
        if (before <= p.chipAbove() && after > p.chipAbove() && !chipped[socket]) {
            chipped[socket] = true;
            events |= EVENT_CHIP;
        }
        if (before >= OPEN_BELOW && after < OPEN_BELOW) events |= EVENT_OPENED;
    }

    private void release() {
        if (!gripping) return;
        int i = gripSocket * p.points() + gripPoint;
        if (!prying && closure[i] > gripStart) {
            closure[i] -= p.springBack() * (closure[i] - gripStart);
        } else if (prying && mode != Mode.REMOVE && closure[i] < gripStart - 0.01F) {
            eases[i]++;
            events |= EVENT_EASE;
        }
        gripping = false;
        gripSocket = -1;
        gripPoint = -1;
        events |= EVENT_RELEASE;
    }

    /** The stone's resistance near contact: closure as a function of raw squeeze. */
    private float bend(float raw) {
        float onset = 1F - 0.1F;
        return raw <= onset ? raw : onset + (raw - onset) * (1F - p.resistance());
    }

    private float unbend(float value) {
        float onset = 1F - 0.1F;
        return value <= onset ? value : onset + (value - onset) / (1F - p.resistance());
    }

    static float angularDistance(float a, float b) {
        float d = Math.abs(a - b) % Mth.TWO_PI;
        return d > Mth.PI ? Mth.TWO_PI - d : d;
    }

    // ------------------------------------------------------------------------------------------------ seating

    /**
     * Lets a stone go into socket {@code s}: {@code dx}, {@code dy} where it was let go relative to the cup's centre and
     * {@code turn} how far it was turned from the cup's orientation, each -1..1 of the capture region.
     */
    public void seat(int s, float dx, float dy, float turn) {
        if (s < 0 || s >= p.sockets()) return;
        dx = SimIO.finite(dx, -1F, 1F);
        dy = SimIO.finite(dy, -1F, 1F);
        turn = SimIO.finite(turn, -1F, 1F);
        seat[s] = SimIO.clamp(1F - 0.55F * (dx * dx + dy * dy) - 0.25F * turn * turn, 0.2F, 1F);
    }

    /** A seated stone is lifted out again (allowed only while no point has been closed on it). */
    public void unseat(int s) {
        if (s < 0 || s >= p.sockets()) return;
        seat[s] = 0F;
        for (int q = 0; q < p.points(); q++) closure[s * p.points() + q] = 0F;
    }

    /** Whether socket {@code s} holds a stone that nothing has closed on yet. */
    public boolean untouched(int s) {
        for (int q = 0; q < p.points(); q++) if (closure[s * p.points() + q] > 0.001F) return false;
        return true;
    }

    /** Sets the state of a finished piece being retightened or taken apart (all stones seated, points as given). */
    public void restore(float[] closures, float[] seats) {
        for (int i = 0; i < n && i < closures.length; i++) closure[i] = SimIO.finite(closures[i], 0F, MAX_CLOSURE);
        for (int s = 0; s < seat.length && s < seats.length; s++) seat[s] = SimIO.finite(seats[s], 0F, 1F);
    }

    // ------------------------------------------------------------------------------------------------ evaluation

    public float pointAccuracy(int i) {
        float c = closure[i];
        float tol = p.tolerance();
        float value;
        if (c < 1F - tol) value = 0.45F * c / (1F - tol);
        else if (c <= 1F + tol) value = 1F - 0.5F * Math.abs(c - 1F) / tol;
        else value = Math.max(0.1F, 0.4F - (c - 1F - tol) * 1.5F);
        if (eases[i] >= 2) value *= 0.9F;
        return SimIO.clamp(value, 0F, 1F);
    }

    /** How level stone {@code s} sits: 1 when the closure is balanced all round. */
    public float alignment(int s) {
        float mean = 0F;
        for (int q = 0; q < p.points(); q++) mean += closure[s * p.points() + q];
        mean /= p.points();
        float x = 0F;
        float y = 0F;
        for (int q = 0; q < p.points(); q++) {
            float w = closure[s * p.points() + q] - mean;
            x += w * (float) StrictMath.cos(p.angle(q));
            y += w * (float) StrictMath.sin(p.angle(q));
        }
        return Math.max(0F, 1F - (float) StrictMath.sqrt(x * x + y * y) / p.points() * 4F);
    }

    public float socketQuality(int s) {
        float sum = 0F;
        for (int q = 0; q < p.points(); q++) sum += pointAccuracy(s * p.points() + q);
        float q = sum / p.points() * (0.7F + 0.3F * alignment(s)) * (0.8F + 0.2F * seat[s]);
        return chipped[s] ? q * 0.85F : q;
    }

    /** Setting quality 0..1. */
    public float quality() {
        float sum = 0F;
        for (int s = 0; s < p.sockets(); s++) sum += socketQuality(s);
        return SimIO.clamp(sum / p.sockets(), 0F, 1F);
    }

    /** Removal: how cleanly the stones came out, 0..1. */
    public float removalQuality() {
        float worst = 0F;
        for (float v : strain) worst = Math.max(worst, v);
        return 1F - Math.min(0.6F, worst);
    }

    public EnumSet<SettingDefect> defects() {
        EnumSet<SettingDefect> out = EnumSet.noneOf(SettingDefect.class);
        float tol = p.tolerance();
        for (int s = 0; s < p.sockets(); s++) {
            if (alignment(s) < 0.7F || seat[s] < 0.45F) out.add(SettingDefect.CROOKED);
            if (chipped[s]) out.add(SettingDefect.CHIPPED_STONE);
            for (int q = 0; q < p.points(); q++) {
                int i = s * p.points() + q;
                if (closure[i] < 1F - tol) out.add(SettingDefect.LOOSE);
                if (closure[i] > 1F + tol) out.add(SettingDefect.BENT_PRONG);
                if (eases[i] >= 2 || closure[i] > 1F + 3F * tol) out.add(SettingDefect.SCRATCHED_METAL);
            }
        }
        return out;
    }

    /** Every point of stone {@code s} is in or past the secure band. */
    public boolean secured(int s) {
        for (int q = 0; q < p.points(); q++) if (closure[s * p.points() + q] < 1F - p.tolerance()) return false;
        return true;
    }

    public boolean allSecured() {
        for (int s = 0; s < p.sockets(); s++) if (!secured(s)) return false;
        return true;
    }

    /** Removal: every point of stone {@code s} is open enough to lift it. */
    public boolean opened(int s) {
        for (int q = 0; q < p.points(); q++) if (closure[s * p.points() + q] >= OPEN_BELOW) return false;
        return true;
    }

    /** The next point to work on around stone {@code s} in the recommended order, or -1. */
    public int recommended(int s) {
        for (int q : p.order()) {
            float c = closure[s * p.points() + q];
            if (mode == Mode.REMOVE ? c >= OPEN_BELOW : c < 1F - p.tolerance()) return q;
        }
        return -1;
    }

    // ------------------------------------------------------------------------------------------------ state

    public SettingParams params() { return p; }
    public Mode mode() { return mode; }
    public float closure(int socket, int point) { return closure[socket * p.points() + point]; }
    public int eases(int socket, int point) { return eases[socket * p.points() + point]; }
    public float seat(int socket) { return seat[socket]; }
    public boolean seated(int socket) { return seat[socket] > 0F; }
    public boolean chipped(int socket) { return chipped[socket]; }
    public float strain(int socket) { return strain[socket]; }
    public float travel() { return travel; }
    public boolean gripping() { return gripping; }
    public int gripSocket() { return gripSocket; }
    public int gripPoint() { return gripPoint; }
    public int steps() { return steps; }
    public int lastEvents() { return events; }

    /** Lets go of everything at once. */
    public void neutral() {
        release();
        travel = 0F;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("V", VERSION);
        tag.putString("Mode", mode.name());
        SimIO.putFloats(tag, "Closure", closure);
        SimIO.putInts(tag, "Eases", eases);
        SimIO.putFloats(tag, "Seat", seat);
        int[] chips = new int[chipped.length];
        for (int i = 0; i < chips.length; i++) chips[i] = chipped[i] ? 1 : 0;
        SimIO.putInts(tag, "Chipped", chips);
        SimIO.putFloats(tag, "Strain", strain);
        tag.putFloat("Travel", travel);
        tag.putBoolean("Gripping", gripping);
        tag.putBoolean("Prying", prying);
        tag.putInt("GripSocket", gripSocket);
        tag.putInt("GripPoint", gripPoint);
        tag.putFloat("GripStart", gripStart);
        tag.putFloat("GripRaw", gripRaw);
        tag.putFloat("GripTravel", gripTravel);
        tag.putInt("Steps", steps);
        return tag;
    }

    public static SettingSim load(SettingParams params, CompoundTag tag) {
        Mode mode;
        try {
            mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.SET;
        }
        SettingSim sim = new SettingSim(params, mode);
        int n = sim.n;
        int s = params.sockets();
        System.arraycopy(SimIO.getFloats(tag, "Closure", n, 0F, 0F, MAX_CLOSURE), 0, sim.closure, 0, n);
        System.arraycopy(SimIO.getInts(tag, "Eases", n, 0, 0, 100), 0, sim.eases, 0, n);
        System.arraycopy(SimIO.getFloats(tag, "Seat", s, 0F, 0F, 1F), 0, sim.seat, 0, s);
        int[] chips = SimIO.getInts(tag, "Chipped", s, 0, 0, 1);
        for (int i = 0; i < s; i++) sim.chipped[i] = chips[i] != 0;
        System.arraycopy(SimIO.getFloats(tag, "Strain", s, 0F, 0F, 1F), 0, sim.strain, 0, s);
        sim.travel = SimIO.finite(tag.getFloat("Travel"), 0F, 1F);
        sim.gripping = tag.getBoolean("Gripping");
        sim.prying = tag.getBoolean("Prying");
        sim.gripSocket = SimIO.clamp(tag.getInt("GripSocket"), -1, s - 1);
        sim.gripPoint = SimIO.clamp(tag.getInt("GripPoint"), -1, params.points() - 1);
        if (sim.gripSocket < 0 || sim.gripPoint < 0) sim.gripping = false;
        sim.gripStart = SimIO.finite(tag.getFloat("GripStart"), 0F, MAX_CLOSURE);
        sim.gripRaw = SimIO.finite(tag.getFloat("GripRaw"), 0F, 4F);
        sim.gripTravel = SimIO.finite(tag.getFloat("GripTravel"), 0F, 1F);
        sim.steps = Math.max(0, tag.getInt("Steps"));
        return sim;
    }

    public SettingSim copy() {
        return load(p, save());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }
}
