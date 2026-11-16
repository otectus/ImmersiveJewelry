package com.otectus.immersivejewelry.client.workstation.render;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * The shape of a stone, in stone units (girdle radius about 1, crown up), from its working regions' depths. A rough
 * stone is an irregular convex blob made from the stone's own seed; each region is a plane that starts at the blob's
 * surface and moves to the cut's planned face as its depth reaches the target, past it when overcut. Chips are small
 * extra cuts at the chipped region's edge. The four profiles lay their planes out differently:
 *
 * <ul>
 *   <li>round: an even ring of crown facets, a table that opens as the work proceeds, a conical pavilion;</li>
 *   <li>brilliant: split crown facets and small upper facets around a wide table;</li>
 *   <li>emerald: a long octagon, sides and cut corners in tiers, a flat table;</li>
 *   <li>cabochon: each zone is a fan of planes at four rising angles, drawn with smooth normals as a dome.</li>
 * </ul>
 *
 * Shapes are cached by profile, seed and depths quantized to 1/512, so an unchanged stone is never rebuilt.
 */
public final class GemGeometry {
    public enum Profile {
        ROUND, BRILLIANT, EMERALD, CABOCHON, ROUGH;

        public static Profile of(String visualProfile) {
            return switch (visualProfile) {
                case "brilliant" -> BRILLIANT;
                case "emerald", "step" -> EMERALD;
                case "cabochon", "dome" -> CABOCHON;
                default -> ROUND;
            };
        }
    }

    private static final int CACHE = 96;
    private static final Map<String, Polyhedron> SHAPES = new LinkedHashMap<>(CACHE, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Polyhedron> eldest) {
            return size() > CACHE;
        }
    };

    /** Forgets every cached shape (resource reload, leaving a world). */
    public static void clear() {
        SHAPES.clear();
    }

    /**
     * The stone's shape.
     *
     * @param depth    each region's depth, or null for a rough stone
     * @param target   each region's planned depth
     * @param chips    a bit per chipped region
     * @param progress overall share of the planned removal (opens the table and shapes the pavilion)
     */
    public static Polyhedron shape(Profile profile, long seed, float[] depth, float[] target, int chips, float progress) {
        int k = depth == null ? 0 : depth.length;
        float[] ratios = new float[k];
        StringBuilder key = new StringBuilder().append(profile.ordinal()).append(':').append(seed).append(':').append(k)
                .append(':').append(chips);
        int progressStep = Math.round(Mth.clamp(progress, 0F, 1F) * 512F);
        key.append(':').append(progressStep);
        for (int i = 0; i < k; i++) {
            int step = Math.round(Math.max(0F, depth[i] / Math.max(0.05F, target[i])) * 512F);
            key.append(',').append(step);
            ratios[i] = step / 512F;
        }
        // Build from the cache key itself. Previously the first sample in a coarse bucket decided its shape, so
        // eviction/re-entry could give the same key a different mesh depending on which packet arrived first.
        return SHAPES.computeIfAbsent(key.toString(), s -> build(profile, seed, ratios, chips, progressStep / 512F));
    }

    /** A finished stone of a profile with {@code regions} faces. */
    public static Polyhedron finished(Profile profile, long seed, int regions) {
        return finished(profile, seed, regions, 0);
    }

    /** Finished geometry retains the stone's damage in the tray, clamp and loupe. */
    public static Polyhedron finished(Profile profile, long seed, int regions, int chips) {
        float[] d = new float[Math.max(2, regions)];
        float[] t = new float[d.length];
        java.util.Arrays.fill(d, 0.8F);
        java.util.Arrays.fill(t, 0.8F);
        return shape(profile, seed, d, t, chips, 1F);
    }

    public static Polyhedron rough(long seed) {
        return shape(Profile.ROUGH, seed, null, null, 0, 0F);
    }

    private static Polyhedron build(Profile profile, long seed, float[] ratios, int chips, float progress) {
        Random random = new Random(seed * 31L + 7L);
        Polyhedron rough = Polyhedron.box(1.2F, 1.0F, 1.15F, Polyhedron.ROUGH);
        for (int i = 0; i < 14; i++) {
            Vector3f n = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1);
            if (n.lengthSquared() < 0.05F) continue;
            rough = rough.clip(n.normalize(), 0.92F + random.nextFloat() * 0.2F, Polyhedron.ROUGH);
        }
        if (profile == Profile.ROUGH || ratios.length == 0) return rough;
        Polyhedron stone = rough;
        int k = ratios.length;
        float p = Mth.clamp(progress, 0F, 1F);
        boolean emerald = profile == Profile.EMERALD;

        // All offsets are measured against the SAME uncut stone. Using the partly clipped mesh as the next plane's
        // origin made old facets move back out when another facet changed. Fixed planes make every cut subtractive.
        int sides = emerald ? 8 : Math.max(12, k * 2);
        float radius = emerald ? 0.72F : 0.78F;
        for (int i = 0; i < sides; i++) {
            float a = Mth.TWO_PI * i / sides;
            Vector3f side = new Vector3f(Mth.cos(a), 0F, Mth.sin(a));
            if (emerald) side.set(side.x / 1.12F, 0F, side.z / 0.85F);
            float length = side.length();
            stone = plane(stone, rough, side.div(length), radius / length, p, Polyhedron.GIRDLE);
            if (profile != Profile.CABOCHON) {
                Vector3f pavilion = new Vector3f(Mth.cos(a) * 0.56F, -0.78F, Mth.sin(a) * 0.56F);
                if (emerald) pavilion.set(pavilion.x / 1.12F, pavilion.y, pavilion.z / 0.85F);
                float d = 0.62F * 0.78F / pavilion.length();
                stone = plane(stone, rough, pavilion.normalize(), d, p, Polyhedron.PAVILION);
            }
        }
        if (profile == Profile.CABOCHON) {
            stone = plane(stone, rough, new Vector3f(0, -1, 0), 0.18F, p, Polyhedron.PAVILION);
        }

        for (int i = 0; i < k; i++) {
            float t = ratios[i];
            float a = Mth.TWO_PI * i / k;
            switch (profile) {
                case ROUND -> stone = plane(stone, rough, crown(a, 48F), crownOffset(48F, radius, 0.06F), t, i);
                case BRILLIANT -> {
                    for (int side = -1; side <= 1; side += 2) {
                        stone = plane(stone, rough, crown(a + side * Mth.PI / k * 0.42F, 42F),
                                crownOffset(42F, radius, 0.06F), t, i);
                    }
                    stone = plane(stone, rough, crown(a, 58F), crownOffset(58F, 0.68F, 0.18F), t, i);
                }
                case EMERALD -> {
                    for (float elevation : new float[]{65F, 35F}) {
                        Vector3f n = crown(a, elevation);
                        n.set(n.x / 1.12F, n.y, n.z / 0.85F);
                        float d = elevation == 65F ? crownOffset(elevation, radius, 0.04F)
                                : crownOffset(elevation, 0.57F, 0.24F);
                        stone = plane(stone, rough, new Vector3f(n).normalize(), d / n.length(), t, i);
                    }
                }
                case CABOCHON -> {
                    for (int f = 0; f < 4; f++) {
                        for (int side = -1; side <= 1; side += 2) {
                            Vector3f n = crown(a + side * Mth.PI / k * 0.5F, 18F + 22F * f);
                            stone = plane(stone, rough, n, 0.76F - 0.06F * n.y, t, i);
                        }
                    }
                }
                default -> { }
            }
            if ((chips & (1 << i)) != 0) {
                Vector3f n = new Vector3f(Mth.cos(a + 0.2F), 0.35F, Mth.sin(a + 0.2F)).normalize();
                stone = plane(stone, rough, n, 0.64F, Math.max(p, t), Polyhedron.CHIP);
            }
        }
        if (profile != Profile.CABOCHON) stone = plane(stone, rough, new Vector3f(0, 1, 0), 0.42F, p, Polyhedron.TABLE);
        return stone;
    }

    private static float crownOffset(float elevation, float radius, float height) {
        float a = elevation * Mth.DEG_TO_RAD;
        return radius * Mth.sin(a) + height * Mth.cos(a);
    }

    /** A crown facet's outward normal: azimuth {@code a}, tilted {@code tiltDegrees} from the stone's axis. */
    private static Vector3f crown(float a, float tiltDegrees) {
        float t = tiltDegrees * Mth.DEG_TO_RAD;
        return new Vector3f(Mth.cos(a) * Mth.sin(t), Mth.cos(t), Mth.sin(a) * Mth.sin(t)).normalize();
    }

    private static Polyhedron plane(Polyhedron stone, Polyhedron rough, Vector3f normal, float planned, float progress, int tag) {
        if (progress <= 0F) return stone;
        float start = rough.support(normal);
        float offset = progress <= 1F ? Mth.lerp(progress, start, Math.min(start, planned)) : planned - (progress - 1F) * 0.18F;
        return stone.clip(normal, offset, tag);
    }

    private GemGeometry() {}
}
