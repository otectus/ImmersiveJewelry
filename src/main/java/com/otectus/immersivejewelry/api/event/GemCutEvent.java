package com.otectus.immersivejewelry.api.event;

import com.otectus.immersivejewelry.api.gem.GemData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/** Posted when a cut finishes at a Lapidary Wheel. {@code after} is null when the stone shattered. Server side. */
public class GemCutEvent extends PlayerEvent {
    private final GemData before;
    @Nullable
    private final GemData after;
    private final BlockPos wheel;

    public GemCutEvent(ServerPlayer player, GemData before, @Nullable GemData after, BlockPos wheel) {
        super(player);
        this.before = before;
        this.after = after;
        this.wheel = wheel;
    }

    public GemData getBefore() { return before; }
    @Nullable public GemData getAfter() { return after; }
    public BlockPos getWheel() { return wheel; }
}
