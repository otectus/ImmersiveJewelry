package com.otectus.immersivejewelry.minigame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Common state of a cutting or setting session. The session clock starts at {@code startMs} after the countdown. */
public abstract class JewelrySession {
    public final int id;
    public final UUID playerId;
    public final ResourceKey<Level> dimension;
    public final BlockPos pos;
    public final long createdMs;
    protected long startMs;
    /** The next step the server expects from the client's input stream. */
    protected int nextStep;

    protected JewelrySession(int id, UUID playerId, ResourceKey<Level> dimension, BlockPos pos, long now, long startMs) {
        this.id = id;
        this.playerId = playerId;
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.createdMs = now;
        this.startMs = startMs;
    }

    public GlobalPos station() {
        return GlobalPos.of(dimension, pos);
    }

    public long startMs() {
        return startMs;
    }

    public int nextStep() {
        return nextStep;
    }

    public abstract int maxSteps();

    public abstract boolean isComplete();

    /** Replays one step of client input. */
    public abstract void applyStep(int index, int x, int y, boolean pressed);
}
