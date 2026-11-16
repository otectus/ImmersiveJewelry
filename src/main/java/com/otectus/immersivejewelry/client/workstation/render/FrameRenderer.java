package com.otectus.immersivejewelry.client.workstation.render;

import com.mojang.math.Axis;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Draws a piece of jewelry face-on in its own pixels (+x right, +y up, +z towards the worker): the frame body of its
 * form, each socket's cup with prongs or a bezel rim shaped by their closure, and the stones. The surface is drawn per
 * finishing cell, so a polish follows the cloth, a burnish follows the passes and a patina darkens the recesses before
 * the high points are wiped bright. The same surface parameters map a pointer to a cell ({@link #surfaceAt}).
 */
public final class FrameRenderer {

    /** The main surface of a body: centre, angular range (radians, counter-clockwise from +x), radial range, thickness. */
    public record Surface(float cx, float cy, float a0, float a1, float rIn, float rOut, float thickness, float sx, float sy) {
        boolean full() {
            return a1 - a0 >= Mth.TWO_PI - 1.0E-3F;
        }
    }

    public static Surface surface(DesignLayout.Body body) {
        return switch (body) {
            case RING -> new Surface(0F, 0F, 0F, Mth.TWO_PI, 3.3F, 4.4F, 1.2F, 1F, 1F);
            case PENDANT, HALO -> new Surface(0F, -0.6F, 0F, Mth.TWO_PI, 0F, 3.3F, 0.5F, 1F, 1F);
            case LOCKET -> new Surface(0F, -0.6F, 0F, Mth.TWO_PI, 0F, 3.2F, 0.6F, 0.86F, 1.12F);
            case CUFF -> new Surface(0F, 1.8F, 210F * Mth.DEG_TO_RAD, 330F * Mth.DEG_TO_RAD, 3.0F, 4.7F, 1.0F, 1F, 1F);
            case LINKS -> new Surface(0F, 2.0F, 198F * Mth.DEG_TO_RAD, 342F * Mth.DEG_TO_RAD, 3.5F, 4.6F, 0.7F, 1F, 1F);
            case CIRCLET -> new Surface(0F, -0.4F, 18F * Mth.DEG_TO_RAD, 162F * Mth.DEG_TO_RAD, 4.1F, 4.8F, 0.7F, 1F, 1F);
            case DIADEM -> new Surface(0F, -0.6F, 15F * Mth.DEG_TO_RAD, 165F * Mth.DEG_TO_RAD, 3.8F, 4.6F, 0.8F, 1F, 1F);
        };
    }

    /** The finishing cell coordinates (u, v in 0..1) of a point of the piece's front, or null when it is off the surface. */
    @Nullable
    public static float[] surfaceAt(DesignLayout.Body body, float x, float y) {
        Surface s = surface(body);
        float dx = (x - s.cx()) / s.sx();
        float dy = (y - s.cy()) / s.sy();
        float r = Mth.sqrt(dx * dx + dy * dy);
        float margin = 0.6F;
        if (r < s.rIn() - margin || r > s.rOut() + margin) return null;
        float a = (float) Math.atan2(dy, dx);
        if (a < 0F) a += Mth.TWO_PI;
        float u;
        if (s.full()) {
            u = a / Mth.TWO_PI;
        } else {
            float span = s.a1() - s.a0();
            float rel = a - s.a0();
            if (rel < -0.3F) rel += Mth.TWO_PI;
            if (rel < -0.25F || rel > span + 0.25F) return null;
            u = Mth.clamp(rel / span, 0F, 0.9999F);
        }
        float v = Mth.clamp((r - s.rIn()) / Math.max(0.1F, s.rOut() - s.rIn()), 0F, 1F);
        return new float[]{u, v};
    }

    // ------------------------------------------------------------------------------------------------ colours

    /** How the metal looks: raw until a finish covers it. */
    public record Look(int metal, int recess, int highlight, @Nullable SurfaceSim surface, @Nullable SurfaceParams.Mode finished, float wear) {

        int colorAt(float u, float v) {
            int raw = Geo.mix(Geo.scale(0xFF000000 | metal, 0.8F), 0xFF8C8C8C, 0.18F);
            int bright = 0xFF000000 | highlight;
            int dark = 0xFF000000 | recess;
            int cell = Mth.clamp((int) (u * SurfaceParams.U), 0, SurfaceParams.U - 1) * SurfaceParams.V
                    + Mth.clamp((int) (v * SurfaceParams.V), 0, SurfaceParams.V - 1);
            int c;
            if (surface != null) {
                SurfaceParams p = surface.params();
                float cover = surface.cover(cell);
                c = switch (p.mode()) {
                    case POLISH -> Geo.mix(raw, Geo.mix(0xFF000000 | metal, bright, 0.55F), cover);
                    case BURNISH -> Geo.mix(raw, Geo.mix(0xFF000000 | metal, bright, 0.25F), cover);
                    case PATINA -> surface.phase() == 0 ? Geo.mix(raw, dark, cover)
                            : p.high()[cell] ? Geo.mix(dark, Geo.mix(0xFF000000 | metal, bright, 0.4F), cover) : dark;
                    case REPAIR -> p.target()[cell] ? Geo.mix(Geo.scale(raw, 0.62F), Geo.mix(0xFF000000 | metal, bright, 0.2F), cover)
                            : Geo.mix(0xFF000000 | metal, bright, 0.2F);
                };
            } else if (finished != null) {
                boolean high = v < 0.17F || v > 0.83F || cell % 4 == 0;
                c = switch (finished) {
                    case POLISH -> Geo.mix(0xFF000000 | metal, bright, high ? 0.65F : 0.45F);
                    case BURNISH -> Geo.mix(0xFF000000 | metal, bright, high ? 0.32F : 0.2F);
                    case PATINA -> high ? Geo.mix(0xFF000000 | metal, bright, 0.45F) : dark;
                    case REPAIR -> 0xFF000000 | metal;
                };
            } else {
                c = raw;
            }
            if (wear > 0F) c = Geo.mix(c, 0xFF5A5650, Mth.clamp(wear, 0F, 0.5F));
            return c;
        }
    }

    // ------------------------------------------------------------------------------------------------ bodies

    public static void body(Geo geo, DesignLayout layout, Look look) {
        geo.plain();
        Surface s = surface(layout.body());
        switch (layout.body()) {
            case RING -> {
                band(geo, s, look, 32);
                DesignLayout.Socket head = layout.socket(0);
                // Raised shoulders sweep into the gallery, with daylight beneath the stone's basket.
                for (int side : new int[]{-1, 1}) {
                    Vector3f shank = new Vector3f(side * 2.55F, 3.25F, 0F);
                    Vector3f shoulder = new Vector3f(side * 1.65F, 4.2F, 0.12F);
                    Vector3f seat = new Vector3f(head.x() + side * head.radius() * 0.7F, head.y(), -0.15F);
                    geo.rod(shank, shoulder, 0.48F, look.colorAt(side < 0 ? 0.36F : 0.14F, 1F));
                    geo.rod(shoulder, seat, 0.32F, look.colorAt(0.25F, 1F));
                }
            }
            case PENDANT, HALO -> {
                plate(geo, s, look, 28);
                rim(geo, s.cx(), s.cy(), s.rOut() - 0.35F, s.rOut() + 0.1F, 0.45F, look, s);
                bail(geo, s.cx(), s.cy() + s.rOut() * s.sy() + 0.55F, look);
                if (layout.body() == DesignLayout.Body.HALO) {
                    DesignLayout.Socket c = layout.socket(0);
                    rim(geo, c.x(), c.y(), c.radius() + 0.45F, c.radius() + 0.85F, 0.55F, look, s);
                }
            }
            case LOCKET -> {
                plate(geo, s, look, 28);
                rim(geo, s.cx(), s.cy(), s.rOut() - 0.3F, s.rOut() + 0.1F, 0.5F, look, s);
                geo.centred(s.cx(), s.cy() + s.rOut() * s.sy() + 0.15F, 0F, 0.7F, 0.25F, 0.35F, look.colorAt(0.25F, 1F));
                bail(geo, s.cx(), s.cy() + s.rOut() * s.sy() + 0.75F, look);
            }
            case CUFF -> {
                band(geo, s, look, 18);
                // The rest of the cuff, thinner, curving away behind the front panel.
                Surface back = new Surface(s.cx(), s.cy(), s.a1(), s.a0() + Mth.TWO_PI, 3.55F, 4.1F, 0.6F, 1F, 1F);
                bandShape(geo, back, (u, v) -> Geo.scale(look.colorAt(0.5F, 0.5F), 0.85F), 18, 0.5F);
            }
            case LINKS -> {
                int links = 9;
                for (int i = 0; i < links; i++) {
                    float t = (i + 0.5F) / links;
                    float a = Mth.lerp(t, s.a0(), s.a1());
                    float x = s.cx() + Mth.cos(a) * 4.05F;
                    float y = s.cy() + Mth.sin(a) * 4.05F;
                    linkLoop(geo, x, y, a, 0.55F, 0.32F, look.colorAt(t, 0.5F));
                }
            }
            case CIRCLET -> band(geo, s, look, 20);
            case DIADEM -> {
                band(geo, s, look, 20);
                for (DesignLayout.Socket socket : layout.sockets()) {
                    float top = socket.y() + socket.radius() + 0.9F;
                    int c = look.colorAt(0.5F, 1F);
                    Vector3f l = crestFoot(s, socket.x() - socket.radius() - 0.5F);
                    Vector3f r = crestFoot(s, socket.x() + socket.radius() + 0.5F);
                    Vector3f t = new Vector3f(socket.x(), top, 0.25F);
                    // An open pierced crest, supported at both ends of the band rather than a flat metal sail.
                    geo.rod(l, t, 0.3F, c);
                    geo.rod(t, r, 0.3F, c);
                    geo.rod(l, new Vector3f(socket.x(), socket.y() - socket.radius() * 0.6F, -0.15F), 0.22F, Geo.scale(c, 0.9F));
                    geo.rod(r, new Vector3f(socket.x(), socket.y() - socket.radius() * 0.6F, -0.15F), 0.22F, Geo.scale(c, 0.9F));
                }
            }
        }
    }

    private static Vector3f crestFoot(Surface band, float x) {
        float radius = (band.rIn() + band.rOut()) * 0.5F;
        float dx = Mth.clamp(x - band.cx(), Mth.cos(band.a1()) * radius, Mth.cos(band.a0()) * radius);
        // The upper arc is the physical attachment: each rod starts inside the band's front surface.
        return new Vector3f(band.cx() + dx, band.cy() + Mth.sqrt(Math.max(0F, radius * radius - dx * dx)), 0.25F);
    }

    private interface CellColor {
        int at(float u, float v);
    }

    private static void band(Geo geo, Surface s, Look look, int segments) {
        bandShape(geo, s, look::colorAt, segments, s.thickness());
    }

    /** An annular sector with thickness: front, back, outer and inner rims; front cells coloured per finishing cell. */
    private static void bandShape(Geo geo, Surface s, CellColor color, int segments, float thickness) {
        float zf = thickness * 0.5F;
        float zb = -thickness * 0.5F;
        int rows = 3;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments, t1 = (i + 1) / (float) segments;
            float a0 = Mth.lerp(t0, s.a0(), s.a1()), a1 = Mth.lerp(t1, s.a0(), s.a1());
            for (int j = 0; j < rows; j++) {
                float v0 = j / (float) rows, v1 = (j + 1) / (float) rows;
                float r0 = Mth.lerp(v0, s.rIn(), s.rOut()), r1 = Mth.lerp(v1, s.rIn(), s.rOut());
                int c = color.at((t0 + t1) * 0.5F, (v0 + v1) * 0.5F);
                float innerZ = j == 0 ? zf * 0.55F : zf;
                float outerZ = j == rows - 1 ? zf * 0.55F : zf;
                geo.face(p(s, a0, r0, innerZ), p(s, a0, r1, outerZ), p(s, a1, r1, outerZ), p(s, a1, r0, innerZ), c);
            }
            int back = Geo.scale(color.at((t0 + t1) * 0.5F, 0.5F), 0.7F);
            geo.face(p(s, a1, s.rIn(), zb), p(s, a1, s.rOut(), zb), p(s, a0, s.rOut(), zb), p(s, a0, s.rIn(), zb), back);
            int outer = Geo.scale(color.at((t0 + t1) * 0.5F, 1F), 0.92F);
            geo.face(p(s, a0, s.rOut(), zb), p(s, a1, s.rOut(), zb), p(s, a1, s.rOut(), zf * 0.55F), p(s, a0, s.rOut(), zf * 0.55F), outer);
            int inner = Geo.scale(color.at((t0 + t1) * 0.5F, 0F), 0.75F);
            geo.face(p(s, a0, s.rIn(), zf * 0.55F), p(s, a1, s.rIn(), zf * 0.55F), p(s, a1, s.rIn(), zb), p(s, a0, s.rIn(), zb), inner);
        }
        if (!s.full()) {
            for (float a : new float[]{s.a0(), s.a1()}) {
                boolean start = a == s.a0();
                int c = Geo.scale(color.at(start ? 0F : 1F, 0.5F), 0.85F);
                for (int j = 0; j < rows; j++) {
                    float r0 = Mth.lerp(j / (float) rows, s.rIn(), s.rOut());
                    float r1 = Mth.lerp((j + 1) / (float) rows, s.rIn(), s.rOut());
                    Vector3f i0 = p(s, a, r0, j == 0 ? zf * 0.55F : zf);
                    Vector3f o0 = p(s, a, r1, j == rows - 1 ? zf * 0.55F : zf);
                    Vector3f o1 = p(s, a, r1, zb), i1 = p(s, a, r0, zb);
                    if (start) geo.face(o0, i0, i1, o1, c);
                    else geo.face(i0, o0, o1, i1, c);
                }
            }
        }
    }

    private static Vector3f p(Surface s, float a, float r, float z) {
        return new Vector3f(s.cx() + Mth.cos(a) * r * s.sx(), s.cy() + Mth.sin(a) * r * s.sy(), z);
    }

    /** A plate: rings of cells from the centre out. */
    private static void plate(Geo geo, Surface s, Look look, int segments) {
        float zf = s.thickness() * 0.5F;
        float zb = -s.thickness() * 0.5F;
        int rows = 4;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments, t1 = (i + 1) / (float) segments;
            float a0 = t0 * Mth.TWO_PI, a1 = t1 * Mth.TWO_PI;
            for (int j = 0; j < rows; j++) {
                float v0 = j / (float) rows, v1 = (j + 1) / (float) rows;
                float r0 = v0 * s.rOut(), r1 = v1 * s.rOut();
                int c = look.colorAt((t0 + t1) * 0.5F, (v0 + v1) * 0.5F);
                if (j == 0) geo.face(p(s, a0, 0F, zf), p(s, a0, r1, zf), p(s, a1, r1, zf), p(s, a1, r1, zf), c);
                else geo.face(p(s, a0, r0, zf), p(s, a0, r1, zf), p(s, a1, r1, zf), p(s, a1, r0, zf), c);
            }
            geo.face(p(s, a1, s.rOut(), zb), p(s, a0, s.rOut(), zb), p(s, a0, 0F, zb), p(s, a0, 0F, zb), Geo.scale(look.colorAt(t0, 0.5F), 0.7F));
            geo.face(p(s, a0, s.rOut(), zb), p(s, a1, s.rOut(), zb), p(s, a1, s.rOut(), zf), p(s, a0, s.rOut(), zf),
                    Geo.scale(look.colorAt(t0, 1F), 0.9F));
        }
    }

    /** A raised rim ring on a plate (decoration, finished with the cells under it). */
    private static void rim(Geo geo, float cx, float cy, float rIn, float rOut, float height, Look look, Surface s) {
        Surface r = new Surface(cx, cy, 0F, Mth.TWO_PI, rIn, rOut, height, s.sx(), s.sy());
        geo.pose().pushPose();
        geo.pose().translate(0F, 0F, s.thickness() * 0.5F);
        bandShape(geo, r, (u, v) -> look.colorAt(u, 0.95F), 28, height);
        geo.pose().popPose();
    }

    private static void bail(Geo geo, float x, float y, Look look) {
        Surface loop = new Surface(x, y, 0F, Mth.TWO_PI, 0.42F, 0.82F, 0.4F, 1F, 1F);
        bandShape(geo, loop, (u, v) -> look.colorAt(0.25F, 1F), 14, 0.4F);
    }

    private static void linkLoop(Geo geo, float x, float y, float a, float rx, float ry, int color) {
        geo.pose().pushPose();
        geo.pose().translate(x, y, 0F);
        geo.pose().mulPose(Axis.ZP.rotation(a + Mth.HALF_PI));
        Surface loop = new Surface(0F, 0F, 0F, Mth.TWO_PI, 0.62F, 1F, 0.45F, rx, ry);
        bandShape(geo, loop, (u, v) -> color, 12, 0.45F);
        geo.pose().popPose();
    }

    // ------------------------------------------------------------------------------------------------ settings

    /** Where the points stand: closure per point, which one is gripped or targeted. */
    public interface Points {
        float closure(int socket, int point);

        default boolean targeted(int socket, int point) { return false; }

        default boolean loose(int socket, int point) { return closure(socket, point) < 0.88F; }
    }

    /**
     * One socket's cup and its prongs or bezel rim. {@code tolerance} is the secure band's half-width (for the rim's
     * remaining-work highlight).
     */
    public static void setting(Geo geo, DesignLayout.Socket socket, int index, SettingStyle style, int points, float tolerance, Points state,
                               Look look, boolean seated, boolean highContrast) {
        geo.plain();
        float r = socket.radius();
        int metal = look.colorAt(0.5F, 0.8F);
        // An open gallery supports the girdle; narrow ribs hold it above a smaller rear seat.
        // Solid caps hid both the pavilion and the relationship between stone, basket and prongs.
        Surface gallery = new Surface(socket.x(), socket.y(), 0F, Mth.TWO_PI, r * 0.79F, r * 0.94F, 0.18F, 1F, 1F);
        bandShape(geo, gallery, (u, v) -> metal, 24, 0.18F);
        geo.pose().pushPose();
        geo.pose().translate(0F, 0F, -0.5F);
        Surface seat = new Surface(socket.x(), socket.y(), 0F, Mth.TWO_PI, r * 0.43F, r * 0.59F, 0.15F, 1F, 1F);
        bandShape(geo, seat, (u, v) -> Geo.scale(metal, 0.8F), 16, 0.15F);
        geo.pose().popPose();
        for (int i = 0; i < 4; i++) {
            float a = Mth.PI * 0.25F + i * Mth.HALF_PI;
            geo.rod(new Vector3f(socket.x() + Mth.cos(a) * r * 0.53F, socket.y() + Mth.sin(a) * r * 0.53F, -0.5F),
                    new Vector3f(socket.x() + Mth.cos(a) * r * 0.87F, socket.y() + Mth.sin(a) * r * 0.87F, 0F),
                    0.18F, Geo.scale(metal, 0.9F));
        }
        if (style == SettingStyle.BEZEL) {
            bezel(geo, socket, index, points, tolerance, state, metal, highContrast);
        } else {
            for (int q = 0; q < points; q++) prong(geo, socket, index, q, points, state, metal, highContrast);
        }
    }

    private static void prong(Geo geo, DesignLayout.Socket socket, int s, int q, int points, Points state, int metal, boolean highContrast) {
        float angle = Mth.PI / points + q * Mth.TWO_PI / points + socket.turn();
        float c = Mth.clamp(state.closure(s, q), 0F, 1.4F);
        float r = socket.radius();
        float cos = Mth.cos(angle), sin = Mth.sin(angle);
        // Base behind the girdle; the knee at girdle height; the tip leaning in as the prong closes.
        Vector3f base = new Vector3f(socket.x() + cos * (r + 0.12F), socket.y() + sin * (r + 0.12F), -0.45F);
        Vector3f knee = new Vector3f(socket.x() + cos * (r + 0.2F), socket.y() + sin * (r + 0.2F), 0.2F);
        float tipR = c < 1F ? Mth.lerp(c, r + 0.55F, r - 0.16F) : Mth.lerp((c - 1F) / 0.4F, r - 0.16F, r - 0.48F);
        float tipZ = c < 1F ? Mth.lerp(c, 0.85F, 0.55F) : Mth.lerp((c - 1F) / 0.4F, 0.55F, 0.4F);
        Vector3f tip = new Vector3f(socket.x() + cos * tipR, socket.y() + sin * tipR, tipZ);
        int color = 0xFF000000 | metal;
        if (state.targeted(s, q)) color = highContrast ? 0xFFFFE600 : Geo.mix(color, 0xFFFFF0B0, 0.55F);
        else if (c > 1.12F) color = Geo.mix(color, 0xFF6A3A2A, 0.35F);
        geo.rod(base, knee, 0.3F, color);
        geo.rod(knee, tip, 0.27F, color);
    }

    private static void bezel(Geo geo, DesignLayout.Socket socket, int s, int sectors, float tolerance, Points state, int metal, boolean highContrast) {
        int segments = Math.max(24, sectors * 6);
        float r = socket.radius() + 0.14F;
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments, a1 = Mth.TWO_PI * (i + 1) / segments;
            float c0 = rimClosure(state, s, sectors, a0), c1 = rimClosure(state, s, sectors, a1);
            float lean0 = Mth.clamp(c0, 0F, 1.4F) * 0.24F, lean1 = Mth.clamp(c1, 0F, 1.4F) * 0.24F;
            float pinch0 = c0 > 1F + tolerance ? (c0 - 1F) * 0.2F * Mth.sin(a0 * 7F) : 0F;
            float pinch1 = c1 > 1F + tolerance ? (c1 - 1F) * 0.2F * Mth.sin(a1 * 7F) : 0F;
            Vector3f b0 = new Vector3f(socket.x() + Mth.cos(a0) * r, socket.y() + Mth.sin(a0) * r, -0.3F);
            Vector3f b1 = new Vector3f(socket.x() + Mth.cos(a1) * r, socket.y() + Mth.sin(a1) * r, -0.3F);
            float tr0 = r - lean0 + pinch0, tr1 = r - lean1 + pinch1;
            Vector3f t0 = new Vector3f(socket.x() + Mth.cos(a0) * tr0, socket.y() + Mth.sin(a0) * tr0, 0.55F);
            Vector3f t1 = new Vector3f(socket.x() + Mth.cos(a1) * tr1, socket.y() + Mth.sin(a1) * tr1, 0.55F);
            int sector = Mth.clamp((int) (((a0 + a1) * 0.5F) / Mth.TWO_PI * sectors + 0.5F) % sectors, 0, sectors - 1);
            float cm = (c0 + c1) * 0.5F;
            int color = 0xFF000000 | metal;
            if (state.targeted(s, sector)) color = highContrast ? 0xFFFFE600 : Geo.mix(color, 0xFFFFF0B0, 0.4F);
            if (cm > 1F + tolerance) color = Geo.mix(color, 0xFF6A3A2A, 0.3F);
            // Outside of the wall, then the inside facing the stone.
            geo.face(b0, b1, t1, t0, color);
            geo.face(b1, b0, t0, t1, Geo.scale(color, 0.8F));
            // The rim's top edge: a narrow bright line where the rim has not yet reached the stone.
            if (cm < 1F - tolerance) {
                int line = highContrast ? 0xFFFFFFFF : Geo.mix(color, 0xFFFFFFFF, 0.65F);
                Vector3f e0 = new Vector3f(t0).add(0, 0, 0.04F), e1 = new Vector3f(t1).add(0, 0, 0.04F);
                geo.rod(e0, e1, 0.09F, line);
            }
        }
    }

    /** The closure round a bezel rim at angle {@code a}, interpolated between sector centres. */
    private static float rimClosure(Points state, int s, int sectors, float a) {
        float pos = a / Mth.TWO_PI * sectors;
        int i0 = Mth.floor(pos) % sectors;
        int i1 = (i0 + 1) % sectors;
        float t = pos - Mth.floor(pos);
        return Mth.lerp(t, state.closure(s, i0), state.closure(s, i1));
    }

    private FrameRenderer() {}
}
