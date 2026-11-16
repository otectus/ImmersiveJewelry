package com.otectus.immersivejewelry.workstation.order;

import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.workstation.sim.FacetParams;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A cut planned or under way on one stone. It travels with the stone: the {@code Work} compound inside the stone's
 * {@code immersive_jewelry} data holds the plan, the parameters as resolved when it was planned and the simulation state,
 * so a stone taken off the wheel, carried away, dropped or traded is resumed exactly where it was, with every mistake
 * still in it. Only finishing the cut replaces the stone (with a cut stone without work).
 *
 * <p>Schema version 1. Fields: {@code Schema}, {@code Sim} (the simulation version that wrote the state), {@code Id}, {@code
 * Cut}, {@code Recut}, {@code Seed}, {@code Params}, {@code State}, {@code Revision}, {@code LapWorn} (the lap paid its one
 * point of wear for this cut), {@code Finalized}.</p>
 */
public final class CutWork {
    public static final String KEY = "Work";
    public static final int SCHEMA = 1;

    private final long id;
    private final ResourceLocation cut;
    private final boolean recut;
    private final long seed;
    private final FacetParams params;
    private FacetSim sim;
    private int revision;
    private boolean lapWorn;

    public CutWork(long id, ResourceLocation cut, boolean recut, long seed, FacetParams params) {
        this(id, cut, recut, seed, params, new FacetSim(params), 0, false);
    }

    private CutWork(long id, ResourceLocation cut, boolean recut, long seed, FacetParams params, FacetSim sim, int revision, boolean lapWorn) {
        this.id = id;
        this.cut = cut;
        this.recut = recut;
        this.seed = seed;
        this.params = params;
        this.sim = sim;
        this.revision = revision;
        this.lapWorn = lapWorn;
    }

    public long id() { return id; }
    public ResourceLocation cut() { return cut; }
    public boolean recut() { return recut; }
    public long seed() { return seed; }
    public FacetParams params() { return params; }
    public FacetSim sim() { return sim; }
    public int revision() { return revision; }
    public boolean lapWorn() { return lapWorn; }

    /** Whether material has been removed: from then on changing the plan is a recut, never free. */
    public boolean started() {
        return sim.touched();
    }

    public void markLapWorn() {
        lapWorn = true;
    }

    public void bump() {
        revision++;
    }

    /** Replaces the simulation state (client prediction rebasing on an authoritative snapshot). */
    public void restore(FacetSim state) {
        sim = state;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Schema", SCHEMA);
        tag.putInt("Sim", FacetSim.VERSION);
        tag.putLong("Id", id);
        tag.putString("Cut", cut.toString());
        if (recut) tag.putBoolean("Recut", true);
        tag.putLong("Seed", seed);
        tag.put("Params", params.save());
        tag.put("State", sim.save());
        tag.putInt("Revision", revision);
        if (lapWorn) tag.putBoolean("LapWorn", true);
        return tag;
    }

    /** The work in {@code tag}; empty when it is missing, from a newer schema, or unreadable. */
    public static Optional<CutWork> load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("Cut", Tag.TAG_STRING) || tag.getInt("Schema") > SCHEMA || tag.getInt("Schema") < 1) {
            return Optional.empty();
        }
        ResourceLocation cut = ResourceLocation.tryParse(tag.getString("Cut"));
        if (cut == null) return Optional.empty();
        FacetParams params = FacetParams.load(tag.getCompound("Params"));
        FacetSim sim = FacetSim.load(params, tag.getCompound("State"));
        return Optional.of(new CutWork(tag.getLong("Id"), cut, tag.getBoolean("Recut"), tag.getLong("Seed"), params, sim,
                Math.max(0, tag.getInt("Revision")), tag.getBoolean("LapWorn")));
    }

    /** The cut work carried by a rough or cut stone. */
    public static Optional<CutWork> of(ItemStack stone) {
        CompoundTag root = JewelryNbt.root(stone);
        return root != null && root.contains(KEY, Tag.TAG_COMPOUND) ? load(root.getCompound(KEY)) : Optional.empty();
    }

    /** Whether the stone carries a work compound at all (also one this version cannot read). */
    public static boolean present(ItemStack stone) {
        CompoundTag root = JewelryNbt.root(stone);
        return root != null && root.contains(KEY, Tag.TAG_COMPOUND);
    }

    /**
     * Writes the work into the stone's own data, in place: the gem data next to it is left exactly as it is. Only
     * {@link #clear} removes it.
     */
    public void writeTo(ItemStack stone) {
        CompoundTag root = JewelryNbt.root(stone);
        if (root == null) return;
        root.put(KEY, save());
        stone.getOrCreateTag().put(JewelryNbt.ROOT, root);
    }

    public static void clear(ItemStack stone) {
        CompoundTag root = JewelryNbt.root(stone);
        if (root != null && root.contains(KEY)) {
            root.remove(KEY);
            stone.getOrCreateTag().put(JewelryNbt.ROOT, root);
        }
    }
}
