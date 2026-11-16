package com.otectus.immersivejewelry.client.workstation.render;

import com.mojang.math.Axis;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The bench tools, built from a few parts in pixels: their working end at {@code at}, their handle reaching back along
 * {@code back} towards the worker. Small, readable silhouettes: the tip and the contact matter more than detail.
 *
 * <p>Each tool is drawn by its GeckoLib rig ({@link JewelryRigs#tool}: articulated jaws and arms, splaying bristles, a
 * squashing pad) in the same frame; the plain parts below are the fallback when the rig is unavailable.</p>
 */
public final class Tools {
    private static final ResourceLocation STEEL = ImmersiveJewelry.id("block/bench_steel");
    private static final ResourceLocation BRASS = ImmersiveJewelry.id("block/bench_brass");
    private static final ResourceLocation WOOD = ImmersiveJewelry.id("block/bench_wood");
    private static final ResourceLocation LEATHER = ImmersiveJewelry.id("block/bench_leather");

    /** Pliers: two jaws round the tip that close as {@code open} goes to 0, two handles behind the pivot. */
    public static void pliers(Geo geo, Vector3f at, Vector3f back, float open, boolean fine) {
        geo.pose().pushPose();
        geo.pose().translate(at.x, at.y, at.z);
        geo.pose().mulPose(new Quaternionf().rotationTo(new Vector3f(0F, 0F, -1F), new Vector3f(back).negate()));
        if (JewelryRigs.tool(geo, JewelryRigs.Tool.PLIERS, open, false, fine)) {
            geo.pose().popPose();
            return;
        }
        float jaw = Mth.clamp(open, 0F, 1F) * 22F + 3F;
        geo.texture(STEEL);
        for (int side = -1; side <= 1; side += 2) {
            geo.pose().pushPose();
            geo.pose().translate(0F, 0F, 1.4F);
            geo.pose().mulPose(Axis.YP.rotationDegrees(side * jaw));
            geo.plain();
            blade(geo, side * 0.08F, -1.4F, 0.08F, side * 0.15F, -0.35F, 0.23F, 0.14F, 0xFFBAC4C7);
            blade(geo, side * 0.15F, -0.35F, 0.23F, side * 0.15F, 0.35F, 0.27F, 0.18F, 0xFF9DAAAF);
            geo.texture(STEEL);
            geo.box(-0.15F + side * 0.15F, -0.18F, 0F, 0.15F + side * 0.15F, 0.18F, 3.4F, fine ? 0xFFB8C8D8 : 0xFFFFFFFF);
            geo.pose().popPose();
        }
        geo.plain();
        geo.cylinder(0F, 1.4F, 0.35F, -0.23F, 0.23F, 12, 0xFF6A7479);
        geo.cylinder(0F, 1.4F, 0.19F, 0.23F, 0.27F, 10, 0xFFC2CCCF);
        geo.box(-0.13F, 0.271F, 1.375F, 0.13F, 0.28F, 1.425F, 0xFF4C5458);
        geo.texture(LEATHER);
        for (int side = -1; side <= 1; side += 2) {
            geo.pose().pushPose();
            geo.pose().translate(0F, 0F, 1.4F);
            geo.pose().mulPose(Axis.YP.rotationDegrees(side * jaw));
            geo.box(-0.2F + side * 0.15F, -0.26F, 2.2F, 0.2F + side * 0.15F, 0.26F, 3.4F, fine ? 0xFF526A80 : 0xFF864A37);
            geo.pose().popPose();
        }
        geo.plain();
        geo.pose().popPose();
    }

    /** Work in a stable tool frame: the tip is the origin and the handle extends along +z. */
    private static void orient(Geo geo, Vector3f at, Vector3f back) {
        geo.pose().pushPose();
        geo.pose().translate(at.x, at.y, at.z);
        geo.pose().mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f(back).normalize()));
    }

    /** A flattened, tapered blade, with a defined broad face and narrow working edge. */
    private static void blade(Geo geo, float x0, float z0, float w0, float x1, float z1, float w1, float h, int color) {
        Vector3f a = new Vector3f(x0 - w0, -h, z0), b = new Vector3f(x0 + w0, -h, z0);
        Vector3f c = new Vector3f(x1 + w1, -h, z1), d = new Vector3f(x1 - w1, -h, z1);
        Vector3f e = new Vector3f(a).add(0, h * 2, 0), f = new Vector3f(b).add(0, h * 2, 0);
        Vector3f g = new Vector3f(c).add(0, h * 2, 0), j = new Vector3f(d).add(0, h * 2, 0);
        geo.face(a, b, c, d, Geo.scale(color, 0.85F));
        geo.face(j, g, f, e, color);
        geo.face(b, a, e, f, color);
        geo.face(d, c, g, j, color);
        geo.face(a, d, j, e, Geo.scale(color, 0.9F));
        geo.face(c, b, f, g, Geo.scale(color, 0.9F));
    }

    public static void tweezers(Geo geo, Vector3f at, Vector3f back, float open) {
        orient(geo, at, back);
        if (JewelryRigs.tool(geo, JewelryRigs.Tool.TWEEZERS, open, false, false)) {
            geo.pose().popPose();
            return;
        }
        float gap = 0.07F + Mth.clamp(open, 0F, 1F) * 0.3F;
        geo.plain();
        for (int side : new int[]{-1, 1}) {
            blade(geo, side * gap, 0F, 0.035F, side * 0.24F, 1.45F, 0.09F, 0.055F, 0xFFBFCACF);
            blade(geo, side * 0.24F, 1.45F, 0.09F, 0F, 3.2F, 0.12F, 0.055F, 0xFFABB7BE);
            for (int i = 0; i < 4; i++) {
                float z = 1.65F + i * 0.17F;
                float x = side * (0.24F * (3.2F - z) / 1.75F);
                geo.box(x - 0.085F, 0.056F, z, x + 0.085F, 0.065F, z + 0.025F, 0xFF748189);
            }
        }
        geo.pose().popPose();
    }

    public static void pusher(Geo geo, Vector3f at, Vector3f back) {
        orient(geo, at, back);
        if (JewelryRigs.tool(geo, JewelryRigs.Tool.PUSHER, 0F, false, false)) {
            geo.pose().popPose();
            return;
        }
        geo.plain();
        blade(geo, 0F, 0F, 0.24F, 0F, 0.65F, 0.1F, 0.07F, 0xFFBCC6CA);
        geo.rod(new Vector3f(0, 0, 0.6F), new Vector3f(0, 0, 1.4F), 0.19F, 0xFFABB7BE);
        handle(geo, 1.2F, 3.4F, 0.3F);
        geo.pose().popPose();
    }

    public static void burnisher(Geo geo, Vector3f at, Vector3f back) {
        orient(geo, at, back);
        if (JewelryRigs.tool(geo, JewelryRigs.Tool.BURNISHER, 0F, false, false)) {
            geo.pose().popPose();
            return;
        }
        geo.plain();
        // A rounded, bent polished nose distinguishes this from the flat bezel pusher.
        geo.rod(new Vector3f(0, 0, 0.04F), new Vector3f(0, 0.16F, 0.55F), 0.24F, 0xFFD3DDE0);
        geo.rod(new Vector3f(0, 0.16F, 0.55F), new Vector3f(0, 0, 1.65F), 0.2F, 0xFFB9C6CE);
        geo.cylinder(0F, 0.1F, 0.13F, -0.08F, 0.08F, 10, 0xFFE0E8E9);
        handle(geo, 1.5F, 3.6F, 0.26F);
        geo.pose().popPose();
    }

    private static void handle(Geo geo, float start, float end, float radius) {
        geo.pose().pushPose();
        geo.pose().mulPose(Axis.XP.rotationDegrees(90F));
        geo.texture(BRASS);
        geo.cylinder(0F, 0F, radius * 0.72F, start, start + 0.32F, 10, 0xFFE0CEAE);
        geo.texture(WOOD);
        geo.ring(0F, 0F, radius * 0.7F, radius, start + 0.27F, start + 0.75F, 10, 0xFFFFFFFF, true);
        geo.ring(0F, 0F, radius, radius * 0.8F, start + 0.75F, end, 10, 0xFFFFFFFF, true);
        geo.pose().popPose();
        geo.plain();
    }

    public static void brush(Geo geo, Vector3f at, Vector3f back, boolean dabbing) {
        orient(geo, at, back);
        if (JewelryRigs.tool(geo, JewelryRigs.Tool.BRUSH, 0F, dabbing, false)) {
            geo.pose().popPose();
            return;
        }
        geo.plain();
        float spread = dabbing ? 0.3F : 0.2F;
        for (int i = -2; i <= 2; i++) {
            for (int row : new int[]{-1, 1}) {
                geo.rod(new Vector3f(i * spread * 0.5F, row * 0.07F, 0.03F + Math.abs(i) * 0.025F),
                        new Vector3f(i * 0.065F, row * 0.055F, 0.72F), 0.065F,
                        (i + row) % 2 == 0 ? 0xFF5B4533 : 0xFF3A3028);
            }
        }
        geo.texture(BRASS);
        geo.box(-0.22F, -0.13F, 0.65F, 0.22F, 0.13F, 1.1F, 0xFFE0CEAE);
        geo.texture(WOOD);
        geo.rod(new Vector3f(0, 0, 1.05F), new Vector3f(0, 0, 3.2F), 0.3F, 0xFFFFFFFF);
        geo.plain();
        geo.pose().popPose();
    }

    /** A folded cloth pad pressed under the fingers; it squashes while rubbing. */
    public static void cloth(Geo geo, Vector3f at, boolean rubbing) {
        geo.pose().pushPose();
        geo.pose().translate(at.x, at.y, at.z);
        boolean drawn = JewelryRigs.tool(geo, JewelryRigs.Tool.CLOTH, 0F, rubbing, false);
        geo.pose().popPose();
        if (drawn) return;
        float h = rubbing ? 0.18F : 0.3F;
        geo.plain();
        geo.centred(at.x, at.y, at.z - 0.25F, 0.85F, 0.85F, h, 0xFFE8E0D0);
        geo.plain();
        geo.centred(at.x + 0.2F, at.y - 0.15F, at.z - 0.25F - h, 0.6F, 0.6F, 0.05F, 0xFFD0C4B0);
    }

    /** A tool lying in its place on the rack. */
    public static void resting(Geo geo, BenchPainter.Tool tool, Vector3f at) {
        Vector3f back = new Vector3f(0F, 0F, 1F);
        Vector3f tip = new Vector3f(at.x, at.y, at.z - 1.4F);
        switch (tool) {
            case TWEEZERS -> tweezers(geo, tip, back, 0.4F);
            case PUSHER -> pusher(geo, tip, back);
            case BURNISHER -> burnisher(geo, tip, back);
            case BRUSH -> brush(geo, tip, back, false);
            case CLOTH -> {
                geo.plain();
                geo.box(at.x - 0.9F, at.y - 0.4F, at.z - 0.9F, at.x + 0.9F, at.y - 0.05F, at.z + 0.9F, 0xFFE8E0D0);
            }
            default -> {
            }
        }
    }

    private Tools() {}
}
