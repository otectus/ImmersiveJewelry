package com.otectus.immersivejewelry.effect;

import com.otectus.immersivejewelry.effect.builtin.BuiltinEffectTypes;

/** Registers Immersive Jewelry's own effect types (called from the mod constructor, before any datapack loads). */
public final class BuiltinEffects {

    public static void register() {
        BuiltinEffectTypes.register();
    }

    private BuiltinEffects() {}
}
