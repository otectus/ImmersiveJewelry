package com.otectus.immersivejewelry.client.presentation.worn;

import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The client's copy of what players wear in the built-in slots (sanitized snapshots), by entity id. Presentation only. */
public final class WornAppearance {
    private static final Map<Integer, List<ItemStack>> BY_ENTITY = new HashMap<>();

    public static void receive(int entity, List<ItemStack> slots) {
        if (slots.stream().allMatch(ItemStack::isEmpty)) BY_ENTITY.remove(entity);
        else BY_ENTITY.put(entity, List.copyOf(slots));
    }

    public static List<ItemStack> of(int entity) {
        return BY_ENTITY.getOrDefault(entity, List.of());
    }

    public static void clear() {
        BY_ENTITY.clear();
    }

    private WornAppearance() {}
}
