package com.otectus.immersivejewelry.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Posted after a piece is reworked at the Jeweler's Bench. Server side, not cancellable. */
public class JewelryReworkedEvent extends PlayerEvent {
    public enum Action { STONES_REMOVED, REPAIRED, REFINISHED, RETIGHTENED }

    private final Action action;
    private final ItemStack result;

    public JewelryReworkedEvent(ServerPlayer player, Action action, ItemStack result) {
        super(player);
        this.action = action;
        this.result = result;
    }

    public Action getAction() { return action; }
    /** The piece (or, after removal, the frame) as it is now. */
    public ItemStack getPiece() { return result; }
}
