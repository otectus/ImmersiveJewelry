package com.otectus.immersivejewelry.effect;

import com.otectus.immersivejewelry.api.effect.JewelryHook;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-side registry of every player's active jewelry. Server thread only. */
public final class JewelryWearers {
    private static final Map<UUID, WearerState> STATES = new HashMap<>();

    static WearerState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), WearerState::new);
    }

    /** Equipment changed: the player's jewelry is re-examined on their next tick. */
    public static void markDirty(Player player) {
        if (player instanceof ServerPlayer sp && !sp.level().isClientSide) state(sp).dirty = true;
    }

    /** Definitions changed (datapack reload): every piece is resolved again and its effects re-applied. */
    public static void invalidateAll() {
        for (WearerState s : STATES.values()) {
            s.dirty = true;
            s.fullReset = true;
        }
    }

    public static void tick(ServerPlayer player) {
        state(player).tick(player);
    }

    /** Runs {@code action} for every active effect of the player that listens to {@code hook}. */
    public static void dispatch(ServerPlayer player, JewelryHook hook, Consumer<ActivePiece.Active> action) {
        WearerState s = STATES.get(player.getUUID());
        if (s != null) s.forEach(hook, action);
    }

    public static boolean hasHook(ServerPlayer player, JewelryHook hook) {
        WearerState s = STATES.get(player.getUUID());
        return s != null && s.hooks.contains(hook);
    }

    public static float profile(ServerPlayer player, String key) {
        WearerState s = STATES.get(player.getUUID());
        return s == null ? 0F : s.profile(key);
    }

    /** Number of active pieces (for commands and tests). */
    public static int activePieces(ServerPlayer player) {
        WearerState s = STATES.get(player.getUUID());
        return s == null ? 0 : s.pieces.size();
    }

    /** Refreshes now instead of on the next tick (tests, commands). */
    public static void refreshNow(ServerPlayer player) {
        state(player).refresh(player);
    }

    public static void onLogout(ServerPlayer player) {
        WearerState s = STATES.remove(player.getUUID());
        if (s != null) s.clear(player, true);
    }

    /** The player entity was replaced (respawn, leaving the End): nothing applied to the old entity carries over. */
    public static void onClone(ServerPlayer player) {
        WearerState s = STATES.remove(player.getUUID());
        if (s != null) s.clear(player, false);
        state(player).dirty = true;
    }

    public static void onServerStopping(MinecraftServer server) {
        for (Map.Entry<UUID, WearerState> e : STATES.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            e.getValue().clear(player, player != null);
        }
        STATES.clear();
    }

    private JewelryWearers() {}
}
