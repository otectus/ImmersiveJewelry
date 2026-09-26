package com.otectus.immersivejewelry.equipment.builtin;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.resources.ResourceLocation;

/** The built-in jewelry slots: two rings, an amulet, a bracelet and a circlet (spec 20 defaults). */
public enum SlotType {
    RING("ring", 2), NECKLACE("necklace", 1), BRACELET("bracelet", 1), HEAD("head", 1);

    public static final int TOTAL = 5;
    private static final SlotType[] LAYOUT = {RING, RING, NECKLACE, BRACELET, HEAD};

    private final String id;
    private final int count;

    SlotType(String id, int count) {
        this.id = id;
        this.count = count;
    }

    public String id() { return id; }
    public int count() { return count; }

    public ResourceLocation icon() {
        return ImmersiveJewelry.id("slot/empty_" + id);
    }

    public static SlotType forIndex(int index) {
        return LAYOUT[Math.max(0, Math.min(LAYOUT.length - 1, index))];
    }
}
