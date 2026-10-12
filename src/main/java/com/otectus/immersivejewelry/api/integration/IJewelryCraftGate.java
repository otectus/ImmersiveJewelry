package com.otectus.immersivejewelry.api.integration;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Decides whether a jeweler may set a design at all. Asked when setting starts, after the design, stones and finish
 * were validated and before anything is taken into escrow. Progression mods gate the making of jewelry here rather
 * than after the piece exists. Server thread only; a gate that throws is skipped.
 */
@FunctionalInterface
public interface IJewelryCraftGate {
    /** A player-facing reason to refuse making {@code design} from {@code blank} with {@code stones}, or empty to allow it. */
    Optional<Component> deny(ServerPlayer jeweler, JewelryDesignRecipe design, BlankData blank, List<GemData> stones);
}
