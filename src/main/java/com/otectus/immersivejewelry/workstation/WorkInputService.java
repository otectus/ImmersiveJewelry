package com.otectus.immersivejewelry.workstation;

import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.network.packet.WorkCommandPacket;
import com.otectus.immersivejewelry.network.packet.WorkInputPacket;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The single entry point for an operator's network input. Both kinds of message are checked against the lease the
 * server holds (token, station, dimension), the player's real position and the station's current work before anything
 * reaches an operation; a rejected or repeated message changes nothing.
 */
public final class WorkInputService {

    /** A batch of continuous control samples. */
    public static void input(ServerPlayer player, WorkInputPacket packet) {
        input(player, packet, Util.getMillis());
    }

    /** {@code nowMs}: the server clock when the batch arrived (GameTests pass their own). */
    public static void input(ServerPlayer player, WorkInputPacket packet, long nowMs) {
        WorkSessionManager.Lease lease = WorkSessionManager.leaseFor(player, packet.pos());
        if (lease == null || lease.token != packet.token() || packet.samples().isEmpty()) return;
        if (!StationAccess.mayWork(player, packet.pos())) {
            WorkSessionManager.release(player.getUUID(), player.server, null, true);
            return;
        }
        if (!(player.level().getBlockEntity(packet.pos()) instanceof StationBlockEntity station)) return;
        int[] skip = new int[1];
        int granted = lease.admit(packet.seq(), packet.firstStep(), packet.samples().size(), nowMs, player.latency, skip);
        station.setAckStep(lease.nextStep());
        if (granted <= 0) {
            station.syncSoon();
            return;
        }
        List<WorkSample> samples = packet.samples().subList(skip[0], skip[0] + granted);
        if (station instanceof LapidaryWheelBlockEntity wheel) WheelOperations.input(player, wheel, packet.workId(), samples);
        else if (station instanceof JewelersBenchBlockEntity bench) BenchOperations.input(player, bench, packet.workId(), samples);
    }

    private static long lastRefusalLog;

    /**
     * A refused discrete command, for diagnosis: logged at most once a second (a flood never floods the log), at debug level
     * unless {@code -Dimmersive_jewelry.logRefusals=true}. Never shown to other players.
     */
    static void refused(ServerPlayer player, WorkCommand command, String reason) {
        long now = Util.getMillis();
        if (now - lastRefusalLog < 1000L) return;
        lastRefusalLog = now;
        if (Boolean.getBoolean("immersive_jewelry.logRefusals")) {
            com.otectus.immersivejewelry.ImmersiveJewelry.LOGGER.info("Refused {} from {}: {}", command, player.getGameProfile().getName(), reason);
        } else {
            com.otectus.immersivejewelry.ImmersiveJewelry.LOGGER.debug("Refused {} from {}: {}", command, player.getGameProfile().getName(), reason);
        }
    }

    /** A discrete action. */
    public static void command(ServerPlayer player, WorkCommandPacket packet) {
        command(player, packet, Util.getMillis());
    }

    /**
     * {@code nowMs}: the server clock when the command arrived (GameTests pass their own). Checked in this order: the lease,
     * the player's reach, the arguments' size, then the command's sequence and the lease's command budget, so a repeated,
     * late or flooding command changes nothing.
     */
    public static void command(ServerPlayer player, WorkCommandPacket packet, long nowMs) {
        WorkSessionManager.Lease lease = WorkSessionManager.leaseFor(player, packet.pos());
        if (lease == null || lease.token != packet.token()) {
            refused(player, packet.command(), "no lease");
            return;
        }
        if (!StationAccess.mayAct(player, packet.pos())) {
            refused(player, packet.command(), "out of reach");
            return;
        }
        if (packet.args().sizeInBytes() > WorkCommandPacket.MAX_ARGS_BYTES) {
            refused(player, packet.command(), "arguments too large");
            return;
        }
        if (!lease.admitCommand(packet.seq(), nowMs)) {
            refused(player, packet.command(), "sequence " + packet.seq() + " after " + lease.lastCommand() + " or over budget");
            return;
        }
        var be = player.level().getBlockEntity(packet.pos());
        if (be instanceof LapidaryWheelBlockEntity wheel) {
            WheelOperations.command(player, wheel, packet.command(), packet.workId(), packet.args());
        } else if (be instanceof JewelersBenchBlockEntity bench) {
            BenchOperations.command(player, bench, packet.command(), packet.workId(), packet.args());
        }
    }

    private WorkInputService() {}
}
