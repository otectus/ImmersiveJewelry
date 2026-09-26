package com.otectus.immersivejewelry.blockentity;

import com.otectus.immersivejewelry.minigame.SessionManager;
import com.otectus.immersivejewelry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Jeweler's Bench. The workpiece (a blank, or a finished piece being reworked) sits on the bench. While stones
 * are being set, the stones and finish materials taken for the piece sit in the bench's persisted escrow: consumed
 * exactly once on completion, refunded exactly once on cancellation. Escrow left by an interrupted session (chunk
 * unload, crash) is dropped at the bench when it next loads.
 */
public class JewelersBenchBlockEntity extends StationBlockEntity {
    private ItemStack workpiece = ItemStack.EMPTY;
    private final List<ItemStack> escrow = new ArrayList<>();
    /** The loose stone shown on the bench pin while it is being set (display only). */
    private ItemStack displayStone = ItemStack.EMPTY;
    private float progress;
    private boolean stale;

    public JewelersBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.JEWELERS_BENCH.get(), pos, state);
    }

    public ItemStack workpiece() { return workpiece; }
    public boolean hasWorkpiece() { return !workpiece.isEmpty(); }
    public boolean hasEscrow() { return !escrow.isEmpty(); }
    public List<ItemStack> escrow() { return List.copyOf(escrow); }
    public ItemStack displayStone() { return displayStone; }
    /** Share of prongs secured in the running session, for rendering. */
    public float progress() { return progress; }

    public void setWorkpiece(ItemStack stack) {
        workpiece = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        sync();
    }

    public ItemStack takeWorkpiece() {
        ItemStack out = workpiece;
        workpiece = ItemStack.EMPTY;
        sync();
        return out;
    }

    public void beginSession(UUID player, List<ItemStack> taken, ItemStack shownStone) {
        escrow.clear();
        taken.forEach(s -> escrow.add(s.copy()));
        displayStone = shownStone.copyWithCount(1);
        progress = 0F;
        lock(player);
    }

    public void setProgress(float value) {
        if (Math.abs(value - progress) < 0.01F) return;
        progress = value;
        sync();
    }

    /** The piece is finished: the escrow is consumed and the result replaces the workpiece. */
    public void completeSession(ItemStack result) {
        escrow.clear();
        displayStone = ItemStack.EMPTY;
        progress = 0F;
        workpiece = result.isEmpty() ? ItemStack.EMPTY : result.copyWithCount(1);
        unlock();
    }

    /** Removes and returns the escrow for a refund. */
    public List<ItemStack> takeEscrow() {
        List<ItemStack> out = new ArrayList<>(escrow);
        escrow.clear();
        displayStone = ItemStack.EMPTY;
        progress = 0F;
        unlock();
        return out;
    }

    public void dropEscrow() {
        if (level == null) return;
        for (ItemStack stack : takeEscrow()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
        }
    }

    public void dropContents() {
        dropEscrow();
        if (level != null && !workpiece.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, workpiece);
            workpiece = ItemStack.EMPTY;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Escrow in a freshly loaded bench belongs to a session that no longer exists.
        if (level != null && !level.isClientSide && !escrow.isEmpty()) stale = true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, JewelersBenchBlockEntity bench) {
        if (bench.stale) {
            bench.stale = false;
            if (SessionManager.atStation(level, pos) == null) bench.dropEscrow();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) SessionManager.onStationUnloaded(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!workpiece.isEmpty()) tag.put("Workpiece", workpiece.save(new CompoundTag()));
        ListTag list = new ListTag();
        for (ItemStack stack : escrow) list.add(stack.save(new CompoundTag()));
        tag.put("Escrow", list);
        if (!displayStone.isEmpty()) tag.put("DisplayStone", displayStone.save(new CompoundTag()));
        tag.putFloat("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        workpiece = tag.contains("Workpiece", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Workpiece")) : ItemStack.EMPTY;
        escrow.clear();
        for (Tag t : tag.getList("Escrow", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) t);
            if (!stack.isEmpty()) escrow.add(stack);
        }
        displayStone = tag.contains("DisplayStone", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("DisplayStone")) : ItemStack.EMPTY;
        progress = tag.getFloat("Progress");
    }
}
