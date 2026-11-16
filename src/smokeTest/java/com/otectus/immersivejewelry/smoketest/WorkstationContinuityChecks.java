package com.otectus.immersivejewelry.smoketest;

import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.client.workstation.WorkstationClient;
import com.otectus.immersivejewelry.client.workstation.render.GemProgress;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.util.Map;

/** Opt-in evidence that the exact gem geometry submitted to the wheel painter advances continuously. */
final class WorkstationContinuityChecks {
    private static final boolean ENABLED = System.getenv("IJ_GEM_CONTINUITY") != null;
    private static final float EPSILON = 1.0e-5F;
    private static final float[] MILESTONES = {.10F, .35F, .65F, .90F};
    private static final boolean[] milestoneShot = new boolean[MILESTONES.length];
    private static boolean active;
    private static Field renderedProgress;
    private static GemProgress.Snapshot previous;
    private static long previousFingerprint = Long.MIN_VALUE;
    private static long priorDistinctFingerprint = Long.MIN_VALUE;
    private static long lastDistinctFingerprint = Long.MIN_VALUE;
    private static int previousVersion = -1, previousAck = -1;
    private static int samples, snapshotChanges, geometryChanges, versionChanges, ackChanges, backtracks, alternations;
    private static int missingSnapshots, workIdChanges;
    private static float minProgress = Float.POSITIVE_INFINITY, maxProgress = Float.NEGATIVE_INFINITY, maxIndexStep;
    private static String failure;

    static boolean enabled() { return ENABLED; }

    static void begin() {
        if (ENABLED) active = true;
    }

    static void pause(Map<String, Object> checks) {
        if (!ENABLED) return;
        active = false;
        publish(checks);
    }

    /** Client-tick work is limited to milestone screenshots; geometry is sampled after actual render frames. */
    static void clientTick(Minecraft mc) {
        if (!ENABLED || !active || previous == null) return;
        for (int i = 0; i < MILESTONES.length; i++) {
            if (!milestoneShot[i] && previous.progress() >= MILESTONES[i]) {
                milestoneShot[i] = true;
                ClientSmokeTest.shot(mc, "smoketest_wheel_continuity_" + Math.round(MILESTONES[i] * 100));
                break;
            }
        }
    }

    /** Called at RenderTick END: renderedProgress is the immutable snapshot WheelBehavior gave WheelPainter.draw. */
    static void renderFrame(Minecraft mc, Map<String, Object> checks) {
        if (!ENABLED || !active || failure != null || mc.level == null) return;
        try {
            Object behavior = WorkstationClient.behaviourAt(ClientSmokeTest.wheel()).orElse(null);
            if (behavior == null) return;
            if (renderedProgress == null || !renderedProgress.getDeclaringClass().isInstance(behavior)) {
                renderedProgress = field(behavior.getClass(), "renderedProgress");
            }
            GemProgress.Snapshot snapshot = (GemProgress.Snapshot) renderedProgress.get(behavior);
            if (snapshot == null || snapshot.ratios().length == 0) {
                if (previous != null) missingSnapshots++;
                return;
            }
            LapidaryWheelBlockEntity wheel = mc.level.getBlockEntity(ClientSmokeTest.wheel()) instanceof LapidaryWheelBlockEntity w ? w : null;
            if (wheel == null) return;
            sample(snapshot, wheel.clientVersion(), wheel.ackStep());
        } catch (ReflectiveOperationException | RuntimeException e) {
            failure = e.toString();
            checks.put("gem_render_continuity_probe", failure);
            checks.put("gem_render_continuity", false);
        }
    }

    private static void sample(GemProgress.Snapshot current, int version, int ack) {
        samples++;
        minProgress = Math.min(minProgress, current.progress());
        maxProgress = Math.max(maxProgress, current.progress());
        long fingerprint = fingerprint(current);
        if (previous != null && current.workId() != previous.workId()) workIdChanges++;
        if (previous != null && current.workId() == previous.workId()) {
            boolean changed = Math.abs(current.progress() - previous.progress()) > EPSILON;
            if (current.progress() + EPSILON < previous.progress()) backtracks++;
            float[] now = current.ratios(), before = previous.ratios();
            if (now.length != before.length) {
                backtracks++;
            } else {
                for (int i = 0; i < now.length; i++) {
                    changed |= Math.abs(now[i] - before[i]) > EPSILON;
                    if (now[i] + EPSILON < before[i]) backtracks++;
                }
            }
            if (changed) snapshotChanges++;
            float angle = (float) Math.abs(Math.atan2(Math.sin(current.indexAngle() - previous.indexAngle()),
                    Math.cos(current.indexAngle() - previous.indexAngle())));
            maxIndexStep = Math.max(maxIndexStep, angle);
        }
        if (fingerprint != previousFingerprint) geometryChanges++;
        if (fingerprint != lastDistinctFingerprint) {
            if (fingerprint == priorDistinctFingerprint) alternations++;
            priorDistinctFingerprint = lastDistinctFingerprint;
            lastDistinctFingerprint = fingerprint;
        }
        if (previousVersion >= 0 && version != previousVersion) versionChanges++;
        if (previousAck >= 0 && ack != previousAck) ackChanges++;
        previous = current;
        previousFingerprint = fingerprint;
        previousVersion = version;
        previousAck = ack;
    }

    /** Mirrors GemGeometry's canonical 1/512 cache key for the varying progress fields. */
    private static long fingerprint(GemProgress.Snapshot snapshot) {
        long hash = 31L * snapshot.workId() + snapshot.chips();
        hash = hash * 31 + Math.round(Math.max(0F, Math.min(1F, snapshot.progress())) * 512F);
        for (float ratio : snapshot.ratios()) hash = hash * 31 + Math.round(Math.max(0F, ratio) * 512F);
        return hash;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Continue through the behavior hierarchy.
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    static void publish(Map<String, Object> checks) {
        if (!ENABLED) return;
        checks.put("gem_render_continuity_samples", samples);
        checks.put("gem_render_snapshot_changes", snapshotChanges);
        checks.put("gem_render_geometry_changes", geometryChanges);
        checks.put("gem_render_server_versions", versionChanges);
        checks.put("gem_render_ack_changes", ackChanges);
        checks.put("gem_render_backtracks", backtracks);
        checks.put("gem_render_alternations", alternations);
        checks.put("gem_render_missing_snapshots", missingSnapshots);
        checks.put("gem_render_work_id_changes", workIdChanges);
        checks.put("gem_render_progress_min", Float.isFinite(minProgress) ? minProgress : -1F);
        checks.put("gem_render_progress_max", Float.isFinite(maxProgress) ? maxProgress : -1F);
        checks.put("gem_render_index_step_max_degrees", Math.toDegrees(maxIndexStep));
        checks.put("gem_render_milestone_screenshots", countMilestones());
        checks.put("gem_render_continuity_active", samples >= 20 && snapshotChanges >= 4 && geometryChanges >= 4
                && versionChanges >= 2 && ackChanges >= 2 && countMilestones() >= 3);
        checks.put("gem_render_continuity", failure == null && backtracks == 0 && alternations == 0
                && missingSnapshots == 0 && workIdChanges == 0);
    }

    private static int countMilestones() {
        int count = 0;
        for (boolean shot : milestoneShot) if (shot) count++;
        return count;
    }

    private WorkstationContinuityChecks() {}
}
