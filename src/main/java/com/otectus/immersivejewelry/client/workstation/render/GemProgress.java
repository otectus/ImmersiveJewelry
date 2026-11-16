package com.otectus.immersivejewelry.client.workstation.render;

/** Render-only, continuous removal between confirmed snapshots. It never feeds back into the work simulation. */
public final class GemProgress {
    public record Snapshot(long workId, float[] ratios, float progress, float indexAngle, int chips) {}

    private long workId;
    private float[] ratios = new float[0];
    private float[] targets = new float[0];
    private float progress, targetProgress, angle;
    private int chips;

    public void clear() { ratios = targets = new float[0]; workId = 0L; }

    public Snapshot snapshot() {
        return ratios.length == 0 ? null : new Snapshot(workId, ratios.clone(), progress, angle, chips);
    }

    public void update(long id, float[] confirmed, float overall, int region, int damage, float seconds) {
        float targetAngle = (float) (Math.PI * 2 * region / confirmed.length);
        if (id != workId || confirmed.length != ratios.length) {
            workId = id;
            ratios = confirmed.clone();
            targets = confirmed.clone();
            progress = targetProgress = overall;
            angle = targetAngle;
            chips = damage;
            return;
        }
        for (int i = 0; i < ratios.length; i++) targets[i] = Math.max(targets[i], confirmed[i]);
        targetProgress = Math.max(targetProgress, overall);
        chips |= damage;
        advance(seconds);
        // Shortest arc around the index collar: no 360-degree rewind at the last/first face boundary.
        float delta = (float) Math.atan2(Math.sin(targetAngle - angle), Math.cos(targetAngle - angle));
        angle += delta * ease(seconds, 12F);
    }

    /** Settle into the completed cut without replacing the mesh or resetting its viewing angle on confirmation. */
    public void finish(float seconds) {
        for (int i = 0; i < targets.length; i++) targets[i] = Math.max(targets[i], 1F);
        targetProgress = 1F;
        advance(seconds);
    }

    private void advance(float seconds) {
        float ease = ease(seconds, 16F);
        for (int i = 0; i < ratios.length; i++) ratios[i] += (targets[i] - ratios[i]) * ease;
        progress += (targetProgress - progress) * ease;
    }

    private static float ease(float seconds, float rate) {
        return (float) (1 - Math.exp(-Math.max(0, Math.min(0.1F, seconds)) * rate));
    }
}
