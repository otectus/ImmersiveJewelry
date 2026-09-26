package com.otectus.immersivejewelry.client.screen;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Stoneborn-inspired workshop UI: masonry surrounds, recessed warm stone, restrained gold corners.
 * Palette references: Ultima's minecraft GUI inventory, widgets, and jewelcraft jeweler_bench.
 */
public final class JewelryGui {
    public static final ResourceLocation STONE = ImmersiveJewelry.id("textures/gui/stone.png");
    public static final ResourceLocation SLOT = ImmersiveJewelry.id("textures/gui/slot.png");
    public static final int SURFACE = 0xFF37322A;
    public static final int INSET = 0xFF27241F;
    public static final int EDGE = 0xFF151310;
    public static final int BORDER = 0xFF4F473C;
    public static final int EDGE_LIGHT = 0xFF675D4E;
    public static final int ROW_HOVER = 0xFF494237;
    public static final int ROW_SELECTED = 0xFF524735;
    public static final int BRASS = 0xFFBD862B;
    public static final int BRASS_DARK = 0xFF8C6420;
    public static final int BRASS_LIGHT = 0xFFE0B250;
    public static final int TEXT = 0xFFE5DFD2;
    public static final int TEXT_DIM = 0xFFB9B0A0;
    public static final int TEXT_WARM = 0xFFE5C887;
    public static final int PRESSURE = 0xFF97ADAE;

    /** Tiled masonry stays at its native pixel density; only the surround is ornamented. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x80000000);
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, EDGE_LIGHT);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, BORDER);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, EDGE);
        for (int py = 5; py < h - 5; py += 16) {
            for (int px = 5; px < w - 5; px += 16) {
                g.blit(STONE, x + px, y + py, 0, 0, Math.min(16, w - 5 - px), Math.min(16, h - 5 - py), 16, 16);
            }
        }
        for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
            int cx = sx == 1 ? x + 2 : x + w - 3;
            int cy = sy == 1 ? y + 2 : y + h - 3;
            for (int i = 0; i < 7; i++) {
                int c = i < 3 ? BRASS_LIGHT : BRASS_DARK;
                g.fill(cx + sx * i, cy, cx + sx * i + 1, cy + 1, c);
                g.fill(cx, cy + sy * i, cx + 1, cy + sy * i + 1, c);
            }
            g.fill(cx + sx * 2, cy + sy * 2, cx + sx * 2 + 1, cy + sy * 2 + 1, BRASS);
        }
    }

    public static void heading(GuiGraphics g, Font font, Component title, int x, int y, int w) {
        g.fill(x + 6, y + 6, x + w - 6, y + 27, SURFACE);
        rule(g, x + 8, y + 26, w - 16);
        g.drawString(font, clipped(font, title, w - 28), x + 14, y + 12, TEXT_WARM, false);
    }

    public static void dialog(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h);
        g.fill(x + 6, y + 6, x + w - 6, y + h - 6, SURFACE);
    }

    /** A recessed area. */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE_LIGHT);
        g.fill(x, y, x + w - 1, y + h - 1, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, INSET);
    }

    public static void row(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hover) {
        g.fill(x, y, x + w, y + h, selected ? ROW_SELECTED : hover ? ROW_HOVER : SURFACE);
        if (selected) {
            g.fill(x, y, x + 2, y + h, BRASS_LIGHT);
            g.fill(x + 2, y, x + w, y + 1, BRASS_DARK);
        }
    }

    public static void slot(GuiGraphics g, int x, int y) {
        // Nine-slice the 16px source into an 18px slot: keep the one-pixel frame crisp and give items a 16px interior.
        g.blit(SLOT, x, y, 0, 0, 1, 1, 16, 16);
        g.blit(SLOT, x + 1, y, 16, 1, 1, 0, 14, 1, 16, 16);
        g.blit(SLOT, x + 17, y, 15, 0, 1, 1, 16, 16);
        g.blit(SLOT, x, y + 1, 1, 16, 0, 1, 1, 14, 16, 16);
        g.blit(SLOT, x + 1, y + 1, 16, 16, 1, 1, 14, 14, 16, 16);
        g.blit(SLOT, x + 17, y + 1, 1, 16, 15, 1, 1, 14, 16, 16);
        g.blit(SLOT, x, y + 17, 0, 15, 1, 1, 16, 16);
        g.blit(SLOT, x + 1, y + 17, 16, 1, 1, 15, 14, 1, 16, 16);
        g.blit(SLOT, x + 17, y + 17, 15, 15, 1, 1, 16, 16);
    }

    public static void rule(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, EDGE);
        g.fill(x, y + 1, x + width, y + 2, EDGE_LIGHT);
    }

    public static String clipped(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getString();
        return font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("..."))) + "...";
    }

    public static int drawWrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int n = Math.min(lines.size(), Math.max(0, maxLines));
        for (int i = 0; i < n; i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
        return n;
    }

    /** A horizontal bar filled to {@code value} (0..1). */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float value, int color) {
        well(g, x - 1, y - 1, w + 2, h + 2);
        int filled = Math.round(w * Mth.clamp(value, 0F, 1F));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
            if (h > 2) g.fill(x, y, x + filled, y + 1, lighten(color, 40));
        }
    }

    /** A time bar that warms from brass to red as it empties. */
    public static void timeBar(GuiGraphics g, int x, int y, int w, int h, float remaining) {
        bar(g, x, y, w, h, remaining, remaining > 0.25F ? BRASS_LIGHT : missColor());
        for (int i = 1; i < 4; i++) g.fill(x + w * i / 4, y, x + w * i / 4 + 1, y + h, 0x80302A24);
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

    public static Button button(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        return new StoneButton(x, y, width, height, label, onPress);
    }

    private static final class StoneButton extends Button {
        private StoneButton(int x, int y, int width, int height, Component label, OnPress onPress) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean hot = active && isHoveredOrFocused();
            g.fill(x, y, x + getWidth(), y + getHeight(), EDGE);
            g.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1, hot ? BRASS : BORDER);
            g.fill(x + 2, y + 2, x + getWidth() - 2, y + getHeight() - 2, !active ? INSET : hot ? ROW_HOVER : SURFACE);
            g.fill(x + 2, y + 2, x + getWidth() - 2, y + 3, active ? EDGE_LIGHT : BORDER);
            Font font = Minecraft.getInstance().font;
            g.drawCenteredString(font, clipped(font, getMessage(), getWidth() - 8), x + getWidth() / 2, y + (getHeight() - 8) / 2,
                    active ? TEXT : 0xFF827B6E);
        }
    }

    public static int goodColor() {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) return 0xFFFFFFFF;
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFF3D8BFF : 0xFF6CCB5F;
    }

    public static int missColor() {
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_COLORS) ? 0xFFFF9F1C : 0xFFE05A4A;
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
