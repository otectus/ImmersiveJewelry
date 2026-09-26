package com.otectus.immersivejewelry;

import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.data.cut.CutPattern;
import com.otectus.immersivejewelry.minigame.cutting.CutParams;
import com.otectus.immersivejewelry.minigame.cutting.CutSimulation;
import com.otectus.immersivejewelry.minigame.setting.SettingParams;
import com.otectus.immersivejewelry.minigame.setting.SettingSimulation;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.testing.CutBot;
import com.otectus.immersivejewelry.testing.SetBot;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shared minigame rules: deterministic, rewarding skill, forgiving of failure (spec 10, 12, 34-35). */
class MinigameTest {

    static CutParams cut(CutPattern pattern, int facets, float difficulty, float brittleness, long seed) {
        return CutParams.create(new CutParams.Inputs(facets, pattern, difficulty, 1F, 9F, brittleness, 0.2F, 1F, 1F, 1F, 1F, 1F,
                1F, false, false, 9), seed);
    }

    static SettingParams set(SettingStyle style, int stones, int points, boolean removal, float brittleness) {
        return SettingParams.create(new SettingParams.Inputs(style, stones, points, 1F, 1F, 1F, brittleness, 1F, false, false, removal, 10), 42L);
    }

    @Test
    void cuttingIsDeterministic() {
        CutParams p = cut(CutPattern.RADIAL, 10, 1.2F, 0.35F, 99L);
        CutBot bot = new CutBot(p, false);
        CutSimulation replay = new CutSimulation(p);
        while (!bot.sim().isComplete()) {
            int[] in = bot.next();
            replay.step(in[0], in[1], in[2] == 1);
        }
        assertTrue(replay.isComplete());
        assertEquals(bot.sim().quality(), replay.quality(), 0F, "The server's replay must match the client exactly");
        assertEquals(bot.sim().chips(), replay.chips());
        assertEquals(bot.sim().sizeLoss(), replay.sizeLoss(), 0F);
    }

    @ParameterizedTest
    @EnumSource(CutPattern.class)
    void carefulCuttingEarnsAGoodCut(CutPattern pattern) {
        int facets = pattern == CutPattern.DOME ? 4 : pattern == CutPattern.STEP ? 8 : 6;
        CutBot bot = new CutBot(cut(pattern, facets, 0.85F, 0.35F, 7L), false);
        int steps = 0;
        while (!bot.sim().isComplete()) {
            bot.next();
            steps++;
        }
        CutSimulation sim = bot.sim();
        assertTrue(!sim.timedOut(), "A careful cut finishes in time");
        assertTrue(sim.quality() >= 0.65F, pattern + ": careful cutting scores well, got " + sim.quality());
        assertEquals(0, sim.chips(), "Pacing the heat avoids chips");
        // Spec 3: a cut takes seconds, not minutes.
        assertTrue(steps * 20 < 60_000, "A " + facets + "-facet cut took " + steps * 20 + " ms");
    }

    @Test
    void carelessCuttingFailsSoftly() {
        CutBot bot = new CutBot(cut(CutPattern.RADIAL, 6, 0.85F, 0.35F, 7L), true);
        while (!bot.sim().isComplete()) bot.next();
        CutSimulation sim = bot.sim();
        assertTrue(sim.timedOut(), "Grinding the table never finishes a facet");
        assertTrue(sim.quality() < 0.3F, "and grades poorly, got " + sim.quality());
        assertTrue(sim.chips() > 0, "and heat without pause chips the stone");
    }

    @Test
    void grindingThroughTheWindowOvercuts() {
        CutParams p = cut(CutPattern.DOME, 4, 0.55F, 0.1F, 3L);
        CutSimulation sim = new CutSimulation(p);
        float a = p.angles()[p.order()[0]];
        int fx = CutSimulation.toFixed((float) Math.cos(a) * 0.7F);
        int fy = CutSimulation.toFixed((float) Math.sin(a) * 0.7F);
        boolean overcut = false;
        for (int i = 0; i < 2000 && !overcut; i++) overcut = (sim.step(fx, fy, true) & CutSimulation.EVENT_OVERCUT) != 0;
        assertTrue(overcut, "Holding past the window overcuts the facet");
        assertTrue(sim.sizeLoss() > 0F, "and costs size");
        assertEquals(1, sim.lockedCount());
    }

    @Test
    void cutParamsTravelIntact() {
        CutParams p = cut(CutPattern.STEP, 8, 1F, 0.4F, 5L);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        p.write(buf);
        CutParams q = CutParams.read(buf);
        assertArrayEquals(p.angles(), q.angles());
        assertArrayEquals(p.halfWidths(), q.halfWidths());
        assertArrayEquals(p.windowCenter(), q.windowCenter());
        assertArrayEquals(p.order(), q.order());
        assertEquals(p.chipThreshold(), q.chipThreshold());
        assertEquals(p.maxSteps(), q.maxSteps());
    }

    @Test
    void balancedSettingIsSecure() {
        for (SettingStyle style : SettingStyle.values()) {
            SetBot bot = new SetBot(set(style, 1, style == SettingStyle.BEZEL ? 6 : 4, false, 0.3F), false);
            while (!bot.sim().isComplete()) bot.next();
            SettingSimulation sim = bot.sim();
            assertTrue(sim.quality() >= 0.85F, style + ": a careful setting scores high, got " + sim.quality());
            assertTrue(sim.defects().isEmpty(), style + ": and leaves no flaws, got " + sim.defects());
            assertTrue(sim.alignment() > 0.9F, "and the stone sits level");
        }
    }

    @Test
    void settingOrderAlternatesOppositePoints() {
        SettingParams p = set(SettingStyle.PRONG, 1, 4, false, 0.3F);
        assertArrayEquals(new int[]{0, 2, 1, 3}, p.order(), "Four prongs are worked 1 -> 3 -> 2 -> 4");
        SettingParams three = set(SettingStyle.PRONG, 3, 4, false, 0.3F);
        assertEquals(12, three.points());
    }

    @Test
    void heavyHandsBendProngsAndChipBrittleStones() {
        SetBot bot = new SetBot(set(SettingStyle.PRONG, 1, 4, false, 0.6F), true);
        while (!bot.sim().isComplete()) bot.next();
        SettingSimulation sim = bot.sim();
        assertTrue(sim.defects().contains(SettingDefect.BENT_PRONG), "Over-squeezing bends prongs");
        assertTrue(sim.quality() < 0.5F, "and the setting is poor, got " + sim.quality());
    }

    @Test
    void neverSqueezingLeavesTheStoneLoose() {
        SettingParams p = set(SettingStyle.PRONG, 1, 4, false, 0.3F);
        SettingSimulation sim = new SettingSimulation(p);
        while (!sim.isComplete()) sim.step(false);
        assertTrue(sim.timedOut());
        assertTrue(sim.defects().contains(SettingDefect.LOOSE));
        assertEquals(0F, sim.quality(), 1e-6);
    }

    @Test
    void settingParamsTravelIntact() {
        SettingParams p = set(SettingStyle.BEZEL, 2, 6, true, 0.2F);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        p.write(buf);
        SettingParams q = SettingParams.read(buf);
        assertArrayEquals(p.angles(), q.angles());
        assertArrayEquals(p.order(), q.order());
        assertArrayEquals(p.opposite(), q.opposite());
        assertEquals(p.window(), q.window());
        assertEquals(p.removal(), q.removal());
    }
}
