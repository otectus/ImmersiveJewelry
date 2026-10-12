package com.otectus.immersivejewelry.api.integration;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Decides whether a worn piece may be active for a player. A refused piece stays where it is worn but contributes
 * nothing, exactly like a broken one, until the gate allows it; the built-in jewelry slots also refuse to take it.
 * Asked on both sides (the built-in slot menu runs on the client too), whenever the wearer's jewelry is re-examined,
 * never per tick. Keep it cheap and side-effect free; a gate that throws counts as allowing.
 */
@FunctionalInterface
public interface IJewelryWearGate {
    boolean mayWear(Player player, ItemStack piece, JewelryData data);
}
