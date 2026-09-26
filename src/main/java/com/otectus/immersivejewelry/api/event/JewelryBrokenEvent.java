package com.otectus.immersivejewelry.api.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Posted when a worn piece's Integrity reaches zero: it stops working until repaired. Server side. */
public class JewelryBrokenEvent extends PlayerEvent {
    private final ItemStack item;

    public JewelryBrokenEvent(Player player, ItemStack item) {
        super(player);
        this.item = item;
    }

    public ItemStack getItem() { return item; }
}
