package com.otectus.immersivejewelry.workstation;

import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.WorkLeasePacket;
import com.otectus.immersivejewelry.recipe.DesignKnowledge;
import com.otectus.immersivejewelry.util.Feedback;
import com.otectus.immersivejewelry.workstation.station.JewelryStations;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Operator leases: temporary permission for one player to work one station. A lease is granted when the player starts
 * working there (the scene opened, server-approved) and ends on every way out: leaving, walking off, logging out,
 * death, damage, teleporting, changing dimension, the station breaking or unloading, a datapack reload or the server
 * stopping. Ending a lease lifts every tool off the work and keeps the work exactly as it was; it never finishes,
 * refunds or rolls anything back. Leases are never saved, so a restart leaves no stale lock.
 *
 * <p>Input pacing: each lease holds a budget of fixed steps that refills with real time (50 per second, a little
 * tolerance) up to a bounded backlog. A client cannot work faster than the clock, a late batch can use at most that
 * backlog, and older input is dropped rather than fast-forwarded into the work.</p>
 */
public final class WorkSessionManager {
    private static final Map<UUID, Lease> BY_PLAYER = new HashMap<>();
    private static final Map<GlobalPos, Lease> BY_STATION = new HashMap<>();
    private static final Map<UUID, Preferences> PREFERENCES = new HashMap<>();
    private static final Random RANDOM = new Random();
    /** Discrete commands a lease may send per second, and how many at once (its own budget, apart from the library's). */
    public static final int COMMAND_RATE = 20, COMMAND_BURST = 20;
    /** Input silence after which a tool still pressed is lifted (plus the player's latency allowance). */
    private static final long HEARTBEAT_MS = 400L;

    /** A player's gameplay-assist wishes. The server honours them only where its assist policy allows. */
    public record Preferences(boolean widerWindows, boolean gentlerRate) {
        public static final Preferences NONE = new Preferences(false, false);
    }

    public static final class Lease {
        public final UUID player;
        public final GlobalPos station;
        public final int token;
        private int nextStep;
        private int lastSeq = -1;
        private long lastInputMs;
        private double tokens;
        private long refillMs;
        private boolean active;
        private int lastCommand;
        private double commandTokens = COMMAND_BURST;
        private long commandRefillMs;

        Lease(UUID player, GlobalPos station, int token, long now) {
            this.player = player;
            this.station = station;
            this.token = token;
            this.lastInputMs = now;
            this.refillMs = now;
            this.commandRefillMs = now;
        }

        /**
         * Accepts one discrete command: its sequence number must be higher than every one before under this lease (a repeat or
         * an older one is dropped), and the lease's command budget ({@value #COMMAND_RATE} a second, a burst of
         * {@value #COMMAND_BURST}) must have room. A command refused by the budget still uses up its number.
         */
        public boolean admitCommand(int seq, long now) {
            if (seq <= lastCommand) return false;
            lastCommand = seq;
            commandTokens = Math.min(COMMAND_BURST, commandTokens + Math.max(0L, now - commandRefillMs) * COMMAND_RATE / 1000.0);
            commandRefillMs = now;
            if (commandTokens < 1.0) return false;
            commandTokens -= 1.0;
            return true;
        }

        public int lastCommand() { return lastCommand; }

        public int nextStep() { return nextStep; }
        public int lastSeq() { return lastSeq; }

        /**
         * Accepts a batch: returns how many of its samples to apply (from {@code skip}), after duplicate, overlap and
         * pacing checks. {@code skip} is filled with how many leading samples were already applied.
         */
        public int admit(int seq, int firstStep, int count, long now, int latencyMs, int[] skip) {
            skip[0] = 0;
            if (seq <= lastSeq || count <= 0) return 0;
            lastSeq = seq;
            int offset = nextStep - firstStep;
            if (offset >= count) return 0;
            if (offset > 0) skip[0] = offset;
            else if (offset < 0) nextStep = firstStep; // the client paused; nothing is filled in
            int want = count - skip[0];
            refill(now, latencyMs);
            int granted = (int) Math.min(want, Math.floor(tokens));
            tokens -= granted;
            nextStep += want; // what the budget refused is dropped, never applied later
            lastInputMs = now;
            active = true;
            return granted;
        }

        private void refill(long now, int latencyMs) {
            int compensation = Math.min(Math.max(latencyMs, 0), ServerConfig.get(ServerConfig.MAX_LATENCY_COMPENSATION_MS));
            double capacity = (1000.0 + compensation + ServerConfig.get(ServerConfig.TIMING_TOLERANCE_MS)) / 20.0;
            tokens = Math.min(capacity, tokens + (now - refillMs) / 20.0 * 1.05);
            refillMs = now;
        }
    }

    // ------------------------------------------------------------------------------------------------ lookup

    public static Optional<Lease> lease(ServerPlayer player) {
        return Optional.ofNullable(BY_PLAYER.get(player.getUUID()));
    }

    @Nullable
    public static Lease leaseAt(Level level, BlockPos pos) {
        return BY_STATION.get(GlobalPos.of(level.dimension(), pos));
    }

    /** The lease {@code player} holds on the station at {@code pos}, if that is the one they hold. */
    @Nullable
    public static Lease leaseFor(ServerPlayer player, BlockPos pos) {
        Lease lease = BY_PLAYER.get(player.getUUID());
        return lease != null && lease.station.equals(GlobalPos.of(player.level().dimension(), pos)) ? lease : null;
    }

    /** Whether someone other than {@code player} holds the station at {@code pos}. */
    public static boolean heldByOther(ServerPlayer player, BlockPos pos) {
        Lease lease = leaseAt(player.level(), pos);
        return lease != null && !lease.player.equals(player.getUUID());
    }

    public static List<Lease> all() {
        return List.copyOf(BY_PLAYER.values());
    }

    public static Preferences preferences(UUID player) {
        return PREFERENCES.getOrDefault(player, Preferences.NONE);
    }

    public static void setPreferences(ServerPlayer player, Preferences prefs) {
        PREFERENCES.put(player.getUUID(), prefs);
    }

    // ------------------------------------------------------------------------------------------------ granting and ending

    /**
     * Grants {@code player} the station's lease (their previous one ends first). Refused while someone else holds it.
     */
    @Nullable
    public static Lease acquire(ServerPlayer player, StationBlockEntity station) {
        GlobalPos key = GlobalPos.of(player.level().dimension(), station.getBlockPos());
        Lease existing = BY_STATION.get(key);
        if (existing != null && !existing.player.equals(player.getUUID())) return null;
        if (existing != null) return existing;
        release(player.getUUID(), player.server, null, false);
        long now = Util.getMillis();
        int token;
        do token = RANDOM.nextInt(); while (token == 0);
        Lease lease = new Lease(player.getUUID(), key, token, now);
        DesignKnowledge.sync(player);
        BY_PLAYER.put(player.getUUID(), lease);
        BY_STATION.put(key, lease);
        station.setOperator(player.getUUID(), player.getGameProfile().getName());
        station.setAckStep(0);
        send(player, new WorkLeasePacket(station.getBlockPos(), token));
        return lease;
    }

    /**
     * Ends {@code player}'s lease, if any: every tool lifts, the work stays as it is. {@code reason} is shown to the
     * player; {@code closeScene} also ends their focus at the station (when the lease ends from the server's side).
     */
    public static void release(UUID player, @Nullable MinecraftServer server, @Nullable Component reason, boolean closeScene) {
        Lease lease = BY_PLAYER.remove(player);
        if (lease == null) return;
        BY_STATION.remove(lease.station, lease);
        ServerLevel level = server != null ? server.getLevel(lease.station.dimension()) : null;
        if (level != null && level.isLoaded(lease.station.pos())) {
            if (level.getBlockEntity(lease.station.pos()) instanceof StationBlockEntity station) {
                neutral(station);
                station.setOperator(null, null);
            }
        }
        ServerPlayer online = server != null ? server.getPlayerList().getPlayer(player) : null;
        if (online != null) {
            send(online, new WorkLeasePacket(lease.station.pos(), 0));
            if (reason != null) Feedback.fail(online, reason);
            if (closeScene) JewelryStations.close(online);
        }
    }

    /** Lifts every tool off the station's work at once (no further grinding, squeezing or rubbing). */
    static void neutral(StationBlockEntity station) {
        if (station instanceof LapidaryWheelBlockEntity wheel) {
            wheel.work().ifPresent(w -> {
                if (w.sim().contact() || w.sim().height() < 1F) {
                    w.sim().neutral();
                    wheel.workChanged();
                }
            });
        } else if (station instanceof JewelersBenchBlockEntity bench && bench.work() != null) {
            var work = bench.work();
            if (work.setting() != null) work.setting().neutral();
            if (work.surface() != null) work.surface().neutral();
            bench.workChanged();
        }
    }

    // ------------------------------------------------------------------------------------------------ the ways out

    public static void onLogout(ServerPlayer player) {
        release(player.getUUID(), player.server, null, false);
        PREFERENCES.remove(player.getUUID());
    }

    public static void onDeath(ServerPlayer player) {
        release(player.getUUID(), player.server, Feedback.message("session_interrupted"), true);
    }

    /** Damage or displacement: release contact and leave at once. */
    public static void onInterrupted(ServerPlayer player) {
        if (BY_PLAYER.containsKey(player.getUUID())) {
            release(player.getUUID(), player.server, Feedback.message("work_interrupted"), true);
        }
    }

    public static void onStationRemoved(Level level, BlockPos pos) {
        Lease lease = leaseAt(level, pos);
        if (lease != null && level.getServer() != null) {
            release(lease.player, level.getServer(), Feedback.message("station_missing"), true);
        }
    }

    public static void onStationUnloaded(Level level, BlockPos pos) {
        Lease lease = leaseAt(level, pos);
        if (lease != null && level.getServer() != null) {
            release(lease.player, level.getServer(), Feedback.message("session_interrupted"), true);
        }
    }

    /** A datapack reload: active manipulation stops; work continues later with the parameters it was planned with. */
    public static void onReload(MinecraftServer server) {
        for (Lease lease : all()) release(lease.player, server, Feedback.message("work_reloaded"), true);
    }

    public static void onServerStopping(MinecraftServer server) {
        for (Lease lease : all()) release(lease.player, server, null, false);
        BY_PLAYER.clear();
        BY_STATION.clear();
        PREFERENCES.clear();
    }

    /** Heartbeat: a lease whose input went quiet while a tool was down gets that tool lifted. */
    public static void tick(MinecraftServer server) {
        if (BY_PLAYER.isEmpty()) return;
        long now = Util.getMillis();
        for (Lease lease : all()) {
            ServerPlayer player = server.getPlayerList().getPlayer(lease.player);
            if (player == null) {
                release(lease.player, server, null, false);
                continue;
            }
            int allowance = Math.min(Math.max(player.latency, 0), ServerConfig.get(ServerConfig.MAX_LATENCY_COMPENSATION_MS));
            if (lease.active && now - lease.lastInputMs > HEARTBEAT_MS + allowance) {
                lease.active = false;
                ServerLevel level = server.getLevel(lease.station.dimension());
                if (level != null && level.isLoaded(lease.station.pos())
                        && level.getBlockEntity(lease.station.pos()) instanceof StationBlockEntity station) {
                    neutral(station);
                }
            }
        }
    }

    private static void send(ServerPlayer player, Object packet) {
        if (player.connection != null && !(player instanceof net.minecraftforge.common.util.FakePlayer)) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    private WorkSessionManager() {}
}
