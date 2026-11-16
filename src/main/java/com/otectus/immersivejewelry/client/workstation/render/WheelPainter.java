package com.otectus.immersivejewelry.client.workstation.render;

import com.mojang.math.Axis;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.client.JewelryColors;
import com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.workstation.WheelOperations;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * The Lapidary Wheel's moving parts in scene pixels (north-facing block space; the worker stands at -z and sees +x on
 * the left): the flat lap spinning in its pan, the quill arm swinging from the mast, the dop with its index collar and
 * handle, the stone itself, the specimen rack, the cooling cup, the loupe and the release lever. The same rig gives
 * the behaviour where the stone touches the lap, so particles, sounds and pointer mapping agree with the picture.
 *
 * <p>The quill: sideways handle motion swings the arm about the mast (the stone sweeps across the lap); lowering it
 * brings the stone down until it meets the lap at half travel, after which the handle compresses its spring (force)
 * while the stone stays on the lap. Lifted, the quill tips the stone up towards the worker so the face just cut catches
 * the light; lowered, the collar's chosen region faces the lap.</p>
 */
public final class WheelPainter {
    public static final Vector3f LAP = new Vector3f(9.0F, 12.3F, 8.5F);
    public static final float LAP_RADIUS = 5.0F;
    public static final Vector3f PIVOT = new Vector3f(1.8F, 17.2F, 11.3F);
    /** Where the stone meets the lap with the handle centred. */
    public static final Vector3f CONTACT = new Vector3f(8.2F, 12.3F, 6.2F);
    public static final float LIFT = 3.0F;
    public static final float SWEEP_DEGREES = 20F;
    public static final Vector3f CUP = new Vector3f(2.0F, 12.4F, 2.0F);
    public static final Vector3f RACK = new Vector3f(14.0F, 12.4F, 1.6F);
    public static final Vector3f LOUPE = new Vector3f(1.3F, 14.6F, 5.4F);
    public static final Vector3f LEVER = new Vector3f(1.8F, 13.6F, 10.0F);
    public static final ResourceLocation LAP_TEXTURE = ImmersiveJewelry.id("block/wheel_lap");
    public static final ResourceLocation BRASS = ImmersiveJewelry.id("block/bench_brass");
    public static final ResourceLocation STEEL = ImmersiveJewelry.id("block/bench_steel");
    public static final ResourceLocation WOOD = ImmersiveJewelry.id("block/wheel_wood");
    public static final ResourceLocation WATER = ImmersiveJewelry.id("block/wheel_water");
    private static final Vector3f CAMERA = new Vector3f(8.0F, 26.5F, -7.5F);

    /** What to draw: the stone and its work, the handle state, the moment's extras. */
    public record State(@Nullable GemData stone, @Nullable CutWork work, int lapColor, float lapAngle, float sweep, float height, float dip,
                        int region, float trim, @Nullable ResourceLocation ghostCut, List<ResourceLocation> specimens, int hoveredSpecimen,
                        float[] glints, boolean showGuide, float lever, boolean hands, long stoneSeed,
                        @Nullable GemProgress.Snapshot presentation, float stoneSize) {}

    public static float stoneSize(@Nullable GemData data) {
        return data == null ? 1F : 0.95F + Mth.clamp(data.size(), 0.2F, 2.5F) * 0.42F;
    }

    // ------------------------------------------------------------------------------------------------ kinematics

    /** Where the stone's working face is, for a handle at {@code sweep} (-1..1), {@code height} (0..1) and a dip (0..1). */
    public static Vector3f stoneAt(float sweep, float height, float dip) {
        float yaw = sweep * SWEEP_DEGREES * Mth.DEG_TO_RAD;
        Vector3f arm = new Vector3f(CONTACT).sub(PIVOT);
        float c = Mth.cos(yaw), s = Mth.sin(yaw);
        Vector3f swung = new Vector3f(arm.x * c - arm.z * s, arm.y, arm.x * s + arm.z * c).add(PIVOT);
        float lift = Math.max(0F, (height - FacetSim.CONTACT_HEIGHT) / (1F - FacetSim.CONTACT_HEIGHT)) * LIFT;
        swung.add(0F, lift, 0F);
        if (dip > 0F) {
            Vector3f cup = new Vector3f(CUP).add(0F, 0.4F, 0F);
            float t = Mth.clamp(dip, 0F, 1F);
            swung.lerp(cup, t * t * (3F - 2F * t));
        }
        return swung;
    }

    /** How far the handle sinks below contact as the spring takes the force (visual only). */
    public static float springTravel(float height) {
        return Math.max(0F, (FacetSim.CONTACT_HEIGHT - height) / FacetSim.CONTACT_HEIGHT) * 0.9F;
    }

    private static Quaternionf stoneRotation(float regionAngle, float tilt, float show, Vector3f at) {
        // Cutting: the chosen region's facet normal points straight down at the lap, the dop leaning to the mast.
        Quaternionf cut = new Quaternionf().rotateY((float) Math.atan2(PIVOT.x - at.x, PIVOT.z - at.z) - Mth.HALF_PI)
                .rotateZ(tilt - Mth.PI).rotateY(-regionAngle);
        // Lifted: the crown faces the worker, the same region at the bottom of the view.
        Vector3f view = new Vector3f(CAMERA).sub(at).normalize();
        Quaternionf face = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), view).rotateY(-regionAngle - Mth.HALF_PI);
        return cut.slerp(face, Mth.clamp(show, 0F, 1F));
    }

    // ------------------------------------------------------------------------------------------------ drawing

    /** The exact rendered hardware locations, also used by the moving interaction targets. */
    public record Rig(Vector3f stone, Vector3f collar, Vector3f grip) {}

    public static Rig draw(Geo geo, State s, float time) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        // The lap, its hub and the release lever are the wheel's GeckoLib rig; plain parts only if it is unavailable.
        boolean rig = JewelryRigs.wheel(geo, s.lapAngle(), s.lapColor(), s.lever());
        lap(geo, s, rig);
        rack(geo, s, defs);
        cup(geo, s, time);
        loupe(geo);
        if (!rig) lever(geo, s.lever());
        return quill(geo, s, defs);
    }

    private static void lap(Geo geo, State s, boolean rigged) {
        geo.pose().pushPose();
        geo.pose().translate(LAP.x, 0F, LAP.z);
        geo.pose().mulPose(Axis.YP.rotation(s.lapAngle()));
        if (!rigged) {
            geo.texture(LAP_TEXTURE);
            geo.ring(0F, 0F, LAP_RADIUS, LAP_RADIUS, LAP.y - 0.5F, LAP.y, 40, Geo.opaque(s.lapColor()), true);
            // The steel spindle nut.
            geo.plain();
            geo.cylinder(0F, 0F, 0.7F, LAP.y, LAP.y + 0.35F, 10, 0xFF8E9298);
        }
        // A worn track where the stone runs.
        geo.plain();
        if (s.work() != null && s.work().started()) {
            float track = new Vector3f(CONTACT).sub(LAP).length();
            int stain = Geo.argb(0xFF, Geo.scale(Geo.opaque(s.lapColor()), 0.72F));
            geo.annulus(0F, 0F, track - 0.55F, track + 0.55F, LAP.y + 0.01F, 40, stain);
        }
        geo.pose().popPose();
    }

    private static void rack(Geo geo, State s, JewelryDefinitions defs) {
        geo.texture(WOOD);
        geo.box(RACK.x - 2.6F, 11F, RACK.z - 0.9F, RACK.x + 2.0F, 11.9F, RACK.z + 0.9F, 0xFFFFFFFF);
        if (s.stone() == null) return;
        int color = JewelryColors.gemColor(defs, s.stone().gem());
        GemDefinition gem = defs.gemOrGeneric(s.stone().gem());
        List<ResourceLocation> cuts = s.specimens();
        for (int i = 0; i < cuts.size() && i < SPECIMENS; i++) {
            Vector3f at = specimen(i, cuts.size());
            CutDefinition cut = defs.cut(cuts.get(i));
            if (cut == null) continue;
            geo.plain();
            geo.box(at.x - 0.12F, 11.9F, at.z - 0.12F, at.x + 0.12F, at.y - 0.35F, at.z + 0.12F, 0xFF8E9298);
            boolean planned = s.work() != null && s.work().cut().equals(cut.id());
            boolean hovered = i == s.hoveredSpecimen();
            geo.pose().pushPose();
            geo.pose().translate(at.x, at.y + (hovered ? 0.25F : 0F), at.z);
            geo.pose().mulPose(Axis.XP.rotationDegrees(-35F));
            GemGeometry.Profile profile = GemGeometry.Profile.of(cut.visualProfile());
            Polyhedron shape = GemGeometry.finished(profile, cut.id().hashCode(), cut.facets());
            GemRenderer.draw(geo, shape, 0.42F, color, gem.opaque(), profile == GemGeometry.Profile.CABOCHON, planned || hovered ? 0.9F : 0.5F,
                    GemRenderer.Regions.NONE);
            geo.pose().popPose();
            if (planned) {
                geo.plain();
                geo.box(at.x - 0.45F, 11.92F, at.z - 0.55F, at.x + 0.45F, 11.98F, at.z - 0.4F, 0xFFE8C46A);
            }
        }
    }

    /** The rack's specimen positions (fixed, so the scene's pick zones line up): at most {@value #SPECIMENS}. */
    public static final int SPECIMENS = 6;

    public static Vector3f specimen(int i, int n) {
        return new Vector3f(RACK.x + 1.9F - 0.76F * i, RACK.y, RACK.z);
    }

    private static void cup(Geo geo, State s, float time) {
        geo.texture(ImmersiveJewelry.id("block/wheel_copper"));
        geo.ring(CUP.x, CUP.z, 1.3F, 1.45F, 11F, 12.7F, 16, 0xFFFFFFFF, false);
        // A shallow dark interior anchors the water instead of leaving the cup visually hollow through the table.
        geo.plain();
        geo.cylinder(CUP.x, CUP.z, 1.28F, 11.05F, 11.16F, 16, 0xFF33484B);
        geo.translucentTexture(WATER);
        float ripple = s.dip() > 0F && !ClientConfig.get(ClientConfig.REDUCED_MOTION)
                ? Mth.sin(time * 1.4F) * 0.05F * s.dip() : 0F;
        geo.annulus(CUP.x, CUP.z, 0F, 1.3F, 12.35F + ripple, 16, 0xC8FFFFFF);
        // A thin rim and a restrained ripple keep the cooling action readable at the work surface.
        geo.plain();
        geo.annulus(CUP.x, CUP.z, 1.3F, 1.45F, 12.7F, 16, 0xFFC8936B);
        if (s.dip() > 0F) {
            geo.glass();
            float ring = 0.35F + 0.8F * s.dip();
            geo.annulus(CUP.x, CUP.z, ring - 0.035F, ring, 12.38F + ripple, 24,
                    Geo.argb(Math.round(130F * Mth.sin(s.dip() * Mth.PI)), 0xC4E1DF));
            geo.plain();
        }
    }

    private static void loupe(Geo geo) {
        geo.texture(BRASS);
        geo.box(LOUPE.x - 0.3F, 11F, LOUPE.z - 0.3F, LOUPE.x + 0.3F, LOUPE.y - 0.6F, LOUPE.z + 0.3F, 0xFFFFFFFF);
        geo.pose().pushPose();
        geo.pose().translate(LOUPE.x, LOUPE.y, LOUPE.z);
        geo.pose().mulPose(Axis.ZP.rotationDegrees(70F));
        geo.ring(0F, 0F, 0.75F, 0.75F, -0.2F, 0.2F, 14, 0xFFFFFFFF, false);
        geo.glass();
        geo.annulus(0F, 0F, 0F, 0.7F, 0F, 14, 0x80CFE8F0);
        geo.pose().popPose();
        geo.plain();
    }

    private static void lever(Geo geo, float pulled) {
        geo.texture(BRASS);
        geo.pose().pushPose();
        geo.pose().translate(LEVER.x, LEVER.y, LEVER.z);
        geo.pose().mulPose(Axis.XP.rotationDegrees(-25F - 50F * pulled));
        geo.box(-0.15F, 0F, -0.15F, 0.15F, 1.6F, 0.15F, 0xFFFFFFFF);
        geo.plain();
        geo.centred(0F, 1.7F, 0F, 0.3F, 0.3F, 0.3F, 0xFF6A3A22);
        geo.pose().popPose();
    }

    private static Rig quill(Geo geo, State s, JewelryDefinitions defs) {
        Vector3f contact = stoneAt(s.sweep(), s.height(), s.dip());
        float show = Math.max(s.dip(), Mth.clamp((s.height() - FacetSim.CONTACT_HEIGHT) / (1F - FacetSim.CONTACT_HEIGHT) * 1.6F, 0F, 1F));
        show = show * show * (3F - 2F * show);

        // The stone and how it sits: its lowest point rests on the working position.
        GemData data = s.stone();
        CutWork work = s.work();
        float size = s.stoneSize();
        Polyhedron shape = null;
        GemGeometry.Profile profile = GemGeometry.Profile.ROUGH;
        float tilt = 48F * Mth.DEG_TO_RAD;
        float regionAngle = 0F;
        int regions = work != null ? work.params().regions() : 0;
        if (data != null) {
            if (s.presentation() != null) {
                var shown = s.presentation();
                CutDefinition cut = defs.cut(work != null ? work.cut() : data.cut());
                profile = cut != null ? GemGeometry.Profile.of(cut.visualProfile()) : GemGeometry.Profile.ROUND;
                float[] targets = new float[shown.ratios().length];
                java.util.Arrays.fill(targets, 1F);
                shape = GemGeometry.shape(profile, s.stoneSeed(), shown.ratios(), targets, shown.chips(), shown.progress());
                regionAngle = shown.indexAngle();
                regions = shown.ratios().length;
                if (profile == GemGeometry.Profile.BRILLIANT) tilt = 42F * Mth.DEG_TO_RAD;
            } else if (work != null) {
                CutDefinition cut = defs.cut(work.cut());
                profile = cut != null ? GemGeometry.Profile.of(cut.visualProfile()) : GemGeometry.Profile.ROUND;
                FacetSim sim = work.sim();
                float[] depth = new float[regions];
                float[] target = new float[regions];
                int chips = 0;
                for (int i = 0; i < regions; i++) {
                    depth[i] = sim.depth(i);
                    target[i] = work.params().target()[i];
                    if (sim.chip(i) > 0F) chips |= 1 << i;
                }
                shape = GemGeometry.shape(profile, s.stoneSeed(), depth, target, chips, sim.overallProgress());
                regionAngle = Mth.TWO_PI * s.region() / Math.max(1, regions);
                if (profile == GemGeometry.Profile.BRILLIANT) tilt = 42F * Mth.DEG_TO_RAD;
            } else if (data.isCut()) {
                CutDefinition cut = defs.cut(data.cut());
                profile = cut != null ? GemGeometry.Profile.of(cut.visualProfile()) : GemGeometry.Profile.ROUND;
                shape = GemGeometry.finished(profile, s.stoneSeed(), cut != null ? cut.facets() : 6, GemRenderer.damageMask(data.damage()));
            } else {
                shape = GemGeometry.rough(s.stoneSeed());
            }
        }
        Quaternionf rotation = stoneRotation(regionAngle, tilt, show, contact);
        float reach = shape == null ? 0.8F : shape.support(new Vector3f(0F, -1F, 0F).rotate(new Quaternionf(rotation).conjugate())) * size;
        Vector3f centre = new Vector3f(contact).add(0F, reach, 0F);

        // The dop leans from the stone towards the mast; the collar turns on it, the handle sits at its top.
        Vector3f dopDir = new Vector3f(0F, -1F, 0F).rotate(rotation).normalize();
        float backReach = shape == null ? size * 0.7F : shape.surfaceRadius(new Vector3f(0F, -1F, 0F)) * size;
        Vector3f dopBase = new Vector3f(centre).add(new Vector3f(dopDir).mul(backReach - 0.06F));
        Vector3f dopTop = new Vector3f(dopBase).add(new Vector3f(dopDir).mul(2.7F));
        float spring = springTravel(s.height());
        Vector3f handleBase = new Vector3f(dopTop).add(0F, -spring * 0.5F, 0F);
        Vector3f handleEnd = new Vector3f(handleBase).add(-1.7F, 0.5F - spring, -1.7F);

        geo.texture(STEEL);
        geo.rod(PIVOT, new Vector3f(dopTop).add(0F, 0.2F, 0F), 0.75F, 0xFFFFFFFF);
        geo.rod(dopBase, dopTop, 0.3F, 0xFFFFFFFF);
        // A small wax seat makes the fixed bond between the stone and its dop visible.
        geo.plain();
        geo.rod(new Vector3f(dopBase).sub(new Vector3f(dopDir).mul(0.06F)),
                new Vector3f(dopBase).add(new Vector3f(dopDir).mul(0.18F)), 0.42F, 0xFF6F563B);
        Vector3f collar = new Vector3f(dopBase).lerp(dopTop, 0.6F);
        geo.texture(BRASS);
        geo.pose().pushPose();
        geo.pose().translate(collar.x, collar.y, collar.z);
        geo.pose().mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dopDir));
        geo.ring(0F, 0F, 0.85F, 0.85F, -0.3F, 0.3F, 16, 0xFFFFFFFF, true);
        geo.plain();
        for (int i = 0; i < regions; i++) {
            float a = Mth.TWO_PI * i / regions + s.trim() * 0.02F;
            int c = i == s.region() ? 0xFFFFF2B0 : 0xFF3C3226;
            geo.centred(Mth.cos(a) * 0.9F, 0F, Mth.sin(a) * 0.9F, 0.09F, i == s.region() ? 0.34F : 0.22F, 0.09F, c);
        }
        geo.pose().popPose();
        geo.texture(WOOD);
        geo.rod(handleBase, handleEnd, 0.55F, 0xFFFFFFFF);
        geo.plain();
        geo.cylinder(handleEnd.x, handleEnd.z, 0.38F, handleEnd.y - 0.3F, handleEnd.y + 0.3F, 8, 0xFF2E241A);
        if (s.hands()) Hands.hold(geo, handleEnd, new Vector3f(handleEnd).sub(handleBase).normalize(), true);

        Rig rig = new Rig(centre, collar, handleEnd);
        if (data == null || shape == null) return rig;
        int color = JewelryColors.gemBody(defs, data);
        GemDefinition gem = defs.gemOrGeneric(data.gem());
        geo.pose().pushPose();
        geo.pose().translate(centre.x, centre.y, centre.z);
        geo.pose().mulPose(rotation);
        GemRenderer.draw(geo, shape, size, color, gem.opaque(), profile == GemGeometry.Profile.CABOCHON,
                data.isCut() ? data.cutQuality() : 0.3F, work != null ? new Glints(s, work) : GemRenderer.damage(data.damage()));
        if (s.showGuide()) {
            ResourceLocation ghost = s.ghostCut() != null ? s.ghostCut() : work != null ? work.cut() : null;
            CutDefinition cut = defs.cut(ghost);
            if (cut != null) {
                GemGeometry.Profile p = GemGeometry.Profile.of(cut.visualProfile());
                GemRenderer.guide(geo, GemGeometry.finished(p, s.stoneSeed(), cut.facets()), size, 0xB0F4D27A);
            }
        }
        geo.pose().popPose();
        return rig;
    }

    /** Initial pick position before the first rendered pose. Roughly where the index collar sits on the dop (its pick zone), for a handle state. */
    public static Vector3f collarAt(float sweep, float height) {
        Vector3f contact = stoneAt(sweep, height, 0F);
        Vector3f toPivot = new Vector3f(PIVOT).sub(contact);
        Vector3f horizontal = new Vector3f(toPivot.x, 0F, toPivot.z).normalize();
        Vector3f dopDir = new Vector3f(horizontal).mul(0.64F).add(0F, 0.77F, 0F).normalize();
        return new Vector3f(contact).add(0F, 1F, 0F).add(new Vector3f(dopDir).mul(2.0F));
    }

    /** Nominal handle path for initial target placement and drag calibration; live targets use the rendered Rig. */
    public static Vector3f handleGrip(float sweep, float height, float dip) {
        Vector3f contact = stoneAt(sweep, height, dip);
        Vector3f toPivot = new Vector3f(PIVOT).sub(contact);
        Vector3f horizontal = new Vector3f(toPivot.x, 0F, toPivot.z).normalize();
        Vector3f dopDir = new Vector3f(horizontal).mul(0.64F).add(0F, 0.77F, 0F).normalize();
        Vector3f dopTop = new Vector3f(contact).add(0F, 1F, 0F).add(new Vector3f(dopDir).mul(3.1F));
        float spring = springTravel(height);
        return new Vector3f(dopTop).add(-1.7F, 0.5F - spring * 1.5F, -1.7F);
    }

    /** Region looks from the work: glints after a face is done, scratches, the window, the chosen region. */
    private record Glints(State s, @Nullable CutWork work) implements GemRenderer.Regions {
        @Override
        public float glint(int region) {
            return region < s.glints().length ? s.glints()[region] : 0F;
        }

        @Override
        public float scratch(int region) {
            return work != null && region < work.params().regions() ? work.sim().scratch(region) + work.sim().rock(region) * 0.5F : 0F;
        }

        @Override
        public boolean inWindow(int region) {
            if (work == null || region >= work.params().regions()) return false;
            float depth = displayedDepth(region);
            return Math.abs(depth - work.params().target()[region]) <= work.params().window();
        }

        @Override
        public boolean over(int region) {
            return work != null && region < work.params().regions()
                    && displayedDepth(region) > work.params().target()[region] + work.params().window();
        }

        private float displayedDepth(int region) {
            return s.presentation() != null && region < s.presentation().ratios().length
                    ? s.presentation().ratios()[region] * work.params().target()[region] : work.sim().depth(region);
        }

        @Override
        public boolean highlighted(int region) {
            return region == s.region() && s.height() > 0.6F;
        }
    }

    // ------------------------------------------------------------------------------------------------ placement hint

    /**
     * Where a stack carried over the wheel would go if let go now (a Diegesis {@code takes_items} node under it): a light
     * band round the empty quill's tip for a stone, round the lap's rim for a lap. Only the operator sees it.
     */
    public static void placeHint(Geo geo, WheelOperations.Placement place, Rig rig, int argb) {
        if (place == WheelOperations.Placement.NONE) return;
        geo.glass();
        if (place == WheelOperations.Placement.QUILL) {
            Vector3f at = rig.stone();
            geo.ring(at.x, at.z, 1.05F, 1.05F, at.y - 0.6F, at.y + 0.6F, 16, argb, false);
        } else {
            geo.ring(LAP.x, LAP.z, LAP_RADIUS + 0.12F, LAP_RADIUS + 0.12F, LAP.y - 0.55F, LAP.y + 0.08F, 40, argb, false);
        }
        geo.plain();
    }

    private WheelPainter() {}
}
