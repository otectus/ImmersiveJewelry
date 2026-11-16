package com.otectus.immersivejewelry.blockentity;

import com.otectus.immersivejewelry.workstation.station.DiegesisStations;
import com.otectus.immersivejewelry.workstation.station.JewelryStations;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationHost;
import com.pockyl.diegesis.api.state.StationState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Jeweler's Bench that is also a Diegesis {@link StationHost}: its {@link StationState} (made with the registered bench schema,
 * the instance the library recognises) binds the {@code supplies} and {@code staging} groups to the bench's own arrays, on both
 * sides, before anything is saved or drawn. The arrays stay the only owner, the library reads and writes them through
 * {@link SlotStorage}, and its {@code save()} leaves both groups out; what it saves on its own is where staged stacks stand.
 *
 * <p>Created only by {@link JewelersBenchBlockEntity#create} when the library's host API is there; every other class names the
 * base class, so a Diegesis without that API never loads this one. The save format is the base class's.</p>
 */
public final class HostedBenchBlockEntity extends JewelersBenchBlockEntity implements StationHost {
    private final StationState station = new StationState(DiegesisStations.BENCH_SCHEMA);

    private HostedBenchBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
        station.bindSlots(JewelryStations.SUPPLIES, new Storage(supplies, GEN_SUPPLY));
        station.bindSlots(JewelryStations.STAGING, new Storage(staging, GEN_STAGING));
    }

    /** Declared as the base class, so naming this factory never makes the verifier load this class. */
    static JewelersBenchBlockEntity make(BlockPos pos, BlockState state) {
        return new HostedBenchBlockEntity(pos, state);
    }

    /**
     * The library's window on one of the bench's arrays. A write is the library's own transfer, already allowed by
     * {@code WorkstationLogic}: it lands in the array, raises the position's generation and marks the bench changed (the
     * library sends the update itself, so no second one is sent from here).
     */
    private final class Storage implements SlotStorage {
        private final net.minecraft.world.item.ItemStack[] stacks;
        private final int gen;

        Storage(net.minecraft.world.item.ItemStack[] stacks, int gen) {
            this.stacks = stacks;
            this.gen = gen;
        }

        @Override
        public int size() {
            return stacks.length;
        }

        @Override
        public net.minecraft.world.item.ItemStack get(int index) {
            return index >= 0 && index < stacks.length ? stacks[index] : net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public void set(int index, net.minecraft.world.item.ItemStack stack) {
            if (index < 0 || index >= stacks.length) return;
            stacks[index] = stack.isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY : stack;
            generations[gen + index]++;
            setChanged();
        }
    }

    @Override
    public StationState stationState() {
        return station;
    }

    /** The pose goes with its stack, and what stood on it comes down onto what it stood on. */
    @Override
    protected void stagedReplaced(int index) {
        SlotRef gone = SlotRef.of(JewelryStations.STAGING, index);
        ItemPose pose = station.getPose(gone);
        station.setPose(gone, null);
        if (pose == null) return;
        for (int i = 0; i < STAGING; i++) {
            SlotRef above = SlotRef.of(JewelryStations.STAGING, i);
            ItemPose standing = station.getPose(above);
            if (i != index && standing != null && standing.on() == index) {
                station.setPose(above, standing.raised(pose.y() - standing.y()).standingOn(pose.on()));
            }
        }
    }

    /**
     * Reads the library's part of the state (after the arrays, which it does not read). A pose that cannot be repaired was
     * already dropped by {@link ItemPose#load}; a pose of an empty position is forgotten here.
     */
    @Override
    protected void loadStation(CompoundTag tag) {
        station.load(tag);
        for (int i = 0; i < STAGING; i++) {
            if (staged(i).isEmpty()) station.setPose(SlotRef.of(JewelryStations.STAGING, i), null);
        }
    }

    @Override
    protected CompoundTag saveStation() {
        return station.save();
    }
}
