package com.otectus.immersivejewelry.blockentity;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.UnfinishedJewelryItem;
import com.otectus.immersivejewelry.registry.ModBlockEntities;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Jeweler's Bench's canonical contents. Every item here is a real stack the bench owns:
 * <ul>
 *   <li>{@code clamp}: the frame being worked (a blank, a finished piece being reworked, or the finished result resting on
 *   the pad until it is collected);</li>
 *   <li>{@code sockets}: stones seated in the frame's cups (resting while planning, committed once the first closure was
 *   made);</li>
 *   <li>{@code tray}: loose cut stones staged for the piece, never committed;</li>
 *   <li>{@code supplies}: consumables staged for finishing or repair, consumed only at their first effective use;</li>
 *   <li>{@code dock}: real pliers handed to the bench.</li>
 * </ul>
 * The {@link BenchWork} is the state of the work on them. Breaking the bench drops progressed work as one
 * unfinished-jewelry carrier (frame, committed stones and work together), plus the uncommitted tray, supplies and
 * tools: every item exactly once.
 *
 * <p>Saved format 2. A bench saved by 1.1 (format 1: {@code Workpiece}, {@code Escrow}, {@code DisplayStone},
 * {@code Progress}) was interrupted in an old minigame session: its workpiece goes to the clamp and its escrow (the real
 * stones and finish materials) is recovered once into the tray and supplies, with any overflow dropped at the bench.
 * {@code DisplayStone} was only a picture of an escrowed stone and is ignored.</p>
 */
public class JewelersBenchBlockEntity extends StationBlockEntity {
    public static final int FORMAT = 2;
    public static final int SOCKETS = 8, TRAY = 8, SUPPLIES = 3;

    private ItemStack clamp = ItemStack.EMPTY;
    private final ItemStack[] sockets = filled(SOCKETS);
    private final ItemStack[] tray = filled(TRAY);
    private final ItemStack[] supplies = filled(SUPPLIES);
    private ItemStack dock = ItemStack.EMPTY;
    @Nullable
    private BenchWork work;
    /** Work data this version could not read (a newer schema): kept untouched so no progress is lost. */
    @Nullable
    private CompoundTag unreadableWork;
    /** Stacks recovered from a 1.1 escrow that found no room; dropped at the bench on the first server tick. */
    private final List<ItemStack> overflow = new ArrayList<>();

    public JewelersBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.JEWELERS_BENCH.get(), pos, state);
    }

    private static ItemStack[] filled(int n) {
        ItemStack[] out = new ItemStack[n];
        Arrays.fill(out, ItemStack.EMPTY);
        return out;
    }

    // ------------------------------------------------------------------------------------------------ contents

    public ItemStack clamp() { return clamp; }
    public ItemStack socket(int i) { return i >= 0 && i < SOCKETS ? sockets[i] : ItemStack.EMPTY; }
    public ItemStack tray(int i) { return i >= 0 && i < TRAY ? tray[i] : ItemStack.EMPTY; }
    public ItemStack supply(int i) { return i >= 0 && i < SUPPLIES ? supplies[i] : ItemStack.EMPTY; }
    public ItemStack dock() { return dock; }
    @Nullable public BenchWork work() { return work; }
    public boolean hasUnreadableWork() { return unreadableWork != null; }

    public void setClamp(ItemStack stack) {
        clamp = one(stack);
        sync();
    }

    public void setSocket(int i, ItemStack stack) {
        sockets[i] = one(stack);
        sync();
    }

    public void setTray(int i, ItemStack stack) {
        tray[i] = stack.isEmpty() ? ItemStack.EMPTY : stack;
        sync();
    }

    public void setSupply(int i, ItemStack stack) {
        supplies[i] = stack.isEmpty() ? ItemStack.EMPTY : stack;
        sync();
    }

    public void setDock(ItemStack stack) {
        dock = one(stack);
        sync();
    }

    public void setWork(@Nullable BenchWork value) {
        work = value;
        unreadableWork = null;
        sync();
    }

    /** The work changed by input: send it with the next throttled update. */
    public void workChanged() {
        syncSoon();
    }

    public void changed() {
        sync();
    }

    private static ItemStack one(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    public int firstEmptyTray() {
        for (int i = 0; i < TRAY; i++) if (tray[i].isEmpty()) return i;
        return -1;
    }

    public int seatedCount() {
        int n = 0;
        for (ItemStack s : sockets) if (!s.isEmpty()) n++;
        return n;
    }

    /** Nothing at all is on the bench. */
    public boolean isEmpty() {
        if (!clamp.isEmpty() || !dock.isEmpty() || work != null || unreadableWork != null) return false;
        for (ItemStack s : sockets) if (!s.isEmpty()) return false;
        for (ItemStack s : tray) if (!s.isEmpty()) return false;
        for (ItemStack s : supplies) if (!s.isEmpty()) return false;
        return true;
    }

    /** Whether the frame and seated stones belong to a work order now (they leave only as a carrier). */
    public boolean committed() {
        return unreadableWork != null || (work != null && work.irreversible());
    }

    // ------------------------------------------------------------------------------------------------ packing

    /**
     * Packs committed work into one unfinished-jewelry carrier and empties the clamp and sockets: the single ownership
     * transition from bench to item. Returns empty when there is nothing committed to pack.
     */
    public ItemStack pack() {
        if (!committed()) return ItemStack.EMPTY;
        List<ItemStack> stones = new ArrayList<>();
        for (ItemStack s : sockets) stones.add(s.copy());
        CompoundTag workTag = work != null ? work.save() : unreadableWork.copy();
        ItemStack carrier = UnfinishedJewelryItem.create(clamp.copy(), stones, workTag);
        clamp = ItemStack.EMPTY;
        Arrays.fill(sockets, ItemStack.EMPTY);
        work = null;
        unreadableWork = null;
        sync();
        return carrier;
    }

    /** Restores a carrier's contents into an empty clamp and empty sockets. The caller consumes the carrier. */
    public boolean unpack(ItemStack carrier) {
        if (!clamp.isEmpty() || work != null || unreadableWork != null || seatedCount() > 0) return false;
        UnfinishedJewelryItem.Contents contents = UnfinishedJewelryItem.contents(carrier).orElse(null);
        if (contents == null) return false;
        clamp = contents.frame().copy();
        for (int i = 0; i < SOCKETS; i++) sockets[i] = i < contents.stones().size() ? contents.stones().get(i).copy() : ItemStack.EMPTY;
        BenchWork loaded = BenchWork.load(contents.work()).orElse(null);
        work = loaded;
        unreadableWork = loaded == null ? contents.work().copy() : null;
        sync();
        return true;
    }

    /** The one removal path: committed work as one carrier, everything else as itself. Every item drops exactly once. */
    public void dropContents() {
        if (level == null) return;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.0, z = worldPosition.getZ() + 0.5;
        if (committed()) {
            Containers.dropItemStack(level, x, y, z, pack());
        } else {
            Containers.dropItemStack(level, x, y, z, clamp);
            for (int i = 0; i < SOCKETS; i++) {
                Containers.dropItemStack(level, x, y, z, sockets[i]);
                sockets[i] = ItemStack.EMPTY;
            }
            clamp = ItemStack.EMPTY;
            work = null;
        }
        for (int i = 0; i < TRAY; i++) {
            Containers.dropItemStack(level, x, y, z, tray[i]);
            tray[i] = ItemStack.EMPTY;
        }
        for (int i = 0; i < SUPPLIES; i++) {
            Containers.dropItemStack(level, x, y, z, supplies[i]);
            supplies[i] = ItemStack.EMPTY;
        }
        Containers.dropItemStack(level, x, y, z, dock);
        dock = ItemStack.EMPTY;
        for (ItemStack s : overflow) Containers.dropItemStack(level, x, y, z, s);
        overflow.clear();
    }

    // ------------------------------------------------------------------------------------------------ ticking and saving

    public static void serverTick(Level level, BlockPos pos, BlockState state, JewelersBenchBlockEntity bench) {
        if (!bench.overflow.isEmpty()) {
            for (ItemStack s : bench.overflow) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, s);
            bench.overflow.clear();
            bench.setChanged();
        }
        bench.flushSync();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) WorkSessionManager.onStationUnloaded(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Format", FORMAT);
        if (!clamp.isEmpty()) tag.put("Clamp", clamp.save(new CompoundTag()));
        tag.put("Sockets", saveList(sockets));
        tag.put("Tray", saveList(tray));
        tag.put("Supplies", saveList(supplies));
        if (!dock.isEmpty()) tag.put("Dock", dock.save(new CompoundTag()));
        if (work != null) tag.put("Work", work.save());
        else if (unreadableWork != null) tag.put("Work", unreadableWork.copy());
        if (!overflow.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack s : overflow) list.add(s.save(new CompoundTag()));
            tag.put("Overflow", list);
        }
    }

    private static ListTag saveList(ItemStack[] stacks) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i].isEmpty()) continue;
            CompoundTag entry = stacks[i].save(new CompoundTag());
            entry.putByte("Index", (byte) i);
            list.add(entry);
        }
        return list;
    }

    private static void loadList(ListTag list, ItemStack[] into) {
        Arrays.fill(into, ItemStack.EMPTY);
        for (Tag t : list) {
            CompoundTag entry = (CompoundTag) t;
            int i = entry.getByte("Index");
            if (i >= 0 && i < into.length) into[i] = ItemStack.of(entry);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.getInt("Format") < FORMAT && !tag.contains("Clamp")) {
            loadLegacy(tag);
            return;
        }
        clamp = tag.contains("Clamp", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Clamp")) : ItemStack.EMPTY;
        loadList(tag.getList("Sockets", Tag.TAG_COMPOUND), sockets);
        loadList(tag.getList("Tray", Tag.TAG_COMPOUND), tray);
        loadList(tag.getList("Supplies", Tag.TAG_COMPOUND), supplies);
        dock = tag.contains("Dock", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Dock")) : ItemStack.EMPTY;
        work = null;
        unreadableWork = null;
        if (tag.contains("Work", Tag.TAG_COMPOUND)) {
            CompoundTag raw = tag.getCompound("Work");
            work = BenchWork.load(raw).orElse(null);
            if (work == null) {
                unreadableWork = raw.copy();
                ImmersiveJewelry.LOGGER.warn("Jeweler's Bench at {} holds work this version cannot read; it is kept unchanged", worldPosition);
            }
        }
        overflow.clear();
        for (Tag t : tag.getList("Overflow", Tag.TAG_COMPOUND)) {
            ItemStack s = ItemStack.of((CompoundTag) t);
            if (!s.isEmpty()) overflow.add(s);
        }
    }

    /**
     * A bench from 1.1: the workpiece goes into the clamp; escrowed stones and materials (an interrupted minigame) are
     * recovered once into the tray and supplies. Loading the same old data again gives the same result, and nothing is
     * written back in the old format, so the recovery happens at most once.
     */
    private void loadLegacy(CompoundTag tag) {
        clamp = tag.contains("Workpiece", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Workpiece")) : ItemStack.EMPTY;
        Arrays.fill(sockets, ItemStack.EMPTY);
        Arrays.fill(tray, ItemStack.EMPTY);
        Arrays.fill(supplies, ItemStack.EMPTY);
        dock = ItemStack.EMPTY;
        work = null;
        unreadableWork = null;
        overflow.clear();
        for (Tag t : tag.getList("Escrow", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) t);
            if (stack.isEmpty()) continue;
            if (!recover(stack)) overflow.add(stack);
        }
        if (tag.contains("Escrow")) {
            ImmersiveJewelry.LOGGER.info("Recovered the escrow of an interrupted 1.1 setting session at the bench at {}", worldPosition);
        }
    }

    private boolean recover(ItemStack stack) {
        ItemStack[] into = stack.getItem() instanceof CutGemItem ? tray : supplies;
        for (int i = 0; i < into.length; i++) {
            if (into[i].isEmpty()) {
                into[i] = stack;
                return true;
            }
            if (ItemStack.isSameItemSameTags(into[i], stack) && into[i].getCount() + stack.getCount() <= into[i].getMaxStackSize()) {
                into[i].grow(stack.getCount());
                return true;
            }
        }
        return false;
    }
}
