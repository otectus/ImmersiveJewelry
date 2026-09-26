package com.otectus.immersivejewelry.minigame.cutting;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.minigame.JewelrySession;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** A running cut at a Lapidary Wheel: the stone as mounted, the chosen cut, and the server's copy of the simulation. */
public final class CuttingSession extends JewelrySession {
    public final GemData stone;
    public final ResourceLocation cut;
    public final boolean recut;
    public final CutSimulation sim;
    public int effectsThrottle;

    public CuttingSession(int id, UUID player, ResourceKey<Level> dimension, BlockPos pos, long now, long startMs, GemData stone,
                          ResourceLocation cut, boolean recut, CutParams params) {
        super(id, player, dimension, pos, now, startMs);
        this.stone = stone;
        this.cut = cut;
        this.recut = recut;
        this.sim = new CutSimulation(params);
    }

    @Override
    public int maxSteps() {
        return sim.params().maxSteps();
    }

    @Override
    public boolean isComplete() {
        return sim.isComplete();
    }

    @Override
    public void applyStep(int index, int x, int y, boolean pressed) {
        sim.step(x, y, pressed);
        nextStep++;
    }
}
