package com.otectus.immersivejewelry;

import com.otectus.immersivejewelry.quality.CutGrade;
import com.otectus.immersivejewelry.quality.MetalworkGrade;
import com.otectus.immersivejewelry.quality.Workmanship;
import com.otectus.immersivejewelry.quality.WorkmanshipGrade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Craftsmanship arithmetic and its labels (spec 10.4, 14, 72). */
class QualityTest {

    @Test
    void specExampleGivesExceptionalWorkmanship() {
        // Spec 72: smithing 0.88, cut 0.94, setting 0.90 -> 0.907, Exceptional.
        float w = Workmanship.compute(0.88F, 0.94F, 0.90F);
        assertEquals(0.907F, w, 0.0005F);
        assertEquals(WorkmanshipGrade.EXCEPTIONAL, WorkmanshipGrade.of(w));
    }

    @Test
    void weightsAreNormalisedAndNoStageIsIrrelevant() {
        assertEquals(0.5F, Workmanship.compute(0.5F, 0.5F, 0.5F, 2F, 2F, 2F), 1e-6);
        assertEquals(0.35F, Workmanship.compute(1F, 0F, 0F), 1e-6);
        assertEquals(0.30F, Workmanship.compute(0F, 0F, 1F), 1e-6);
        // Degenerate weights fall back to the defaults instead of dividing by zero.
        assertEquals(Workmanship.compute(0.7F, 0.6F, 0.5F), Workmanship.compute(0.7F, 0.6F, 0.5F, 0F, 0F, 0F), 1e-6);
    }

    @Test
    void cutGradesFollowTheirThresholds() {
        float[] t = {0.20F, 0.40F, 0.60F, 0.78F, 0.92F};
        assertEquals(CutGrade.RUINED, CutGrade.of(0.1F, t));
        assertEquals(CutGrade.CHIPPED, CutGrade.of(0.2F, t));
        assertEquals(CutGrade.ACCEPTABLE, CutGrade.of(0.5F, t));
        assertEquals(CutGrade.FINE, CutGrade.of(0.7F, t));
        assertEquals(CutGrade.EXCELLENT, CutGrade.of(0.9F, t));
        assertEquals(CutGrade.FLAWLESS, CutGrade.of(0.95F, t));
    }

    @Test
    void workmanshipAndMetalworkBands() {
        assertEquals(WorkmanshipGrade.FAULTY, WorkmanshipGrade.of(0.2F));
        assertEquals(WorkmanshipGrade.COMMON, WorkmanshipGrade.of(0.5F));
        assertEquals(WorkmanshipGrade.FINE, WorkmanshipGrade.of(0.7F));
        assertEquals(WorkmanshipGrade.MASTERWORK, WorkmanshipGrade.of(0.95F));
        assertEquals(MetalworkGrade.CRUDE, MetalworkGrade.of(0.2F));
        assertEquals(MetalworkGrade.STANDARD, MetalworkGrade.of(0.45F));
        assertEquals(MetalworkGrade.FINE, MetalworkGrade.of(0.8F));
        assertEquals(MetalworkGrade.MASTERWORK, MetalworkGrade.of(0.9F));
    }
}
