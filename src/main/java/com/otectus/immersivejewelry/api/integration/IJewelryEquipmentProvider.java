package com.otectus.immersivejewelry.api.integration;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/**
 * An equipment system jewelry can be worn in (spec 69). Curios and the built-in slots are providers; another
 * accessory mod can register its own. Called on the server when a player's equipment is re-examined, never per tick.
 */
@FunctionalInterface
public interface IJewelryEquipmentProvider {
    /**
     * Reports every stack the player wears in this system. {@code slotKey} must be stable and unique per slot
     * (for example {@code curios:ring/1}), because effect state and attribute modifiers are keyed by it.
     */
    void collect(Player player, BiConsumer<String, ItemStack> out);
}
