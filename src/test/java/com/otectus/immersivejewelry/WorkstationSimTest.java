package com.otectus.immersivejewelry;

import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.data.cut.CutPattern;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.testing.FacetBot;
import com.otectus.immersivejewelry.testing.SettingBot;
import com.otectus.immersivejewelry.testing.SurfaceBot;
import com.otectus.immersivejewelry.workstation.sim.FacetParams;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import com.otectus.immersivejewelry.workstation.sim.SettingParams;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SimIO;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hands-on simulations: progress only from valid contact, readable mistakes, deterministic and frame-rate independent. */
class WorkstationSimTest {

    static FacetParams facet(CutPattern pattern, int regions, float brittleness, long seed) {
        return FacetParams.create(new FacetParams.Inputs(regions, pattern, 0.85F, 1F, 9F, brittleness, 0.15F, 1F, 1F, 1F, 1F, 1F, 1F,
                false, false, FacetParams.Tuning.DEFAULT), seed);
    }

    static SettingParams setting(SettingStyle style, int stones, int points, float brittleness) {
        return SettingParams.create(new SettingParams.Inputs(style, stones, points, 1F, 1F, 1F, brittleness, 1F, false, false,
                SettingParams.Tuning.DEFAULT));
    }

    static int regions(CutPattern pattern) {
        return pattern == CutPattern.DOME ? 4 : pattern == CutPattern.STEP ? 8 : 6;
    }

    // ------------------------------------------------------------------------------------------------ faceting

    @Test
    void liftedHandleRemovesNothing() {
        FacetSim sim = new FacetSim(facet(CutPattern.RADIAL, 6, 0.35F, 1L));
        for (int i = 0; i < 500; i++) sim.step(new WorkSample(2048, 4095, 0, 0, WorkSample.TRIM_BIAS, 0));
        for (int r = 0; r < 6; r++) assertEquals(0F, sim.depth(r), "No contact, no material removed");
        assertFalse(sim.touched());
        // Hovering just above the lap is still no contact.
        for (int i = 0; i < 200; i++) sim.step(new WorkSample(2048, SimIO.toUnit12(FacetSim.CONTACT_HEIGHT + 0.02F), 0, 0, WorkSample.TRIM_BIAS, 0));
        assertEquals(0F, sim.depth(0));
    }

    @ParameterizedTest
    @EnumSource(CutPattern.class)
    void carefulCuttingEarnsAGoodCut(CutPattern pattern) {
        FacetSim sim = new FacetSim(facet(pattern, regions(pattern), 0.35F, 7L));
        int steps = new FacetBot(FacetBot.Style.CAREFUL, -Math.round(sim.params().dopOffset())).run(sim, 20_000);
        assertTrue(sim.ready(), "A careful cut reaches every window");
        assertEquals(0, sim.chips(), "A careful cut does not chip");
        assertTrue(sim.quality() >= 0.75F, "careful " + pattern + " quality " + sim.quality());
        // Pacing: about 20 to 40 seconds of active work for a familiar cut (50 steps a second).
        assertTrue(steps / 50F < 60F, "careful " + pattern + " took " + steps / 50F + " s");
    }

    @Test
    void heavyHandsCostQualityButNeverSilently() {
        FacetParams p = facet(CutPattern.RADIAL, 6, 0.6F, 11L);
        FacetSim careful = new FacetSim(p);
        new FacetBot(FacetBot.Style.CAREFUL).run(careful, 20_000);
        FacetSim heavy = new FacetSim(p);
        new FacetBot(FacetBot.Style.HEAVY).run(heavy, 20_000);
        assertTrue(heavy.quality() < careful.quality() - 0.1F, "heavy " + heavy.quality() + " vs careful " + careful.quality());
        float marks = 0F;
        for (int r = 0; r < 6; r++) marks += heavy.scratch(r) + heavy.rock(r);
        assertTrue(marks > 0F || heavy.chips() > 0, "Pressing hard and jerking leaves visible marks");
        assertTrue(heavy.sizeLoss() > careful.sizeLoss(), "Overcutting costs size");
    }

    @Test
    void grindingInPlaceLeavesUnevenFaces() {
        FacetParams p = facet(CutPattern.STEP, 8, 0.3F, 5L);
        FacetSim careful = new FacetSim(p);
        new FacetBot(FacetBot.Style.CAREFUL).run(careful, 20_000);
        FacetSim still = new FacetSim(p);
        new FacetBot(FacetBot.Style.STILL).run(still, 20_000);
        assertTrue(still.unevenness(0) > careful.unevenness(0) + 0.5F, "Sweeping evens a face");
        assertTrue(still.quality() < careful.quality(), "still " + still.quality() + " vs " + careful.quality());
    }

    @Test
    void domesNeedTheSweep() {
        FacetParams p = facet(CutPattern.DOME, 4, 0.3F, 9L);
        FacetSim still = new FacetSim(p);
        new FacetBot(FacetBot.Style.STILL).run(still, 20_000);
        assertTrue(still.flatSpot(0) > 0.3F, "Grinding a dome without moving leaves a flat spot");
    }

    @Test
    void heatWarnsBeforeItChips() {
        FacetSim sim = new FacetSim(facet(CutPattern.RADIAL, 6, 0.9F, 3L));
        boolean warned = false;
        int chipStep = -1;
        for (int i = 0; i < 3000 && chipStep < 0; i++) {
            int events = sim.step(new WorkSample(2048 + (int) (Math.sin(i * 0.06) * 1200), 0, 0, 0, WorkSample.TRIM_BIAS, 0));
            if ((events & FacetSim.EVENT_HOT) != 0) warned = true;
            if ((events & FacetSim.EVENT_CHIP) != 0) chipStep = i;
        }
        assertTrue(chipStep > 0, "Sustained full force chips a brittle stone");
        assertTrue(warned, "The stone runs hot before it chips");
    }

    @Test
    void theCollarRefusesToTurnUnderLoad() {
        FacetSim sim = new FacetSim(facet(CutPattern.RADIAL, 6, 0.3F, 2L));
        int start = sim.region();
        for (int i = 0; i < 30; i++) sim.step(new WorkSample(2048, 0, 0, start, WorkSample.TRIM_BIAS, 0));
        assertTrue(sim.contact());
        sim.step(new WorkSample(2048, 0, 0, (start + 1) % 6, WorkSample.TRIM_BIAS, 0));
        assertEquals(start, sim.region(), "No indexing while the stone is on the lap");
    }

    @Test
    void handleMotionIsBoundedPerStep() {
        FacetSim sim = new FacetSim(facet(CutPattern.RADIAL, 6, 0.3F, 2L));
        sim.step(new WorkSample(2048, 0, 0, 0, WorkSample.TRIM_BIAS, 0));
        assertEquals(1F - FacetSim.MAX_HEIGHT_STEP, sim.height(), 1.0E-6F, "A hand cannot teleport the quill onto the lap");
        assertFalse(sim.contact());
    }

    @Test
    void cuttingIsDeterministicAndResumesExactly() {
        FacetParams p = facet(CutPattern.RADIAL, 10, 0.35F, 99L);
        FacetSim a = new FacetSim(p);
        FacetBot bot = new FacetBot(FacetBot.Style.CAREFUL);
        List<WorkSample> samples = new ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            WorkSample s = bot.next(a);
            samples.add(s);
            a.step(s);
        }
        // Saved half way and loaded again (a reload, a stone carried away and remounted), the rest replays identically.
        FacetSim b = new FacetSim(FacetParams.load(p.save()));
        for (int i = 0; i < 700; i++) b.step(samples.get(i));
        FacetSim resumed = FacetSim.load(FacetParams.load(p.save()), b.save());
        for (int i = 700; i < samples.size(); i++) resumed.step(samples.get(i));
        assertEquals(a.save(), resumed.save(), "Server replay and resumed work are bit-identical");
    }

    @Test
    void frameRateDoesNotChangeTheResult() {
        FacetParams p = facet(CutPattern.RADIAL, 6, 0.35F, 21L);
        float q30 = byFrameRate(p, 30), q60 = byFrameRate(p, 60), q144 = byFrameRate(p, 144);
        assertEquals(q60, q30, 0.02F, "30 fps vs 60 fps");
        assertEquals(q60, q144, 0.02F, "144 fps vs 60 fps");
    }

    /** The same hand movement (a function of real time) sampled by frames at {@code fps} and spread over fixed steps. */
    private static float byFrameRate(FacetParams p, int fps) {
        FacetSim sim = new FacetSim(p);
        double frame = 1.0 / fps;
        WorkSample previous = WorkSample.NEUTRAL;
        double clock = 0;
        double stepped = 0;
        for (double t = 0; t < 60.0 && !sim.ready(); t += frame) {
            WorkSample current = hand(t, sim);
            int steps = (int) Math.floor((t - stepped) / 0.02);
            for (int i = 1; i <= steps; i++) sim.step(WorkSample.between(previous, current, i / (float) steps));
            stepped += steps * 0.02;
            previous = current;
            clock = t;
        }
        assertTrue(clock > 0);
        return sim.quality();
    }

    /** A careful hand as a function of time: sweeping, lowering on the recommended face, lifting between faces. */
    private static WorkSample hand(double t, FacetSim sim) {
        int r = sim.recommended();
        if (r < 0) return new WorkSample(2048, 4095, 0, sim.region(), WorkSample.TRIM_BIAS, 0);
        boolean onFace = sim.region() == r && sim.depth(r) < sim.params().target()[r] - sim.params().window() * 0.2F;
        float sweep = (float) Math.sin(t * 1.75) * 0.82F;
        float height = onFace ? FacetSim.CONTACT_HEIGHT * 0.4F : 1F;
        return new WorkSample(SimIO.toSigned12(sweep), SimIO.toUnit12(height), 0, r, WorkSample.TRIM_BIAS, 0);
    }

    // ------------------------------------------------------------------------------------------------ setting

    @Test
    void carefulProngsSecureTheStone() {
        SettingSim sim = new SettingSim(setting(SettingStyle.PRONG, 1, 4, 0.35F), SettingSim.Mode.SET);
        sim.seat(0, 0.05F, -0.05F, 0F);
        new SettingBot(SettingBot.Style.CAREFUL).run(sim, 5000);
        assertTrue(sim.allSecured());
        assertTrue(sim.defects().isEmpty(), "defects " + sim.defects());
        assertTrue(sim.quality() >= 0.8F, "quality " + sim.quality());
    }

    @Test
    void overbentProngsAreVisibleAndCostQuality() {
        SettingSim sim = new SettingSim(setting(SettingStyle.PRONG, 1, 4, 0.6F), SettingSim.Mode.SET);
        sim.seat(0, 0F, 0F, 0F);
        new SettingBot(SettingBot.Style.HEAVY).run(sim, 5000);
        assertTrue(sim.defects().contains(SettingDefect.BENT_PRONG));
        assertTrue(sim.chipped(0), "Crushing prongs onto a brittle stone chips it");
        assertTrue(sim.quality() < 0.6F, "quality " + sim.quality());
    }

    @Test
    void releasingWithoutContactChangesNothing() {
        SettingSim sim = new SettingSim(setting(SettingStyle.PRONG, 1, 4, 0.3F), SettingSim.Mode.SET);
        sim.seat(0, 0F, 0F, 0F);
        for (int i = 0; i < 200; i++) sim.step(new WorkSample(4095, 0, 0, 0, SettingSim.TOOL_PLIERS, 0));
        for (int q = 0; q < 4; q++) assertEquals(0F, sim.closure(0, q));
        // Gripping an empty socket does nothing either.
        SettingSim empty = new SettingSim(setting(SettingStyle.PRONG, 2, 4, 0.3F), SettingSim.Mode.SET);
        for (int i = 0; i < 200; i++) empty.step(new WorkSample(4095, 0, 0, 16, SettingSim.TOOL_PLIERS, WorkSample.FLAG_CONTACT));
        assertEquals(0F, empty.closure(1, 0));
    }

    @Test
    void easingTheHandleBackOpensNothing() {
        SettingSim sim = new SettingSim(setting(SettingStyle.PRONG, 1, 4, 0.3F), SettingSim.Mode.SET);
        sim.seat(0, 0F, 0F, 0F);
        for (int i = 0; i < 12; i++) sim.step(new WorkSample(SimIO.toUnit12(i * 0.06F), 0, 0, 0, SettingSim.TOOL_PLIERS, WorkSample.FLAG_CONTACT));
        float closed = sim.closure(0, 0);
        assertTrue(closed > 0.5F);
        for (int i = 0; i < 12; i++) sim.step(new WorkSample(0, 0, 0, 0, SettingSim.TOOL_PLIERS, WorkSample.FLAG_CONTACT));
        assertEquals(closed, sim.closure(0, 0), 1.0E-6F, "Metal stays where it was bent");
    }

    @Test
    void carefulBezelIsEvenAndHeavyPushingPinches() {
        SettingSim careful = new SettingSim(setting(SettingStyle.BEZEL, 1, 6, 0.3F), SettingSim.Mode.SET);
        careful.seat(0, 0F, 0F, 0F);
        new SettingBot(SettingBot.Style.CAREFUL).run(careful, 8000);
        assertTrue(careful.allSecured(), "Sweeping round the rim closes it");
        assertTrue(careful.alignment(0) > 0.7F);
        SettingSim heavy = new SettingSim(setting(SettingStyle.BEZEL, 1, 6, 0.3F), SettingSim.Mode.SET);
        heavy.seat(0, 0F, 0F, 0F);
        for (int i = 0; i < 600; i++) heavy.step(new SettingBot(SettingBot.Style.HEAVY).next(heavy));
        assertTrue(heavy.defects().contains(SettingDefect.BENT_PRONG), "Pushing hard in one place pinches the rim");
    }

    @Test
    void removalOpensEveryPoint() {
        SettingSim sim = new SettingSim(setting(SettingStyle.PRONG, 3, 4, 0.3F), SettingSim.Mode.REMOVE);
        float[] closures = new float[12];
        java.util.Arrays.fill(closures, 1F);
        sim.restore(closures, new float[]{0.9F, 0.9F, 0.9F});
        new SettingBot(SettingBot.Style.CAREFUL).run(sim, 8000);
        for (int s = 0; s < 3; s++) assertTrue(sim.opened(s), "socket " + s);
    }

    @Test
    void whereTheStoneIsLetGoDecidesTheSeat() {
        SettingSim sim = new SettingSim(setting(SettingStyle.PRONG, 2, 4, 0.3F), SettingSim.Mode.SET);
        sim.seat(0, 0F, 0F, 0F);
        sim.seat(1, 0.9F, 0.8F, 1F);
        assertTrue(sim.seat(0) > 0.99F);
        assertTrue(sim.seat(1) < 0.5F);
        sim.seat(1, Float.NaN, Float.POSITIVE_INFINITY, -7F);
        assertTrue(Float.isFinite(sim.seat(1)) && sim.seat(1) >= 0.2F && sim.seat(1) <= 1F, "Hostile offsets are bounded");
    }

    // ------------------------------------------------------------------------------------------------ surface

    @ParameterizedTest
    @EnumSource(SurfaceParams.Mode.class)
    void finishingIsShortAndCoverageFollowsTheTool(SurfaceParams.Mode mode) {
        SurfaceSim sim = new SurfaceSim(SurfaceParams.create(mode, 5L, 0.4F, 1F, false));
        int steps = new SurfaceBot().run(sim, 5000);
        assertTrue(sim.complete(), mode + " completes");
        assertTrue(steps / 50F < 20F, mode + " took " + steps / 50F + " s");
    }

    @Test
    void scrubbingOneSpotNeverFinishes() {
        SurfaceSim sim = new SurfaceSim(SurfaceParams.create(SurfaceParams.Mode.POLISH, 5L, 0F, 1F, false));
        new SurfaceBot(true).run(sim, 10_000);
        assertFalse(sim.complete());
        assertTrue(sim.progress() < 0.2F, "progress " + sim.progress());
    }

    @Test
    void burnishingNeedsPassesNotPressure() {
        SurfaceSim sim = new SurfaceSim(SurfaceParams.create(SurfaceParams.Mode.BURNISH, 5L, 0F, 1F, false));
        // Bring the burnisher to its place first, then hold it there under pressure.
        for (int i = 0; i < 20; i++) sim.step(new WorkSample(1000, 2048, 0, 0, SurfaceParams.TOOL_BURNISHER, 0));
        for (int i = 0; i < 2000; i++) {
            sim.step(new WorkSample(1000, 2048, 3000, 0, SurfaceParams.TOOL_BURNISHER, WorkSample.FLAG_CONTACT));
        }
        assertEquals(0F, sim.progress(), 1.0E-6F, "A burnisher held still burnishes nothing");
    }

    @Test
    void theWrongToolDoesNothing() {
        SurfaceSim sim = new SurfaceSim(SurfaceParams.create(SurfaceParams.Mode.POLISH, 5L, 0F, 1F, false));
        for (int i = 0; i < 2000; i++) {
            sim.step(new WorkSample((i * 40) % 4096, 2048, 3000, 0, SurfaceParams.TOOL_BRUSH, WorkSample.FLAG_CONTACT));
        }
        assertEquals(0F, sim.progress(), 1.0E-6F);
    }

    // ------------------------------------------------------------------------------------------------ bounds

    @Test
    void samplesAreClampedAndRoundTrip() {
        WorkSample s = new WorkSample(-5, 99999, 4095, 999, 77, 0xFF);
        assertEquals(0, s.a());
        assertEquals(4095, s.b());
        assertEquals(255, s.target());
        assertEquals(31, s.aux());
        assertEquals(7, s.flags());
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        WorkSample t = new WorkSample(123, 4000, 7, 35, 9, 5);
        t.write(buf);
        assertEquals(WorkSample.BYTES, buf.readableBytes());
        assertEquals(t, WorkSample.read(buf));
    }

    @Test
    void corruptSavedParametersAreBounded() {
        var tag = facet(CutPattern.RADIAL, 6, 0.3F, 1L).save();
        tag.putFloat("Rate", Float.NaN);
        tag.putFloat("Window", Float.POSITIVE_INFINITY);
        FacetParams p = FacetParams.load(tag);
        assertTrue(Float.isFinite(p.rate()) && p.rate() > 0F);
        assertTrue(Float.isFinite(p.window()) && p.window() <= 0.4F);
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeVarInt(1_000_000);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> SimIO.readFloats(buf, 6, 0F, 1F));
    }
}
