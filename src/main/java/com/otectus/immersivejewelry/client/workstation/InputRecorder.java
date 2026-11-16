package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.WorkInputPacket;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Turns the operator's controls into the fixed-step sample stream the server simulates. Steps come from real time, 20
 * ms each, whatever the frame rate: a frame that covers several steps spreads the control's movement over them, so a
 * motion gives the same samples at 30 or 144 frames per second. Samples go out in batches of up to five, at most ten
 * batches a second, and are kept for a while so the client can replay what the server has not yet acknowledged on top
 * of its latest state. Without activity the recorder falls silent (no traffic, and the server pauses the work).
 */
final class InputRecorder {
    static final int STEP_NANOS = 20_000_000;
    private static final int BATCH = 5;
    private static final long BATCH_NANOS = 100_000_000L;
    private static final long IDLE_NANOS = 300_000_000L;
    private static final int HISTORY = 512;
    /** A frame hitch never produces more than this many steps at once. */
    private static final int MAX_STEPS_PER_FRAME = 12;

    private final WorkSample[] history = new WorkSample[HISTORY];
    private final List<WorkSample> pending = new ArrayList<>();
    private int pendingFirst;
    private int nextStep;
    private int seq;
    private long clock = -1L;
    private long lastSend;
    private long lastActive;
    private WorkSample previous = WorkSample.NEUTRAL;

    int nextStep() {
        return nextStep;
    }

    /**
     * Generates the steps real time has made room for, moving linearly from the previous control state to
     * {@code current}. {@code active}: the operator is doing something (a tool held, a key down, a contact).
     */
    void pump(WorkSample current, boolean active, long now, Consumer<WorkSample> apply) {
        boolean changed = !current.equals(previous);
        if (active || changed) lastActive = now;
        boolean live = now - lastActive < IDLE_NANOS;
        if (!live) {
            clock = -1L;
            previous = current;
            return;
        }
        if (clock < 0L) clock = now;
        int steps = (int) Math.min(MAX_STEPS_PER_FRAME, (now - clock) / STEP_NANOS);
        if (steps <= 0) return;
        clock += (long) steps * STEP_NANOS;
        if (now - clock > (long) STEP_NANOS * MAX_STEPS_PER_FRAME) clock = now;
        for (int i = 1; i <= steps; i++) {
            WorkSample s = WorkSample.between(previous, current, i / (float) steps);
            if (pending.isEmpty()) pendingFirst = nextStep;
            pending.add(s);
            history[Math.floorMod(nextStep, HISTORY)] = s;
            nextStep++;
            apply.accept(s);
        }
        previous = current;
    }

    /** Sends a batch when five samples are waiting or a tenth of a second passed. */
    void flush(BlockPos pos, int token, long workId, long now, boolean force) {
        if (pending.isEmpty() || token == 0) {
            if (token == 0) pending.clear();
            return;
        }
        if (!force && pending.size() < BATCH && now - lastSend < BATCH_NANOS) return;
        while (!pending.isEmpty()) {
            int n = Math.min(WorkInputPacket.MAX_SAMPLES, pending.size());
            List<WorkSample> batch = new ArrayList<>(pending.subList(0, n));
            ModNetwork.CHANNEL.sendToServer(new WorkInputPacket(pos, token, workId, ++seq, pendingFirst, batch));
            pending.subList(0, n).clear();
            pendingFirst += n;
        }
        lastSend = now;
    }

    /** Replays every recorded step from {@code from} on (the server acknowledged everything before it). */
    void replay(int from, Consumer<WorkSample> apply) {
        int start = Math.max(from, nextStep - HISTORY);
        for (int step = start; step < nextStep; step++) {
            WorkSample s = history[Math.floorMod(step, HISTORY)];
            if (s != null) apply.accept(s);
        }
    }

    /** A fresh stream (new lease, new work). Batch numbers keep counting so the server never mistakes them for repeats. */
    void reset() {
        pending.clear();
        java.util.Arrays.fill(history, null);
        nextStep = 0;
        clock = -1L;
        previous = WorkSample.NEUTRAL;
        lastActive = 0L;
    }
}
