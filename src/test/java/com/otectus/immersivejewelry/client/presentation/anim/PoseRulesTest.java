package com.otectus.immersivejewelry.client.presentation.anim;

import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The jeweler's body follows accepted station state only, and a first sight of a station is never an event. */
class PoseRulesTest {
    private static PoseRules.Snapshot wheel(boolean stone, boolean contact) {
        return new PoseRules.Snapshot(true, stone, null, contact, false, false, contact, null, null, false, SettingStyle.PRONG, null, 0, false, 0);
    }

    private static PoseRules.Snapshot bench(boolean clamp, boolean finished, boolean work, BenchWork.Kind kind, BenchWork.Stage stage,
                                            boolean gripping, SettingStyle style, SurfaceParams.Mode surface, int phase, boolean rubbing, int seated) {
        return new PoseRules.Snapshot(false, false, null, false, clamp, finished, work, kind, stage, gripping, style, surface, phase, rubbing, seated);
    }

    @Test
    void heldMotionFollowsTheWork() {
        assertEquals(JewelerClip.WHEEL_READY, PoseRules.held(wheel(true, false)));
        assertEquals(JewelerClip.WHEEL_GRIND, PoseRules.held(wheel(true, true)));
        assertEquals(JewelerClip.PLIERS_SQUEEZE, PoseRules.held(bench(true, false, true, BenchWork.Kind.ASSEMBLE, BenchWork.Stage.SETTING, true,
                SettingStyle.PRONG, null, 0, false, 1)));
        assertEquals(JewelerClip.BEZEL_PUSH, PoseRules.held(bench(true, false, true, BenchWork.Kind.ASSEMBLE, BenchWork.Stage.SETTING, true,
                SettingStyle.BEZEL, null, 0, false, 1)));
        assertEquals(JewelerClip.BENCH_READY, PoseRules.held(bench(true, false, true, BenchWork.Kind.ASSEMBLE, BenchWork.Stage.SETTING, false,
                SettingStyle.PRONG, null, 0, false, 1)));
        assertEquals(JewelerClip.PATINA_DAB, PoseRules.held(bench(true, false, true, BenchWork.Kind.ASSEMBLE, BenchWork.Stage.FINISHING, false,
                SettingStyle.PRONG, SurfaceParams.Mode.PATINA, 0, true, 1)));
        assertEquals(JewelerClip.POLISH_WIPE, PoseRules.held(bench(true, false, true, BenchWork.Kind.ASSEMBLE, BenchWork.Stage.FINISHING, false,
                SettingStyle.PRONG, SurfaceParams.Mode.PATINA, 1, true, 1)));
        assertEquals(JewelerClip.PRY_STONE, PoseRules.held(bench(true, false, true, BenchWork.Kind.REMOVE, BenchWork.Stage.SETTING, true,
                SettingStyle.PRONG, null, 0, false, 0)));
    }

    @Test
    void oneShotsAnswerOnlyAcceptedChanges() {
        assertNull(PoseRules.change(null, wheel(true, true)), "first sight replays nothing");
        assertEquals(JewelerClip.WHEEL_MOUNT, PoseRules.change(wheel(false, false), wheel(true, false)));
        assertEquals(JewelerClip.WHEEL_LIFT, PoseRules.change(wheel(true, true), wheel(true, false)));
        assertNull(PoseRules.change(wheel(true, false), wheel(true, true)));
        var empty = bench(false, false, false, null, null, false, SettingStyle.PRONG, null, 0, false, 0);
        var blank = bench(true, false, false, null, null, false, SettingStyle.PRONG, null, 0, false, 0);
        var finishing = bench(true, false, true, BenchWork.Kind.ASSEMBLE, BenchWork.Stage.FINISHING, false, SettingStyle.PRONG,
                SurfaceParams.Mode.POLISH, 0, false, 1);
        var done = bench(true, true, false, null, null, false, SettingStyle.PRONG, null, 0, false, 0);
        assertEquals(JewelerClip.BENCH_PLACE, PoseRules.change(empty, blank));
        assertEquals(JewelerClip.INSPECT_PIECE, PoseRules.change(finishing, done));
        assertEquals(JewelerClip.BENCH_COLLECT, PoseRules.change(done, empty));
    }
}
