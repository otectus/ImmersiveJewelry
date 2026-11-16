package com.otectus.immersivejewelry.client.workstation.render;

import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.api.gem.GemDamage;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws a stone's {@link Polyhedron}: the rough skin matte and dull, ground regions in the gem's colour with their
 * state readable on them (fresh faces bright, a face in its window catching the light, scratches greyed, chips dark),
 * the table brightest, domes smoothly shaded. Opaque by default; see-through only with the client's gem-translucency
 * option, so it reads without shaders.
 */
public final class GemRenderer {

    public static int damageMask(GemDamage damage) {
        return switch (damage) {
            case CHIPPED -> 1;
            case FRACTURED -> 5;
            default -> 0;
        };
    }

    public static Regions damage(GemDamage damage) {
        if (damage == GemDamage.NONE) return Regions.NONE;
        return new Regions() {
            @Override public float scratch(int region) { return damage == GemDamage.SCRATCHED ? 0.18F : 0.08F; }
        };
    }

    /** How each region looks: its brightness boost (a passing glint), scratch and whether it is in its window. */
    public interface Regions {
        Regions NONE = new Regions() {};

        default float glint(int region) { return 0F; }
        default float scratch(int region) { return 0F; }
        default boolean inWindow(int region) { return false; }
        default boolean over(int region) { return false; }
        default boolean highlighted(int region) { return false; }
    }

    /**
     * @param size     girdle radius in pixels
     * @param color    the gem's colour (RGB)
     * @param opaque   the gem is opaque by nature (onyx, lapis): never see-through
     * @param smooth   dome shading (cabochon)
     * @param sparkle  0..1 extra brightness of worked faces (cut quality)
     */
    public static void draw(Geo geo, Polyhedron shape, float size, int color, boolean opaque, boolean smooth, float sparkle, Regions regions) {
        boolean see = !opaque && ClientConfig.get(ClientConfig.GEM_TRANSLUCENCY);
        if (see) geo.glass();
        else geo.plain();
        int alpha = see ? 0xD8 : 0xFF;
        float sparkleScale = (float) ClientConfig.get(ClientConfig.GEM_SPARKLE_INTENSITY);
        for (Polyhedron.Face face : shape.faces()) {
            List<Vector3f> pts = face.points();
            int tint = faceColor(face, color, sparkle * sparkleScale, regions,
                    ClientConfig.get(ClientConfig.REDUCED_MOTION) ? 0F : sparkleScale);
            int argb = (alpha << 24) | (tint & 0xFFFFFF);
            Vector3f a = scaled(pts.get(0), size);
            for (int i = 1; i + 1 < pts.size(); i += 2) {
                Vector3f b = scaled(pts.get(i), size);
                Vector3f c = scaled(pts.get(i + 1), size);
                Vector3f d = i + 2 < pts.size() ? scaled(pts.get(i + 2), size) : c;
                if (smooth && face.tag() >= 0) {
                    geo.quadSmooth(a, b, c, d, dome(a), dome(b), dome(c), dome(d), argb);
                } else {
                    geo.quad(a, b, c, d, face.normal(), argb, 0F, 0F, 1F, 1F);
                }
            }
        }
        geo.plain();
    }

    private static Vector3f scaled(Vector3f p, float size) {
        return new Vector3f(p).mul(size);
    }

    private static Vector3f dome(Vector3f p) {
        return new Vector3f(p.x, Math.max(0.2F, p.y + 0.4F), p.z).normalize();
    }

    private static int faceColor(Polyhedron.Face face, int color, float sparkle, Regions regions, float glintScale) {
        int base = 0xFF000000 | color;
        int tag = face.tag();
        if (tag == Polyhedron.ROUGH) return Geo.scale(Geo.mix(base, 0xFF8A8478, 0.5F), 0.72F);
        if (tag == Polyhedron.CHIP) return Geo.scale(Geo.mix(base, 0xFF3A3530, 0.45F), 0.7F);
        if (tag == Polyhedron.PAVILION) return Geo.scale(base, 0.78F);
        if (tag == Polyhedron.GIRDLE) return Geo.scale(Geo.mix(base, 0xFFFFFFFF, 0.1F), 0.86F);
        if (tag == Polyhedron.TABLE) return Geo.mix(base, 0xFFFFFFFF, 0.18F + 0.25F * sparkle);
        float scratch = Mth.clamp(regions.scratch(tag) * 1.5F, 0F, 0.6F);
        int c = Geo.mix(base, 0xFFFFFFFF, 0.06F + 0.18F * sparkle);
        if (regions.inWindow(tag)) c = Geo.mix(c, 0xFFFFFFFF, 0.12F);
        if (regions.over(tag)) c = Geo.scale(c, 0.85F);
        if (scratch > 0F) c = Geo.mix(c, 0xFF9A958C, scratch);
        float glint = Mth.clamp(regions.glint(tag), 0F, 1F) * glintScale;
        if (glint > 0F) c = Geo.mix(c, 0xFFFFFFFF, 0.55F * glint);
        if (regions.highlighted(tag)) c = Geo.mix(c, 0xFFFFE6A0, 0.25F);
        return c;
    }

    /** The planned shape as thin gold edges over the stone: a guide, plainly not the stone itself. */
    public static void guide(Geo geo, Polyhedron target, float size, int argb) {
        geo.glass();
        float thickness = 0.05F;
        for (Polyhedron.Face face : target.faces()) {
            if (face.tag() < 0 && face.tag() != Polyhedron.TABLE) continue;
            List<Vector3f> pts = face.points();
            for (int i = 0; i < pts.size(); i++) {
                Vector3f a = new Vector3f(pts.get(i)).mul(size * 1.01F);
                Vector3f b = new Vector3f(pts.get((i + 1) % pts.size())).mul(size * 1.01F);
                geo.rod(a, b, thickness, argb);
            }
        }
        geo.plain();
    }

    private GemRenderer() {}
}
