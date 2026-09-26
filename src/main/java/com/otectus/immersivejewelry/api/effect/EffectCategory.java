package com.otectus.immersivejewelry.api.effect;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** What kind of help an effect gives. Metals and finishes bias power per category (gold favours MAGIC, for example). */
public enum EffectCategory {
    OFFENSE, DEFENSE, MOBILITY, UTILITY, MAGIC;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static EffectCategory byId(String id) {
        for (EffectCategory c : values()) {
            if (c.id().equals(id)) return c;
        }
        return null;
    }
}
