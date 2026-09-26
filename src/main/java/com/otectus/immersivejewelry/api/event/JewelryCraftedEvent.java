package com.otectus.immersivejewelry.api.event;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Posted on the Forge bus when a jeweler finishes setting stones into a new piece, before it is placed on the bench.
 * {@link #getItem()} is the live stack (listeners may add to it). Server side, not cancellable.
 */
public class JewelryCraftedEvent extends PlayerEvent {
    private final ItemStack item;
    private final JewelryData data;
    private final BlockPos bench;

    public JewelryCraftedEvent(ServerPlayer player, ItemStack item, JewelryData data, BlockPos bench) {
        super(player);
        this.item = item;
        this.data = data;
        this.bench = bench;
    }

    public ItemStack getItem() { return item; }
    public JewelryData getData() { return data; }
    public BlockPos getBench() { return bench; }
}
