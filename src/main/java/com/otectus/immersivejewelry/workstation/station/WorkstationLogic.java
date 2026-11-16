package com.otectus.immersivejewelry.workstation.station;

import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.util.Feedback;
import com.otectus.immersivejewelry.workstation.StationAccess;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The server rules Diegesis asks about while a player works at a Jewelry station. They only grant and end the operator
 * lease: every real change goes through Jewelry's own commands and input, which check that lease again. Library
 * slots, custom actions and controls are refused outright, so the library offers no second path to the work.
 */
final class WorkstationLogic implements StationLogic {

    WorkstationLogic(StationContext station) {
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        if (!(player instanceof ServerPlayer sp) || !(station.blockEntity() instanceof StationBlockEntity be)) return;
        if (!StationAccess.mayAct(sp, station.pos()) || WorkSessionManager.acquire(sp, be) == null) {
            Feedback.fail(sp, "station_in_use");
            Diegesis.close(sp);
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
        return false;
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return false;
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        // Not used: Jewelry's own channel carries every action, validated against the lease.
    }
}
