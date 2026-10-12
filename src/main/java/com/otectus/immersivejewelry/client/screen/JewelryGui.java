package com.otectus.immersivejewelry.client.screen;

import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;


/**
 * The minigames' own indicators, drawn inside the dark displays of the vanilla screens ({@link VanillaGui#display}):
 * the stone and its facets, prongs, gauge bands, markers, ratings and cue sounds. Brass marks what to work on, as on a
 * jeweler's bench. Colours honour the client's high-contrast and colorblind options, every state also has a label or a
 * shape, and none of this changes a scoring window. Everything else on the screens is Minecraft's own look.
 */
public final class JewelryGui {
    /** The brass of the target facet, the gauge bands and the pips. */
    public static final int BRASS = 0xFFBD862B;
    public static final int BRASS_DARK = 0xFF8C6420;
    public static final int BRASS_LIGHT = 0xFFE0B250;
    /** The target band of a gauge, translucent over the dark track. */
    public static final int BAND = 0x80C9A45C;
    public static final int PRESSURE = 0xFF97ADAE;
    /** Countdowns and "once more" cues inside the dark displays. */
    public static final int CUE = 0xFFFFD66B;
    /** Experience gained, in a result readout. */
    public static final int XP = 0xFF9CDB5A;

    /** The time left as a vanilla bar: brass while there is time, then red (orange when colorblind-safe). */
    public static void timeBar(GuiGraphics g, int x, int y, int w, int h, float remaining) {
        VanillaGui.bar(g, x, y, w, h, remaining, remaining > 0.25F ? BRASS_LIGHT : missColor());
        // Quarter marks make the remaining time readable without colour.
        for (int i = 1; i < 4; i++) g.fill(x + w * i / 4, y, x + w * i / 4 + 1, y + h, 0x80000000);
    }

    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int size, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        int n = Math.max(1, Math.round(Mth.sqrt(dx * dx + dy * dy)));
        for (int i = 0; i <= n; i++) {
            int px = Math.round(x0 + dx * i / n);
            int py = Math.round(y0 + dy * i / n);
            g.fill(px, py, px + size, py + size, color);
        }
    }

    public static void dashed(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        int n = Math.max(1, Math.round(Mth.sqrt(dx * dx + dy * dy)));
        for (int i = 0; i <= n; i++) {
            if ((i / 2) % 2 == 1) continue;
            int px = Math.round(x0 + dx * i / n);
            int py = Math.round(y0 + dy * i / n);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    public static void ring(GuiGraphics g, float cx, float cy, float radius, int color) {
        int n = Math.max(16, Math.round(radius * 6));
        for (int i = 0; i < n; i++) {
            float a = i * Mth.TWO_PI / n;
            int px = Math.round(cx + Mth.cos(a) * radius);
            int py = Math.round(cy + Mth.sin(a) * radius);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    public static void disc(GuiGraphics g, float cx, float cy, float radius, int color) {
        int r = Math.max(1, Math.round(radius));
        for (int dy = -r; dy <= r; dy++) {
            int half = Math.round(Mth.sqrt(Math.max(0, r * r - dy * dy)));
            int py = Math.round(cy) + dy;
            g.fill(Math.round(cx) - half, py, Math.round(cx) + half + 1, py + 1, color);
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

    public static void outline(GuiGraphics g, float[] pts, int color) {
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            line(g, pts[i * 2], pts[i * 2 + 1], pts[j * 2], pts[j * 2 + 1], 1, color);
        }
    }

    /** An annular wedge from radius r0 to r1 over [a0, a1]. */
    public static float[] wedge(float cx, float cy, float r0, float r1, float a0, float a1) {
        int steps = Math.max(3, Math.round((a1 - a0) * r1 / 4F));
        float[] pts = new float[(steps + 1) * 4];
        int k = 0;
        for (int i = 0; i <= steps; i++) {
            float a = a0 + (a1 - a0) * i / steps;
            pts[k++] = cx + Mth.cos(a) * r1;
            pts[k++] = cy + Mth.sin(a) * r1;
        }
        for (int i = steps; i >= 0; i--) {
            float a = a0 + (a1 - a0) * i / steps;
            pts[k++] = cx + Mth.cos(a) * r0;
            pts[k++] = cy + Mth.sin(a) * r0;
        }
        return pts;
    }

    /** A diamond marker (a shape, so it reads without colour). */
    public static void diamond(GuiGraphics g, float cx, float cy, float r, int color) {
        polygon(g, new float[]{cx, cy - r, cx + r, cy, cx, cy + r, cx - r, cy}, color);
    }

    public static void check(GuiGraphics g, int x, int y, int color) {
        line(g, x, y + 2, x + 2, y + 4, 1, color);
        line(g, x + 2, y + 4, x + 6, y, 1, color);
    }

    public static void cross(GuiGraphics g, int x, int y, int color) {
        line(g, x, y, x + 4, y + 4, 1, color);
        line(g, x + 4, y, x, y + 4, 1, color);
    }

    public static int goodColor() {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) return 0xFFFFFFFF;
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFF3D8BFF : 0xFF6CCB5F;
    }

    public static int missColor() {
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFFFF9F1C : 0xFFE05A4A;
    }

    /** {@link #goodColor} for text on the light panel, where the display colours would wash out. */
    public static int goodText() {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) return 0xFF000000;
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFF0B4FB0 : 0xFF1F7A1F;
    }

    /** {@link #missColor} for text on the light panel (vanilla's dark red). */
    public static int missText() {
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFFA34A00 : 0xFFAA0000;
    }

    public static int targetColor() {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) return 0xFFFFE600;
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFFBFE0FF : BRASS_LIGHT;
    }

    public static float markerScale() {
        return ClientConfig.get(ClientConfig.LARGE_MINIGAME_MARKERS) ? 1.6F : 1.0F;
    }

    public static boolean reducedMotion() {
        return ClientConfig.get(ClientConfig.REDUCED_MOTION);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int lighten(int argb, int amount) {
        int r = Math.min(255, ((argb >> 16) & 255) + amount);
        int gr = Math.min(255, ((argb >> 8) & 255) + amount);
        int b = Math.min(255, (argb & 255) + amount);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
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

    public static void playUi(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public static void playCue() {
        if (!ClientConfig.get(ClientConfig.TIMING_CUE_SOUNDS)) return;
        float volume = (float) ClientConfig.get(ClientConfig.SOUND_CUE_VOLUME);
        if (volume > 0F) playUi(ModSounds.UI_CUE.get(), 1.3F, volume);
    }

    private JewelryGui() {}
}
