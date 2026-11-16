package com.otectus.immersivejewelry.client.presentation.anim;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * The jeweler's body clips ({@code assets/immersive_jewelry/player_animation/<id>.json} and their {@code _reduced} twins,
 * generated from {@code art/jewelry_poses.py}). Lengths match the authored clips; a looping clip is a held working motion,
 * the others are one-shot responses to an accepted change.
 */
public enum JewelerClip {
    WHEEL_READY(20, true),
    WHEEL_GRIND(24, true),
    WHEEL_LIFT(10, false),
    WHEEL_COOL(16, false),
    WHEEL_MOUNT(16, false),
    LAP_CHANGE(20, false),
    INSPECT_LOUPE(20, true),
    BENCH_READY(24, true),
    TWEEZERS_SEAT(30, true),
    PLIERS_SQUEEZE(16, true),
    BEZEL_PUSH(32, true),
    POLISH_WIPE(20, true),
    BURNISH_PASS(24, true),
    PATINA_DAB(14, true),
    PRY_STONE(20, true),
    REPAIR_TRUE(22, true),
    BENCH_PLACE(14, false),
    BENCH_COLLECT(14, false),
    INSPECT_PIECE(30, false);

    public final int length;
    public final boolean loop;

    JewelerClip(int length, boolean loop) {
        this.length = length;
        this.loop = loop;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ResourceLocation id(boolean reduced) {
        return ImmersiveJewelry.id(reduced ? id() + "_reduced" : id());
    }
}
