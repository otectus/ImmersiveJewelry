package com.otectus.immersivejewelry.blockentity;

import com.otectus.immersivejewelry.item.LapItem;
import com.otectus.immersivejewelry.registry.ModBlockEntities;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The Lapidary Wheel's canonical contents: the stone on the quill and the installed lap. A cut in progress is part of
 * the stone itself ({@link CutWork}), so the stone always carries its own progress and mistakes: taken off, carried
 * away or dropped when the wheel breaks, it is resumed exactly where it was. The wheel keeps a parsed copy of that work
 * while the stone sits here and writes every accepted change back into the stone.
 */
public class LapidaryWheelBlockEntity extends StationBlockEntity {
    private ItemStack stone = ItemStack.EMPTY;
    private ItemStack lap = ItemStack.EMPTY;
    @Nullable
    private CutWork work;
    private boolean workParsed;
    /** Client animation: lap angle and speed. */
    public float angle;
    public float prevAngle;
    public float speed;
    /** Client: contact state as last synced, for sound and spin. */
    private boolean clientContact;

    public LapidaryWheelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAPIDARY_WHEEL.get(), pos, state);
    }

    public ItemStack stone() { return stone; }
    public ItemStack lap() { return lap; }
    public boolean hasStone() { return !stone.isEmpty(); }

    /** Interpolate the tick's forward displacement before reducing modulo 2pi. */
    public float renderAngle(float partialTick) { return prevAngle + speed * partialTick; }

    public LapItem.Tier tier() {
        return lap.getItem() instanceof LapItem l ? l.tier() : LapItem.Tier.SANDSTONE;
    }

    /** The cut planned or under way on the mounted stone. */
    public Optional<CutWork> work() {
        if (!workParsed) {
            work = stone.isEmpty() ? null : CutWork.of(stone).orElse(null);
            workParsed = true;
        }
        return Optional.ofNullable(work);
    }

    public void setStone(ItemStack stack) {
        stone = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        workParsed = false;
        work = null;
        sync();
    }

    public ItemStack takeStone() {
        ItemStack out = stone;
        stone = ItemStack.EMPTY;
        work = null;
        workParsed = true;
        sync();
        return out;
    }

    /** Puts a (new or changed) cut work on the stone and keeps it parsed. */
    public void setWork(@Nullable CutWork value) {
        if (stone.isEmpty()) return;
        if (value == null) CutWork.clear(stone);
        else value.writeTo(stone);
        work = value;
        workParsed = true;
        sync();
    }

    /** The work's simulation changed by input: write it into the stone and send it with the next update. */
    public void workChanged() {
        if (work != null && !stone.isEmpty()) work.writeTo(stone);
        syncSoon();
    }

    /** Installs a lap and returns the one it replaces. */
    public ItemStack swapLap(ItemStack stack) {
        ItemStack old = lap;
        lap = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        sync();
        return old;
    }

    /** One point of lap wear; a worn-out lap crumbles. Returns whether it broke. */
    public boolean wearLap() {
        if (lap.isEmpty() || !lap.isDamageableItem()) return false;
        lap.setDamageValue(lap.getDamageValue() + 1);
        boolean broke = lap.getDamageValue() >= lap.getMaxDamage();
        if (broke) lap = ItemStack.EMPTY;
        sync();
        return broke;
    }

    /** The one removal path: every real item drops exactly once (the stone with its work). */
    public void dropContents() {
        if (level == null) return;
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stone);
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, lap);
        stone = ItemStack.EMPTY;
        lap = ItemStack.EMPTY;
        work = null;
        workParsed = true;
    }

    /** Client: whether the stone touches the lap in the last synced state. */
    public boolean clientContact() {
        return clientContact;
    }

    @Override
    protected void onClientUpdate() {
        workParsed = false;
        clientContact = work().map(w -> w.sim().contact()).orElse(false);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LapidaryWheelBlockEntity wheel) {
        wheel.flushSync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LapidaryWheelBlockEntity wheel) {
        wheel.prevAngle = wheel.angle;
        float target = wheel.isWorking() ? 0.55F : 0F;
        wheel.speed += (target - wheel.speed) * (target > wheel.speed ? 0.08F : 0.03F);
        wheel.angle = (wheel.angle + wheel.speed) % ((float) Math.PI * 2F);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) WorkSessionManager.onStationUnloaded(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!stone.isEmpty()) tag.put("Stone", stone.save(new CompoundTag()));
        if (!lap.isEmpty()) tag.put("Lap", lap.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stone = tag.contains("Stone", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Stone")) : ItemStack.EMPTY;
        lap = tag.contains("Lap", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Lap")) : ItemStack.EMPTY;
        workParsed = false;
        work = null;
    }
}
