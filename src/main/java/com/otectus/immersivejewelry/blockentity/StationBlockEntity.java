package com.otectus.immersivejewelry.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Shared plumbing of the two stations: full-state client sync and a transient player lock for minigames. */
public abstract class StationBlockEntity extends BlockEntity {
    @Nullable
    private UUID lockedBy; // transient: locks never survive a restart

    protected StationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isLocked() {
        return lockedBy != null;
    }

    public boolean isLockedFor(UUID player) {
        return lockedBy != null && !lockedBy.equals(player);
    }

    @Nullable
    public UUID lockedBy() {
        return lockedBy;
    }

    public void lock(UUID player) {
        lockedBy = player;
        sync();
    }

    public void unlock() {
        lockedBy = null;
        sync();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        tag.putBoolean("Working", lockedBy != null);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Client copy: whether someone is working at the station (drives animation). */
    protected boolean clientWorking;

    public boolean isWorking() {
        return level != null && level.isClientSide ? clientWorking : lockedBy != null;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
        clientWorking = tag.getBoolean("Working");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) handleUpdateTag(tag);
    }

    protected void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
