package com.otectus.immersivejewelry.client.compat.ponder;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;

import java.util.function.Supplier;

/**
 * A stretch of hands-on work in a demonstration: for {@code ticks} scene ticks, a fresh {@link Step} (made when the stretch
 * starts, so a replay or a seek starts from the same hands) is applied to the demonstration's own station once per tick.
 * Blocking, like an idle of the same length.
 */
final class WorkInstruction extends TickingInstruction {
    @FunctionalInterface
    interface Step {
        void tick(PonderScene scene, int tick);
    }

    private final Supplier<Step> factory;
    private Step step;
    private int done;

    WorkInstruction(int ticks, Supplier<Step> factory) {
        super(true, ticks);
        this.factory = factory;
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        step = null;
        done = 0;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        step = factory.get();
        done = 0;
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        if (step != null) step.tick(scene, done++);
    }
}
