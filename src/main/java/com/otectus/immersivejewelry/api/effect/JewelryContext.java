package com.otectus.immersivejewelry.api.effect;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * What an effect sees of the piece it belongs to, on the server. The numbers are already resolved from the piece's
 * construction: gem, cut, metal, workmanship, finish, design and integrity.
 */
public interface JewelryContext {
    ServerPlayer player();

    JewelryData data();

    ResourceLocation form();

    /** The stable key of the slot the piece is worn in plus this effect's index, for attribute modifier ids. */
    String slot();

    /** Effect strength, about 1.0 for a well-made piece. Already includes the metal's and finish's bias for this category. */
    float power();

    /** Multiplier for durations (emerald cuts and burnished finishes last longer). */
    float stability();

    /** Divisor for cooldowns (copper frames respond faster). */
    float responsiveness();

    long gameTime();

    /** Mutable state of this effect on this piece, created on first use and dropped when the piece is unequipped. */
    <T> T state(Supplier<T> factory);

    /** {@code baseTicks} scaled by stability. */
    default int duration(int baseTicks) {
        return Math.max(1, Math.round(baseTicks * stability()));
    }

    /** {@code baseTicks} scaled by responsiveness. */
    default int cooldown(int baseTicks) {
        return Math.max(1, Math.round(baseTicks / Math.max(0.1F, responsiveness())));
    }

    /** True when the named cooldown has run out. */
    boolean ready(String key);

    /** Starts the named cooldown for {@code cooldown(baseTicks)} ticks. */
    void startCooldown(String key, int baseTicks);

    /** Plays the effect's trigger feedback (a quiet chime and a sparkle in the gem's colour) at the wearer. */
    void announce();
}
