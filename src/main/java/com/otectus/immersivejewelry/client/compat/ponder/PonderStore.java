package com.otectus.immersivejewelry.client.compat.ponder;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a demonstration's jeweler carries between two instructions (a packed Unfinished piece): per scene, set again from
 * the start on every replay, never anyone's inventory.
 */
final class PonderStore {
    private static final Map<PonderScene, ItemStack> CARRIED = new WeakHashMap<>();

    static void put(PonderScene scene, ItemStack stack) {
        CARRIED.put(scene, stack.copy());
    }

    static ItemStack take(PonderScene scene) {
        ItemStack stack = CARRIED.remove(scene);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    static void clear() {
        CARRIED.clear();
    }

    private PonderStore() {}
}
