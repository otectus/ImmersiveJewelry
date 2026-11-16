package com.otectus.immersivejewelry.client.workstation.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A small immediate-mode geometry writer for the workstations' moving parts, in pixels. Everything goes through the
 * block atlas with the entity cutout (or translucent) render type: plain coloured surfaces sample the near-white
 * {@code workstation_plain} sprite, wood and metal parts their own block textures, so one draw style serves the Diegesis
 * scene and the fallback block-entity renderer alike and needs no shader features.
 */
public final class Geo {
    public static final ResourceLocation PLAIN = ImmersiveJewelry.id("block/workstation_plain");

    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final int light;
    private TextureAtlasSprite sprite;
    private boolean see;

    public Geo(PoseStack pose, MultiBufferSource buffers, int light) {
        this.pose = pose;
        this.buffers = buffers;
        this.light = light;
        this.sprite = sprite(PLAIN);
    }

    public PoseStack pose() { return pose; }
    public int light() { return light; }
    public MultiBufferSource buffers() { return buffers; }

    public static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
    }

    /** Plain colour from here on (opaque). */
    public Geo plain() {
        sprite = sprite(PLAIN);
        see = false;
        return this;
    }

    /** A block texture from here on. */
    public Geo texture(ResourceLocation id) {
        sprite = sprite(id);
        see = false;
        return this;
    }

    /** A textured surface whose alpha blends with the work behind it (water, glass). */
    public Geo translucentTexture(ResourceLocation id) {
        texture(id);
        see = true;
        return this;
    }

    /** See-through plain colour from here on (gems when translucency is enabled, ghost guides). */
    public Geo glass() {
        sprite = sprite(PLAIN);
        see = true;
        return this;
    }

    private VertexConsumer out() {
        // BufferSource can end its shared builder on a render-type change. Reacquire for every primitive, including
        // when our own glass/solid surfaces alternate; a cached consumer may belong to an already finished batch.
        return buffers.getBuffer(see ? RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)
                : RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
    }

    /** One quad, corners counter-clockwise seen from outside, with a face normal and texture-space UVs 0..1. */
    public void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f normal, int argb, float u0, float v0, float u1, float v1) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vector3f nn = n.transform(new Vector3f(normal)).normalize();
        VertexConsumer vc = out();
        vertex(vc, m, a, nn, argb, u(u0), v(v1));
        vertex(vc, m, b, nn, argb, u(u1), v(v1));
        vertex(vc, m, c, nn, argb, u(u1), v(v0));
        vertex(vc, m, d, nn, argb, u(u0), v(v0));
    }

    /** One quad with its own normal per corner (smooth shading). */
    public void quadSmooth(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f na, Vector3f nb, Vector3f nc, Vector3f nd, int argb) {
        quadSmooth(a, b, c, d, na, nb, nc, nd, argb, 0.5F, 0.5F, 0.5F, 0.5F);
    }

    private void quadSmooth(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f na, Vector3f nb, Vector3f nc, Vector3f nd,
                            int argb, float u0, float v0, float u1, float v1) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer vc = out();
        vertex(vc, m, a, n.transform(new Vector3f(na)).normalize(), argb, u(u1), v(v1));
        vertex(vc, m, b, n.transform(new Vector3f(nb)).normalize(), argb, u(u0), v(v1));
        vertex(vc, m, c, n.transform(new Vector3f(nc)).normalize(), argb, u(u0), v(v0));
        vertex(vc, m, d, n.transform(new Vector3f(nd)).normalize(), argb, u(u1), v(v0));
    }

    /** One continuous top-down texture across a circular surface, rather than one stretched copy per wedge. */
    private void discQuad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f normal,
                          int argb, float cx, float cz, float radius) {
        Matrix4f m = pose.last().pose();
        Vector3f n = pose.last().normal().transform(new Vector3f(normal)).normalize();
        VertexConsumer vc = out();
        float diameter = Math.max(0.001F, radius * 2F);
        for (Vector3f p : new Vector3f[]{a, b, c, d}) {
            vertex(vc, m, p, n, argb, u(0.5F + (p.x - cx) / diameter), v(0.5F + (p.z - cz) / diameter));
        }
    }

    /** A flat quad of one colour: the normal follows the winding. */
    public void face(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int argb) {
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (normal.lengthSquared() < 1.0E-10F) normal = new Vector3f(d).sub(a).cross(new Vector3f(c).sub(a));
        if (normal.lengthSquared() < 1.0E-10F) normal.set(0, 1, 0);
        quad(a, b, c, d, normal.normalize(), argb, 0F, 0F, 1F, 1F);
    }

    public void triangle(Vector3f a, Vector3f b, Vector3f c, int argb) {
        face(a, b, c, c, argb);
    }

    private float u(float t) {
        return sprite.getU(t * 16F);
    }

    private float v(float t) {
        return sprite.getV(t * 16F);
    }

    private void vertex(VertexConsumer vc, Matrix4f m, Vector3f p, Vector3f n, int argb, float u, float v) {
        vc.vertex(m, p.x, p.y, p.z).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24))
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n.x, n.y, n.z).endVertex();
    }

    // ------------------------------------------------------------------------------------------------ solids

    /** An axis-aligned box from (x0,y0,z0) to (x1,y1,z1), every face textured with the current sprite. */
    public void box(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        Vector3f p000 = new Vector3f(x0, y0, z0), p100 = new Vector3f(x1, y0, z0), p110 = new Vector3f(x1, y1, z0), p010 = new Vector3f(x0, y1, z0);
        Vector3f p001 = new Vector3f(x0, y0, z1), p101 = new Vector3f(x1, y0, z1), p111 = new Vector3f(x1, y1, z1), p011 = new Vector3f(x0, y1, z1);
        float du = Math.min(1F, Math.abs(x1 - x0) / 16F), dv = Math.min(1F, Math.abs(y1 - y0) / 16F), dw = Math.min(1F, Math.abs(z1 - z0) / 16F);
        quad(p100, p000, p010, p110, new Vector3f(0, 0, -1), argb, 0, 0, du, dv);   // north (-z)
        quad(p001, p101, p111, p011, new Vector3f(0, 0, 1), argb, 0, 0, du, dv);    // south (+z)
        quad(p000, p001, p011, p010, new Vector3f(-1, 0, 0), argb, 0, 0, dw, dv);   // west (-x)
        quad(p101, p100, p110, p111, new Vector3f(1, 0, 0), argb, 0, 0, dw, dv);    // east (+x)
        quad(p010, p011, p111, p110, new Vector3f(0, 1, 0), argb, 0, 0, du, dw);    // up
        quad(p001, p000, p100, p101, new Vector3f(0, -1, 0), argb, 0, 0, du, dw);   // down
    }

    /** A box centred on (cx,cy,cz) with half sizes. */
    public void centred(float cx, float cy, float cz, float hx, float hy, float hz, int argb) {
        box(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, argb);
    }

    /** A cylinder along local +y from y0 to y1, with caps. */
    public void cylinder(float cx, float cz, float radius, float y0, float y1, int segments, int argb) {
        ring(cx, cz, radius, radius, y0, y1, segments, argb, true);
    }

    /**
     * A tube or cylinder along +y: {@code r0} at the bottom, {@code r1} at the top (a cone when they differ); with
     * {@code caps} the ends are closed.
     */
    public void ring(float cx, float cz, float r0, float r1, float y0, float y1, int segments, int argb, boolean caps) {
        float slope = (r0 - r1) / Math.max(1.0E-4F, y1 - y0);
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments, a1 = Mth.TWO_PI * (i + 1) / segments;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            Vector3f b0 = new Vector3f(cx + c0 * r0, y0, cz + s0 * r0), b1 = new Vector3f(cx + c1 * r0, y0, cz + s1 * r0);
            Vector3f t0 = new Vector3f(cx + c0 * r1, y1, cz + s0 * r1), t1 = new Vector3f(cx + c1 * r1, y1, cz + s1 * r1);
            Vector3f n0 = new Vector3f(c0, slope, s0).normalize(), n1 = new Vector3f(c1, slope, s1).normalize();
            quadSmooth(b1, b0, t0, t1, n1, n0, n0, n1, argb,
                    (float) i / segments, 0F, (float) (i + 1) / segments, Math.min(1F, (y1 - y0) / 16F));
            if (caps) {
                Vector3f top = new Vector3f(cx, y1, cz), bottom = new Vector3f(cx, y0, cz);
                discQuad(t0, top, top, t1, new Vector3f(0, 1, 0), argb, cx, cz, r1);
                discQuad(b1, bottom, bottom, b0, new Vector3f(0, -1, 0), argb, cx, cz, r0);
            }
        }
    }

    /** A flat annulus in the xz plane at height {@code y}, facing up. */
    public void annulus(float cx, float cz, float inner, float outer, float y, int segments, int argb) {
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments, a1 = Mth.TWO_PI * (i + 1) / segments;
            Vector3f i0 = new Vector3f(cx + Mth.cos(a0) * inner, y, cz + Mth.sin(a0) * inner);
            Vector3f i1 = new Vector3f(cx + Mth.cos(a1) * inner, y, cz + Mth.sin(a1) * inner);
            Vector3f o0 = new Vector3f(cx + Mth.cos(a0) * outer, y, cz + Mth.sin(a0) * outer);
            Vector3f o1 = new Vector3f(cx + Mth.cos(a1) * outer, y, cz + Mth.sin(a1) * outer);
            discQuad(i0, i1, o1, o0, new Vector3f(0, 1, 0), argb, cx, cz, outer);
        }
    }

    /** A thin rod from {@code a} to {@code b} with a square cross-section of {@code thickness}. */
    public void rod(Vector3f a, Vector3f b, float thickness, int argb) {
        Vector3f dir = new Vector3f(b).sub(a);
        float length = dir.length();
        if (length < 1.0E-4F) return;
        dir.div(length);
        Vector3f side = Math.abs(dir.y) < 0.9F ? new Vector3f(0, 1, 0).cross(dir).normalize() : new Vector3f(1, 0, 0).cross(dir).normalize();
        Vector3f up = new Vector3f(dir).cross(side).normalize();
        float h = thickness * 0.5F;
        Vector3f[] corners = new Vector3f[8];
        int k = 0;
        for (Vector3f end : new Vector3f[]{a, b}) {
            for (int[] s : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
                corners[k++] = new Vector3f(end).add(new Vector3f(side).mul(s[0] * h)).add(new Vector3f(up).mul(s[1] * h));
            }
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            Vector3f normal = new Vector3f(corners[j]).sub(corners[i]).cross(dir).normalize();
            float u = Math.min(0.5F, thickness / 32F), v = Math.min(0.5F, length / 32F);
            quad(corners[i], corners[j], corners[4 + j], corners[4 + i], normal, argb, 0.5F - u, 0.5F - v, 0.5F + u, 0.5F + v);
        }
        float uv = Math.min(0.5F, thickness / 32F);
        quad(corners[3], corners[2], corners[1], corners[0], new Vector3f(dir).negate(), argb,
                0.5F - uv, 0.5F - uv, 0.5F + uv, 0.5F + uv);
        quad(corners[4], corners[5], corners[6], corners[7], dir, argb,
                0.5F - uv, 0.5F - uv, 0.5F + uv, 0.5F + uv);
    }

    // ------------------------------------------------------------------------------------------------ colour

    public static int argb(int alpha, int rgb) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0F, 1F);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = a >>> 24;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = b >>> 24;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }

    public static int scale(int argb, float f) {
        int r = Mth.clamp(Math.round(((argb >> 16) & 255) * f), 0, 255);
        int g = Mth.clamp(Math.round(((argb >> 8) & 255) * f), 0, 255);
        int b = Mth.clamp(Math.round((argb & 255) * f), 0, 255);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
