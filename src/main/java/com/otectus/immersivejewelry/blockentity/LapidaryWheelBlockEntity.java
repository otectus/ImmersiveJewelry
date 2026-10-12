package com.otectus.immersivejewelry.blockentity;

import com.otectus.immersivejewelry.item.LapItem;
import com.otectus.immersivejewelry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Lapidary Wheel. The stone being cut stays here for the whole session: cancelling, disconnecting or breaking the
 * wheel never loses or duplicates it, and only a completed cut changes it (in place). The lap slot holds the
 * abrasive upgrade.
 */
public class LapidaryWheelBlockEntity extends StationBlockEntity {
    private ItemStack stone = ItemStack.EMPTY;
    private ItemStack lap = ItemStack.EMPTY;
    /** Client animation: wheel angle and speed. */
    public float angle;
    public float prevAngle;
    public float speed;

    public LapidaryWheelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAPIDARY_WHEEL.get(), pos, state);
    }

    public ItemStack stone() { return stone; }

    /** Interpolate the tick's forward displacement before reducing modulo 2pi. */
    public float renderAngle(float partialTick) { return prevAngle + speed * partialTick; }
    public ItemStack lap() { return lap; }
    public boolean hasStone() { return !stone.isEmpty(); }

    public LapItem.Tier tier() {
        return lap.getItem() instanceof LapItem l ? l.tier() : LapItem.Tier.SANDSTONE;
    }

    public void setStone(ItemStack stack) {
        stone = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        sync();
    }

    public ItemStack takeStone() {
        ItemStack out = stone;
        stone = ItemStack.EMPTY;
        sync();
        return out;
    }

    /** Installs a lap and returns the one it replaces. */
    public ItemStack swapLap(ItemStack stack) {
        ItemStack old = lap;
        lap = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        sync();
        return old;
    }

    /** One point of lap wear per finished cut; a worn-out lap crumbles. */
    public boolean wearLap() {
        if (lap.isEmpty() || !lap.isDamageableItem()) return false;
        lap.setDamageValue(lap.getDamageValue() + 1);
        boolean broke = lap.getDamageValue() >= lap.getMaxDamage();
        if (broke) lap = ItemStack.EMPTY;
        sync();
        return broke;
    }

    public void dropContents() {
        if (level == null) return;
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stone);
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, lap);
        stone = ItemStack.EMPTY;
        lap = ItemStack.EMPTY;
    }

    public static void clientTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, LapidaryWheelBlockEntity wheel) {
        wheel.prevAngle = wheel.angle;
        float target = wheel.isWorking() ? 0.55F : 0F;
        wheel.speed += (target - wheel.speed) * (target > wheel.speed ? 0.08F : 0.03F);
        wheel.angle = (wheel.angle + wheel.speed) % ((float) Math.PI * 2F);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) com.otectus.immersivejewelry.minigame.SessionManager.onStationUnloaded(level, worldPosition);
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
    }
}
