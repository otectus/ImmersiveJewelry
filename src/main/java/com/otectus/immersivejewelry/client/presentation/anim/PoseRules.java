package com.otectus.immersivejewelry.client.presentation.anim;

import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Which body clip a jeweler plays, from a station's synced state alone: the same answer for the operator and every
 * watcher, a late one included, and never from anyone's raw input. A held motion follows what the work is doing now
 * (grinding, gripping a prong, rubbing the surface); a one-shot answers an accepted change seen between two states (a stone
 * mounted, a lap swapped, a piece put in the clamp, a piece finished). The first state seen is never a change.
 */
public final class PoseRules {
    /** What the rules read from a station. */
    public record Snapshot(boolean wheel, boolean stone, @Nullable Item lap, boolean contact, boolean clamp, boolean finishedPiece,
                           boolean work, @Nullable BenchWork.Kind kind, @Nullable BenchWork.Stage stage, boolean gripping,
                           SettingStyle style, @Nullable SurfaceParams.Mode surface, int phase, boolean rubbing, int seated) {}

    public static Snapshot of(StationBlockEntity station) {
        if (station instanceof LapidaryWheelBlockEntity wheel) {
            CutWork work = wheel.work().orElse(null);
            return new Snapshot(true, wheel.hasStone(), wheel.lap().isEmpty() ? null : wheel.lap().getItem(),
                    work != null && work.sim().contact(), false, false, work != null, null, null, false, SettingStyle.PRONG, null, 0, false, 0);
        }
        JewelersBenchBlockEntity bench = (JewelersBenchBlockEntity) station;
        BenchWork work = bench.work();
        SettingSim setting = work != null ? work.setting() : null;
        SurfaceSim surface = work != null ? work.surface() : null;
        return new Snapshot(false, false, null, false, !bench.clamp().isEmpty(), bench.clamp().getItem() instanceof JewelryItem && work == null,
                work != null, work != null ? work.kind() : null, work != null ? work.stage() : null, setting != null && setting.gripping(),
                setting != null ? setting.params().style() : SettingStyle.PRONG, surface != null ? surface.params().mode() : null,
                surface != null ? surface.phase() : 0, surface != null && surface.contact(), bench.seatedCount());
    }

    /** The held motion for the station's state now. */
    public static JewelerClip held(Snapshot s) {
        if (s.wheel()) return s.contact() ? JewelerClip.WHEEL_GRIND : JewelerClip.WHEEL_READY;
        if (!s.work()) return JewelerClip.BENCH_READY;
        if (s.kind() == BenchWork.Kind.REMOVE) return s.gripping() ? JewelerClip.PRY_STONE : JewelerClip.BENCH_READY;
        if (s.stage() == BenchWork.Stage.SETTING) {
            if (!s.gripping()) return JewelerClip.BENCH_READY;
            return s.style() == SettingStyle.BEZEL ? JewelerClip.BEZEL_PUSH : JewelerClip.PLIERS_SQUEEZE;
        }
        if (s.stage() == BenchWork.Stage.FINISHING && s.rubbing() && s.surface() != null) {
            return switch (s.surface()) {
                case POLISH -> JewelerClip.POLISH_WIPE;
                case BURNISH -> JewelerClip.BURNISH_PASS;
                case PATINA -> s.phase() == 0 ? JewelerClip.PATINA_DAB : JewelerClip.POLISH_WIPE;
                case REPAIR -> JewelerClip.REPAIR_TRUE;
            };
        }
        return JewelerClip.BENCH_READY;
    }

    /** The one-shot answering the change from {@code before} to {@code after}, or null. */
    @Nullable
    public static JewelerClip change(@Nullable Snapshot before, Snapshot after) {
        if (before == null || before.wheel() != after.wheel()) return null;
        if (after.wheel()) {
            if (before.lap() != after.lap()) return JewelerClip.LAP_CHANGE;
            if (before.stone() != after.stone()) return JewelerClip.WHEEL_MOUNT;
            if (before.contact() && !after.contact()) return JewelerClip.WHEEL_LIFT;
            return null;
        }
        if (!before.finishedPiece() && after.finishedPiece() && before.work()) return JewelerClip.INSPECT_PIECE;
        if (!before.clamp() && after.clamp()) return JewelerClip.BENCH_PLACE;
        if (before.clamp() && !after.clamp()) return JewelerClip.BENCH_COLLECT;
        if (after.seated() > before.seated()) return JewelerClip.TWEEZERS_SEAT;
        return null;
    }

    /** How long a one-shot keeps the body (a held clip played once runs one cycle). */
    public static int duration(JewelerClip clip) {
        return clip.length;
    }

    private PoseRules() {}
}
