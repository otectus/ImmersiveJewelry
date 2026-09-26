package com.otectus.immersivejewelry.api.effect;

import net.minecraft.network.chat.Component;

/** Collects tooltip lines for an effect, with the piece's resolved numbers and formatting helpers. */
public interface EffectDescription {
    float power();

    float stability();

    float responsiveness();

    /** Adds a translated line; arguments are usually made with the helpers below. */
    void line(String key, Object... args);

    void line(Component component);

    /** "12%" for 0.12. */
    Component percent(float fraction);

    /** "4.5s" for 90 ticks. */
    Component seconds(float ticks);

    /** A number with at most one decimal. */
    Component number(float value);

    /** "1.5 ♥" for 3 health points. */
    Component hearts(float health);

    default int duration(int baseTicks) {
        return Math.max(1, Math.round(baseTicks * stability()));
    }

    default int cooldown(int baseTicks) {
        return Math.max(1, Math.round(baseTicks / Math.max(0.1F, responsiveness())));
    }
}
