package com.otectus.immersivejewelry.minigame.setting;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.minigame.JewelrySession;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** A running setting, retightening or removal at a Jeweler's Bench. */
public final class SettingSession extends JewelrySession {
    public enum Mode { SET, RETIGHTEN, REMOVE }

    public final Mode mode;
    public final InteractionHand hand;
    @Nullable
    public final ResourceLocation design;
    public final List<GemData> stones;
    @Nullable
    public final ResourceLocation finish;
    public final SettingSimulation sim;
    private int lastEvents;

    public SettingSession(int id, UUID player, ResourceKey<Level> dimension, BlockPos pos, long now, long startMs, Mode mode,
                          InteractionHand hand, @Nullable ResourceLocation design, List<GemData> stones, @Nullable ResourceLocation finish,
                          SettingParams params) {
        super(id, player, dimension, pos, now, startMs);
        this.mode = mode;
        this.hand = hand;
        this.design = design;
        this.stones = List.copyOf(stones);
        this.finish = finish;
        this.sim = new SettingSimulation(params);
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
        lastEvents |= sim.step(pressed);
        nextStep++;
    }

    /** Events since the last call (for sounds). */
    public int drainEvents() {
        int e = lastEvents;
        lastEvents = 0;
        return e;
    }
}
