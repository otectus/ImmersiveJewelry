package com.otectus.immersivejewelry.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Small pixel shapes and colour arithmetic for the remaining screens and widgets (the inventory's jewelry button, metal
 * tints). The 1.1 minigame screens and their indicators are gone: the work happens at the stations in the world.
 */
public final class JewelryGui {

    public static void ring(GuiGraphics g, float cx, float cy, float radius, int color) {
        int n = Math.max(16, Math.round(radius * 6));
        for (int i = 0; i < n; i++) {
            float a = i * Mth.TWO_PI / n;
            int px = Math.round(cx + Mth.cos(a) * radius);
            int py = Math.round(cy + Mth.sin(a) * radius);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** Fills a polygon (x/y pairs) with the even-odd rule, one scanline at a time. */
    public static void polygon(GuiGraphics g, float[] pts, int color) {
        if (pts.length < 6) return;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 1; i < pts.length; i += 2) {
            minY = Math.min(minY, pts[i]);
            maxY = Math.max(maxY, pts[i]);
        }
        int n = pts.length / 2;
        float[] xs = new float[n];
        for (int y = Mth.floor(minY); y <= Mth.ceil(maxY); y++) {
            float sy = y + 0.5F;
            int count = 0;
            for (int i = 0; i < n; i++) {
                float ax = pts[i * 2], ay = pts[i * 2 + 1];
                float bx = pts[((i + 1) % n) * 2], by = pts[((i + 1) % n) * 2 + 1];
                if ((ay <= sy && by > sy) || (by <= sy && ay > sy)) xs[count++] = ax + (sy - ay) / (by - ay) * (bx - ax);
            }
            java.util.Arrays.sort(xs, 0, count);
            for (int i = 0; i + 1 < count; i += 2) g.fill(Math.round(xs[i]), y, Math.round(xs[i + 1]), y + 1, color);
        }
    }

    /** A diamond marker (a shape, so it reads without colour). */
    public static void diamond(GuiGraphics g, float cx, float cy, float r, int color) {
        polygon(g, new float[]{cx, cy - r, cx + r, cy, cx, cy + r, cx - r, cy}, color);
    }

    public static int scale(int argb, float factor) {
        int r = Mth.clamp(Math.round(((argb >> 16) & 255) * factor), 0, 255);
        int gr = Mth.clamp(Math.round(((argb >> 8) & 255) * factor), 0, 255);
        int b = Mth.clamp(Math.round((argb & 255) * factor), 0, 255);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0F, 1F);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    private JewelryGui() {}
}
