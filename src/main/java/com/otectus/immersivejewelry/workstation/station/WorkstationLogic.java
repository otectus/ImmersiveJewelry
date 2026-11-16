package com.otectus.immersivejewelry.workstation.station;

import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.util.Feedback;
import com.otectus.immersivejewelry.workstation.BenchOperations;
import com.otectus.immersivejewelry.workstation.StationAccess;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The server rules Diegesis asks about while a player works at a Jewelry station. They grant and end the operator lease,
 * and they are the only gate of the library's own item gestures, which reach exactly two groups of the bench:
 * <ul>
 *   <li>{@code supplies} (the three jars): anything the bench takes as a supply now ({@link BenchOperations#isSupply} with the
 *   real bench, the clamped piece's repair metal included); taking one out is always allowed, since a supply is paid for only
 *   at its first effective use and nothing there belongs to a work yet;</li>
 *   <li>{@code staging} (the drawer): what {@link BenchOperations#isStageable} names, while the drawer is open. Its being open
 *   is checked here, not only drawn: a shut drawer refuses every native gesture, whatever a client shows.</li>
 * </ul>
 * Every gesture also needs this player's lease on this station and their reach ({@link StationAccess#mayAct}), and the group
 * must really be bound to the bench's own array ({@code StationState.isBound}): a group the bench could not bind (a Diegesis
 * whose host API changed) would only hold stacks for the length of one use, so it takes and gives nothing. The library
 * calls these before it touches a stack, so a refusal leaves the cursor, the inventory and the bench as they were. Custom
 * actions and every other group stay refused: the wheel, the clamp, the sockets, the tray and the dock are Jewelry operations
 * only.
 */
final class WorkstationLogic implements StationLogic {

    WorkstationLogic(StationContext station) {
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        if (!(player instanceof ServerPlayer sp) || !(station.blockEntity() instanceof StationBlockEntity be)) return;
        if (!StationAccess.mayAct(sp, station.pos()) || WorkSessionManager.acquire(sp, be) == null) {
            Feedback.fail(sp, "station_in_use");
            JewelryStations.close(sp);
        }
    }

    @Override
    public void onClose(StationContext station, Player player) {
        if (player instanceof ServerPlayer sp) {
            WorkSessionManager.Lease lease = WorkSessionManager.leaseFor(sp, station.pos());
            if (lease != null) WorkSessionManager.release(sp.getUUID(), sp.server, null, false);
        }
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        JewelersBenchBlockEntity bench = operatedBench(station, player, slot);
        if (bench == null) return false;
        return switch (slot.group()) {
            case JewelryStations.SUPPLIES -> BenchOperations.isSupply(stack, bench);
            case JewelryStations.STAGING -> bench.drawerOpen() && BenchOperations.isStageable(stack);
            default -> false;
        };
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        JewelersBenchBlockEntity bench = operatedBench(station, player, slot);
        if (bench == null) return false;
        return switch (slot.group()) {
            case JewelryStations.SUPPLIES -> true;
            case JewelryStations.STAGING -> bench.drawerOpen();
            default -> false;
        };
    }

    /**
     * The bench whose native slot {@code slot} this player may use now: their lease on it, within reach, a named group and an
     * index inside it. Null for anything else (the wheel has no slots at all).
     */
    static JewelersBenchBlockEntity operatedBench(StationContext station, Player player, SlotRef slot) {
        if (!(player instanceof ServerPlayer sp) || !(station.blockEntity() instanceof JewelersBenchBlockEntity bench)) return null;
        if (WorkSessionManager.leaseFor(sp, station.pos()) == null || !StationAccess.mayAct(sp, station.pos())) return null;
        if (!JewelryStations.nativeStorage() || !bound(station, slot.group())) return null;
        int size = switch (slot.group()) {
            case JewelryStations.SUPPLIES -> JewelersBenchBlockEntity.SUPPLIES;
            case JewelryStations.STAGING -> JewelersBenchBlockEntity.STAGING;
            default -> 0;
        };
        return slot.index() >= 0 && slot.index() < size ? bench : null;
    }

    private static boolean bound(StationContext station, String group) {
        try {
            return station.state().isBound(group);
        } catch (LinkageError e) {
            com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.disable(
                    com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.Feature.NATIVE_STORAGE, "the bench's slot gate", e);
            return false;
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        // Not used: Jewelry's own channel carries every action, validated against the lease.
    }
}
