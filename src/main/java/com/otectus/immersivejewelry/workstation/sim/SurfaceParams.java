package com.otectus.immersivejewelry.workstation.sim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The bounded surface of one piece for finishing and repair: a grid of {@link #U} cells round the piece by {@link #V}
 * across it, which cells are raised (high points), and which a repair must reach. Resolved by the server, saved with
 * the work.
 *
 * @param mode      what the tool does to the surface
 * @param high      raised cells (an antique wipe brightens these and leaves the recesses dark)
 * @param target    cells the operation has to cover (all of them, the high points, or the damaged ones)
 * @param rate      coverage per second under the middle of the tool (polish, patina)
 * @param stroke    coverage per cell of travel along the piece (burnish, repair)
 * @param threshold share of the target that must be covered
 */
public record SurfaceParams(Mode mode, boolean[] high, boolean[] target, float rate, float stroke, float threshold) {
    public static final int VERSION = 1;
    public static final int U = 24, V = 6, CELLS = U * V;
    public static final int TOOL_CLOTH = 3, TOOL_BURNISHER = 4, TOOL_BRUSH = 5;

    /** Polish: compound wiped round in overlapping arcs. Burnish: smooth passes along the frame. Patina: dab on, wipe the high
     * points. Repair: alignment passes over the damaged sections. */
    public enum Mode {
        POLISH, BURNISH, PATINA, REPAIR;

        public static Mode forFinish(String visualProfile) {
            return switch (visualProfile) {
                case "burnished", "satin", "brushed" -> BURNISH;
                case "antique", "patina", "oxidized" -> PATINA;
                default -> POLISH;
            };
        }

        /** The tool that works this mode's surface in its {@code phase}. */
        public int tool(int phase) {
            return switch (this) {
                case POLISH -> TOOL_CLOTH;
                case BURNISH, REPAIR -> TOOL_BURNISHER;
                case PATINA -> phase == 0 ? TOOL_BRUSH : TOOL_CLOTH;
            };
        }

        public int phases() {
            return this == PATINA ? 2 : 1;
        }
    }

    /**
     * @param seed       the work's seed (the ornament ridges and damage pattern follow it)
     * @param damage     repair only: how much of the frame is bent, 0..1 of the cells
     * @param timeScale  data-driven work rate: above 1 is slower (a {@code target_active_seconds} profile)
     */
    public static SurfaceParams create(Mode mode, long seed, float damage, float timeScale, boolean gentler) {
        Random random = new Random(seed ^ 0x5DEECE66DL);
        boolean[] high = new boolean[CELLS];
        int ridge = 3 + random.nextInt(3);
        for (int u = 0; u < U; u++) {
            for (int v = 0; v < V; v++) {
                high[u * V + v] = v == 0 || v == V - 1 || (u % ridge == 0 && v > 0 && v < V - 1);
            }
        }
        boolean[] target = new boolean[CELLS];
        if (mode == Mode.REPAIR) {
            int count = Mth.clamp(Math.round(CELLS * Mth.clamp(damage, 0.15F, 1F) * 0.5F), 8, CELLS);
            int start = random.nextInt(U);
            // One bent section round the piece, spreading across its width.
            for (int n = 0, u = start; n < count; u = (u + 1) % U) {
                for (int v = 0; v < V && n < count; v++, n++) target[u * V + v] = true;
            }
        } else {
            for (int i = 0; i < CELLS; i++) target[i] = true;
        }
        float scale = Mth.clamp(timeScale, 0.25F, 4F) * (gentler ? 1.25F : 1F);
        return new SurfaceParams(mode, high, target, 22F / scale, 0.45F / scale, 0.88F);
    }

    public int targetCount() {
        int n = 0;
        for (boolean b : target) if (b) n++;
        return n;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("V", VERSION);
        tag.putString("Mode", mode.name());
        tag.putByteArray("High", pack(high));
        tag.putByteArray("Target", pack(target));
        tag.putFloat("Rate", rate);
        tag.putFloat("Stroke", stroke);
        tag.putFloat("Threshold", threshold);
        return tag;
    }

    public static SurfaceParams load(CompoundTag tag) {
        Mode mode;
        try {
            mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.POLISH;
        }
        boolean[] target = unpack(tag.getByteArray("Target"));
        boolean any = false;
        for (boolean b : target) any |= b;
        if (!any) java.util.Arrays.fill(target, true);
        return new SurfaceParams(mode, unpack(tag.getByteArray("High")), target, SimIO.finite(tag.getFloat("Rate"), 1F, 200F),
                SimIO.finite(tag.getFloat("Stroke"), 0.02F, 4F), SimIO.finite(tag.getFloat("Threshold"), 0.3F, 1F));
    }

    private static byte[] pack(boolean[] cells) {
        byte[] out = new byte[CELLS];
        for (int i = 0; i < CELLS; i++) out[i] = (byte) (cells[i] ? 1 : 0);
        return out;
    }

    private static boolean[] unpack(byte[] raw) {
        boolean[] out = new boolean[CELLS];
        for (int i = 0; i < CELLS && i < raw.length; i++) out[i] = raw[i] != 0;
        return out;
    }
}
