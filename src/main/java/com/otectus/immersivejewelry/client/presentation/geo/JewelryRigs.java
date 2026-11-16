package com.otectus.immersivejewelry.client.presentation.geo;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.client.presentation.PresentationClock;
import com.otectus.immersivejewelry.client.workstation.render.BenchPainter;
import com.otectus.immersivejewelry.client.workstation.render.Geo;
import com.otectus.immersivejewelry.config.ClientConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The stations' and tools' GeckoLib rigs, posed from what the painters already know (synced state, the operator's
 * predicted controls) and drawn in the painters' own frames:
 * <ul>
 *   <li><b>Lapidary wheel</b>: the lap disc turns with the wheel's lap angle (tinted by the lap's tier), a newly installed
 *       lap drops onto the spindle ({@code lap_seat}, from the moment the lap changed), the release lever follows its pull
 *       ({@code lever_pull}).</li>
 *   <li><b>Jeweler's bench</b>: the vise's jaws close on a piece put in the clamp ({@code clamp_close}, from the moment the
 *       clamp changed), stand open on an empty clamp, ease with the piece's relax; the posts for circlets and bracelets
 *       replace the jaws for those forms.</li>
 *   <li><b>Bench tools</b>: the pliers' jaws follow their opening ({@code pliers_squeeze}), the tweezers' arms their gap, the
 *       brush's bristles splay while dabbing ({@code brush_dab}), the cloth's pad squashes while rubbing
 *       ({@code cloth_rub}); the Fine Pliers wear their own texture.</li>
 * </ul>
 * The rig instance is the station being drawn (its block entity is the GeckoLib animatable) or, where there is none, a
 * detached animatable. A late observer's first sight of a station never replays an old lap change or clamp change. Rigs are
 * cosmetic only: no clip marker or pose ever changes work.
 */
public final class JewelryRigs {
    public static final RigRenderer WHEEL = new RigRenderer("lapidary_wheel");
    public static final RigRenderer BENCH = new RigRenderer("jewelers_bench");
    public static final RigRenderer TOOLS = new RigRenderer("bench_tools");
    public static final ResourceLocation FINE_TOOLS = ImmersiveJewelry.id("textures/rig/bench_tools_fine.png");
    private static final String[] WHEEL_BONES = {"root", "lap", "hub", "spindle", "lever"};
    private static final String[] BENCH_BONES = {"root", "vise", "screw", "jaw_front", "jaw_rear", "posts_circlet", "posts_bracelet"};
    private static final String[] TOOL_BONES = {"root", "pliers", "jaw_left", "jaw_right", "tweezers", "arm_left", "arm_right", "pusher",
            "burnisher", "brush", "bristles", "cloth", "pad"};
    private static final String[] TOOL_ROOTS = {"pliers", "tweezers", "pusher", "burnisher", "brush", "cloth"};

    /** Ticks a new lap takes to settle, and the clamp to close. Match the authored clips (seconds x 20). */
    private static final double LAP_SEAT_TICKS = 8, CLAMP_TICKS = 10;

    @Nullable
    private static StationBlockEntity current;
    private static final Detached DETACHED = new Detached();
    private static final Map<StationBlockEntity, Memory> MEMORY = new WeakHashMap<>();

    /** What a station looked like when last drawn, to time its settle motions from the moment they changed. */
    private static final class Memory {
        ItemStack lap = ItemStack.EMPTY;
        ItemStack clamp = ItemStack.EMPTY;
        double lapChanged = Double.NEGATIVE_INFINITY;
        double clampChanged = Double.NEGATIVE_INFINITY;
    }

    /** Draws {@code painter} with {@code station} as the owner of the rigs it draws (null: a detached preview). */
    public static void drawing(@Nullable StationBlockEntity station, Runnable painter) {
        StationBlockEntity previous = current;
        current = station;
        try {
            painter.run();
        } finally {
            current = previous;
        }
    }

    private static GeoAnimatable owner() {
        return current != null ? current : DETACHED;
    }

    /** Whether rigs move at all (the decorative-animation option); state-driven poses still show, settles are skipped. */
    private static boolean settles() {
        return ClientConfig.get(ClientConfig.WORKSTATION_ANIMATIONS) && !ClientConfig.get(ClientConfig.REDUCED_MOTION);
    }

    // ------------------------------------------------------------------------------------------------ the wheel

    /**
     * The lap, its hub and the release lever. Returns false when the rig is unavailable (the painter draws its own).
     *
     * @param lapAngle radians, the wheel's interpolated lap angle
     * @param lapColor rgb of the lap's tier
     * @param lever    0..1, how far the release lever is pulled
     */
    public static boolean wheel(Geo geo, float lapAngle, int lapColor, float lever) {
        double lapSince = Double.POSITIVE_INFINITY;
        if (current instanceof LapidaryWheelBlockEntity wheel) {
            Memory memory = memory(wheel);
            double now = PresentationClock.now(wheel.getLevel());
            if (!ItemStack.isSameItem(memory.lap, wheel.lap())) {
                memory.lapChanged = now;
                memory.lap = wheel.lap().copy();
            }
            lapSince = now - memory.lapChanged;
        }
        double seat = lapSince;
        return WHEEL.draw(geo.pose(), geo.buffers(), geo.light(), owner(), null, "lap", lapColor & 0xFFFFFF, model -> {
            RigClip.rest(model, WHEEL_BONES);
            model.getBone("lap").ifPresent(b -> b.setRotY(b.getRotY() + lapAngle));
            if (settles() && seat >= 0 && seat < LAP_SEAT_TICKS) RigClip.apply(model, WHEEL.animations(), "lap_seat", seat, 1F, false);
            RigClip.applyProgress(model, WHEEL.animations(), "lever_pull", lever, 1F);
        });
    }

    // ------------------------------------------------------------------------------------------------ the bench

    /**
     * The vise and the supports for the clamp's form. Returns false when the rig is unavailable.
     *
     * @param form  the clamped piece's form path ("ring", "amulet", "bracelet", "circlet"), empty for an empty clamp
     * @param relax 0..1, how far the clamp eases off a finished piece (the painter's relax)
     */
    public static boolean vise(Geo geo, String form, float relax) {
        double clampSince = Double.POSITIVE_INFINITY;
        if (current instanceof JewelersBenchBlockEntity bench) {
            Memory memory = memory(bench);
            double now = PresentationClock.now(bench.getLevel());
            if (!ItemStack.isSameItem(memory.clamp, bench.clamp())) {
                memory.clampChanged = now;
                memory.clamp = bench.clamp().copy();
            }
            clampSince = now - memory.clampChanged;
        }
        boolean empty = form.isEmpty();
        boolean posts = form.equals("circlet") || form.equals("bracelet");
        double since = clampSince;
        return BENCH.draw(geo.pose(), geo.buffers(), geo.light(), owner(), null, null, 0xFFFFFF, model -> {
            RigClip.rest(model, BENCH_BONES);
            model.getBone("posts_circlet").ifPresent(b -> b.setHidden(!form.equals("circlet")));
            model.getBone("posts_bracelet").ifPresent(b -> b.setHidden(!form.equals("bracelet")));
            model.getBone("jaw_front").ifPresent(b -> b.setHidden(posts));
            model.getBone("jaw_rear").ifPresent(b -> b.setHidden(posts));
            if (empty) {
                // An empty vise stands open: the clip's first frame.
                RigClip.apply(model, BENCH.animations(), "clamp_close", 0, 1F, false);
                return;
            }
            if (settles() && since >= 0 && since < CLAMP_TICKS) RigClip.apply(model, BENCH.animations(), "clamp_close", since, 1F, false);
            else RigClip.apply(model, BENCH.animations(), "clamp_close", CLAMP_TICKS, 1F, false);
            float open = relax * 0.08F;
            float drop = form.equals("ring") ? -0.2F : 0F;
            model.getBone("jaw_front").ifPresent(b -> {
                b.setPosZ(b.getPosZ() - open);
                b.setPosY(b.getPosY() + drop);
            });
            model.getBone("jaw_rear").ifPresent(b -> {
                b.setPosZ(b.getPosZ() + open);
                b.setPosY(b.getPosY() + drop);
            });
        });
    }

    // ------------------------------------------------------------------------------------------------ tools

    /** The bench tools the rig carries. */
    public enum Tool { PLIERS, TWEEZERS, PUSHER, BURNISHER, BRUSH, CLOTH }

    /**
     * One tool in its own frame (tip at the origin, handle along +z; the caller has oriented the stack). Returns false
     * when the rig is unavailable.
     *
     * @param open   pliers and tweezers: 0 closed .. 1 open
     * @param active brush: dabbing; cloth: rubbing
     * @param fine   the Fine Pliers
     */
    public static boolean tool(Geo geo, Tool tool, float open, boolean active, boolean fine) {
        String shown = TOOL_ROOTS[tool.ordinal()];
        return TOOLS.draw(geo.pose(), geo.buffers(), geo.light(), owner(), fine && tool == Tool.PLIERS ? FINE_TOOLS : null, null, 0xFFFFFF, model -> {
            RigClip.rest(model, TOOL_BONES);
            for (String root : TOOL_ROOTS) model.getBone(root).ifPresent(b -> b.setHidden(!root.equals(shown)));
            switch (tool) {
                case PLIERS -> RigClip.applyProgress(model, TOOLS.animations(), "pliers_squeeze", 1F - Mth.clamp(open, 0F, 1F), 1F);
                case TWEEZERS -> {
                    float gap = 0.07F + Mth.clamp(open, 0F, 1F) * 0.3F;
                    float angle = (float) Math.asin(Math.min(0.5F, gap / 3.2F));
                    model.getBone("arm_left").ifPresent(b -> b.setRotY(b.getRotY() + angle));
                    model.getBone("arm_right").ifPresent(b -> b.setRotY(b.getRotY() - angle));
                }
                case BRUSH -> RigClip.applyProgress(model, TOOLS.animations(), "brush_dab", active ? 1F : 0F, 1F);
                case CLOTH -> RigClip.applyProgress(model, TOOLS.animations(), "cloth_rub", active ? 1F : 0F, 1F);
                default -> {
                }
            }
        });
    }

    /** The painter's bench tool as a rig tool, or null for none. */
    @Nullable
    public static Tool of(BenchPainter.Tool tool) {
        return switch (tool) {
            case PLIERS -> Tool.PLIERS;
            case TWEEZERS -> Tool.TWEEZERS;
            case PUSHER -> Tool.PUSHER;
            case BURNISHER -> Tool.BURNISHER;
            case BRUSH -> Tool.BRUSH;
            case CLOTH -> Tool.CLOTH;
            case NONE -> null;
        };
    }

    // ------------------------------------------------------------------------------------------------ housekeeping

    private static Memory memory(StationBlockEntity station) {
        return MEMORY.computeIfAbsent(station, s -> {
            Memory m = new Memory();
            // First sight: what the station holds now is not news (a late observer sees no old settle).
            if (s instanceof LapidaryWheelBlockEntity wheel) m.lap = wheel.lap().copy();
            if (s instanceof JewelersBenchBlockEntity bench) m.clamp = bench.clamp().copy();
            return m;
        });
    }

    /** A resource reload: rigs that failed are tried again, missing clips are reported again. */
    public static void reload() {
        WHEEL.reset();
        BENCH.reset();
        TOOLS.reset();
        RigClip.reloaded();
    }

    /** Leaving a world or closing a demonstration level: nothing about its stations is kept. */
    public static void forget() {
        MEMORY.clear();
    }

    /** The animatable for rigs drawn with no station (an item preview): no controllers, its own instance cache. */
    private static final class Detached implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object object) {
            return 0;
        }
    }

    private JewelryRigs() {}
}
