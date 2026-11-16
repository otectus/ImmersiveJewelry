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
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Shared plumbing of the two workstations: the canonical contents and work live in the subclasses; this class keeps
 * who operates the station now (transient: an operator lease never survives a restart or a chunk unload) and sends the
 * public state to the players tracking the chunk, at most ten times a second while work is going on.
 *
 * <p>Each station is also its own GeckoLib animatable (one rig instance per station; a demonstration's station in a Ponder
 * level is a separate instance). No controllers are registered: the rigs are posed on every render from the station's
 * synced state ({@code client/presentation/geo}), so nothing animates on GeckoLib's model-wide clock and nothing here
 * runs on a server.</p>
 */
public abstract class StationBlockEntity extends BlockEntity implements GeoBlockEntity {
    /** Ticks between two state updates while the station keeps changing (10 per second). */
    private static final int SYNC_INTERVAL = 2;
    private final AABB renderBounds;
    @Nullable
    private UUID operator;
    @Nullable
    private String operatorName;
    /** The next input step the server expects from the operator (prediction rebases on it). Transient. */
    private int ackStep;
    private boolean syncPending;
    private long lastSync;
    /** Client: counts the server states received, so behaviours notice a new authoritative state. */
    private int clientVersion;
    private final AnimatableInstanceCache rigCache = GeckoLibUtil.createInstanceCache(this);

    protected StationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // Tools and the work reach a little past the block; one bounded box, never the whole neighbourhood.
        renderBounds = new AABB(pos).inflate(2D / 16D).expandTowards(0, 0.4, 0);
    }

    @Override
    public AABB getRenderBoundingBox() { return renderBounds; }

    /** No controllers: the rigs are posed from synced state on every render (see the class comment). */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return rigCache;
    }

    @Nullable
    public UUID operator() {
        return operator;
    }

    @Nullable
    public String operatorName() {
        return operatorName;
    }

    public boolean isOperated() {
        return operator != null;
    }

    /** Someone other than {@code player} operates the station. */
    public boolean isOperatedByOther(UUID player) {
        return operator != null && !operator.equals(player);
    }

    public void setOperator(@Nullable UUID player, @Nullable String name) {
        operator = player;
        operatorName = player == null ? null : name;
        sync();
    }

    public int ackStep() {
        return ackStep;
    }

    public void setAckStep(int step) {
        ackStep = step;
    }

    /** Whether someone works at the station: drives idle animation on clients. */
    public boolean isWorking() {
        return operator != null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        writeTransient(tag);
        return tag;
    }

    protected void writeTransient(CompoundTag tag) {
        if (operator != null) {
            tag.putUUID("Operator", operator);
            if (operatorName != null) tag.putString("OperatorName", operatorName);
        }
        tag.putInt("AckStep", ackStep);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
        operator = tag.hasUUID("Operator") ? tag.getUUID("Operator") : null;
        operatorName = tag.contains("OperatorName") ? tag.getString("OperatorName") : null;
        ackStep = tag.getInt("AckStep");
        clientVersion++;
        onClientUpdate();
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) handleUpdateTag(tag);
    }

    /** Client: how many server states arrived so far. */
    public int clientVersion() {
        return clientVersion;
    }

    /** Client: the server's state just arrived. */
    protected void onClientUpdate() {}

    /** Saves and sends the state now (discrete changes: an item moved, a stage changed). */
    protected void sync() {
        setChanged();
        syncPending = false;
        if (level != null && !level.isClientSide) {
            lastSync = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Saves now and sends the state with the next throttled update (continuous work). */
    public void syncSoon() {
        setChanged();
        syncPending = true;
    }

    /** Server tick: flushes a pending update at most every {@link #SYNC_INTERVAL} ticks. */
    protected void flushSync() {
        if (!syncPending || level == null || level.isClientSide) return;
        if (level.getGameTime() - lastSync < SYNC_INTERVAL) return;
        sync();
    }
}
