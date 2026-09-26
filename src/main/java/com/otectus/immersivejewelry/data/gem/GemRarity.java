package com.otectus.immersivejewelry.data.gem;

import java.util.Locale;

/** How rare a gem is: its weight in treasure loot and the Jeweler trade level that sells it rough. */
public enum GemRarity {
    COMMON(12, 1), UNCOMMON(7, 2), RARE(3, 3), EPIC(1, 4);

    private final int lootWeight;
    private final int tradeLevel;

    GemRarity(int lootWeight, int tradeLevel) {
        this.lootWeight = lootWeight;
        this.tradeLevel = tradeLevel;
    }

    public int lootWeight() { return lootWeight; }
    public int tradeLevel() { return tradeLevel; }

    public static GemRarity byId(String id) {
        for (GemRarity r : values()) {
            if (r.name().toLowerCase(Locale.ROOT).equals(id)) return r;
        }
        return COMMON;
    }
}
