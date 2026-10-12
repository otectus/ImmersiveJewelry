package com.otectus.immersivejewelry.api.integration;

import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player difficulty relief for the cutting and setting minigames. Answers multiply the server's difficulty
 * multiplier; the product of all sources is clamped to {@code minigames.minAssistDifficulty..1.0}, so assistance can
 * only ease a session and never past the operator's floor. The client receives the final parameters in the start
 * packet and simulates the same session. Server thread only; a source that throws counts as {@code 1.0}.
 */
@FunctionalInterface
public interface IJewelryAssistSource {
    enum Kind { CUTTING, SETTING }

    double difficultyMultiplier(ServerPlayer player, Kind kind);
}
