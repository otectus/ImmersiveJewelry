package com.otectus.immersivejewelry.workstation.order;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.workstation.sim.SettingParams;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The durable state of one piece of work at the Jeweler's Bench: assembling a new piece, or retightening, taking apart,
 * repairing or refinishing a finished one. It lives in the bench (or, packed, in an unfinished-jewelry carrier) next to
 * the real stacks it works on: the frame in the clamp and the stones in the sockets. An operator's lease is only
 * permission to work on it; leaving the bench ends the lease, never the work.
 *
 * <p>Stages of an assembly: {@link Stage#PLAN} (design, stones and finish chosen, nothing committed: everything can
 * still go back), {@link Stage#SETTING} (the first closure committed the frame and stones; from here the stones belong to
 * the work), {@link Stage#FINISHING} (every stone secured; the chosen finish is applied by hand). Other kinds start in
 * {@code SETTING} (retighten, remove) or {@code FINISHING} (repair, refinish).</p>
 *
 * <p>Schema version 1. Fields: {@code Schema}, {@code Id}, {@code Kind}, {@code Stage}, {@code Design}, {@code Finish},
 * {@code Seed}, {@code Revision}, {@code Committed}, {@code Setting} ({@code Params}, {@code State}), {@code Surface}
 * ({@code Params}, {@code State}), {@code FinishPaid}, {@code RepairPaid}, {@code PliersCharged}, {@code PriorSetting},
 * {@code Baseline}, {@code Lifted}, {@code Original} (removal: the stones as they were set), {@code Finalized}.</p>
 */
public final class BenchWork {
    public static final int SCHEMA = 1;

    public enum Kind {
        ASSEMBLE, RETIGHTEN, REMOVE, REPAIR, REFINISH;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Stage { PLAN, SETTING, FINISHING }

    private final long id;
    private final Kind kind;
    private final long seed;
    private Stage stage;
    @Nullable
    private ResourceLocation design;
    @Nullable
    private ResourceLocation finish;
    private int revision;
    private boolean committed;
    @Nullable
    private SettingSim setting;
    @Nullable
    private SurfaceSim surface;
    private boolean finishPaid;
    private boolean repairPaid;
    private boolean pliersCharged;
    private float priorSetting;
    private float baseline;
    private int lifted;
    private final List<GemData> original = new ArrayList<>();
    private boolean finalized;

    public BenchWork(long id, Kind kind, long seed) {
        this.id = id;
        this.kind = kind;
        this.seed = seed;
        this.stage = switch (kind) {
            case ASSEMBLE -> Stage.PLAN;
            case RETIGHTEN, REMOVE -> Stage.SETTING;
            case REPAIR, REFINISH -> Stage.FINISHING;
        };
    }

    public long id() { return id; }
    public Kind kind() { return kind; }
    public long seed() { return seed; }
    public Stage stage() { return stage; }
    @Nullable public ResourceLocation design() { return design; }
    @Nullable public ResourceLocation finish() { return finish; }
    public int revision() { return revision; }
    public boolean committed() { return committed; }
    @Nullable public SettingSim setting() { return setting; }
    @Nullable public SurfaceSim surface() { return surface; }
    public boolean finishPaid() { return finishPaid; }
    public boolean repairPaid() { return repairPaid; }
    public boolean pliersCharged() { return pliersCharged; }
    public float priorSetting() { return priorSetting; }
    public float baseline() { return baseline; }
    public boolean finalized() { return finalized; }
    public List<GemData> original() { return List.copyOf(original); }

    public boolean lifted(int socket) {
        return (lifted & (1 << socket)) != 0;
    }

    public int liftedCount() {
        return Integer.bitCount(lifted);
    }

    public void setDesign(@Nullable ResourceLocation value) { design = value; }
    public void setFinish(@Nullable ResourceLocation value) { finish = value; }
    public void setStage(Stage value) { stage = value; }
    public void commit() { committed = true; }
    public void setSetting(@Nullable SettingSim value) { setting = value; }
    public void setSurface(@Nullable SurfaceSim value) { surface = value; }
    public void markFinishPaid() { finishPaid = true; }
    public void markRepairPaid() { repairPaid = true; }
    public void markPliersCharged() { pliersCharged = true; }
    public void markLifted(int socket) { lifted |= 1 << socket; }
    public void markFinalized() { finalized = true; }

    public void setRetightenBaseline(float prior, float base) {
        priorSetting = prior;
        baseline = base;
    }

    public void setOriginal(List<GemData> gems) {
        original.clear();
        original.addAll(gems.subList(0, Math.min(gems.size(), SettingParams.MAX_SOCKETS)));
    }

    public void bump() {
        revision++;
    }

    /** Whether anything irreversible happened: the work can no longer simply be put back the way it was. */
    public boolean irreversible() {
        return committed || finishPaid || repairPaid || (surface != null && surface.started()) || lifted != 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Schema", SCHEMA);
        tag.putLong("Id", id);
        tag.putString("Kind", kind.name());
        tag.putString("Stage", stage.name());
        tag.putLong("Seed", seed);
        if (design != null) tag.putString("Design", design.toString());
        if (finish != null) tag.putString("Finish", finish.toString());
        tag.putInt("Revision", revision);
        if (committed) tag.putBoolean("Committed", true);
        if (setting != null) {
            CompoundTag s = new CompoundTag();
            s.put("Params", setting.params().save());
            s.put("State", setting.save());
            tag.put("Setting", s);
        }
        if (surface != null) {
            CompoundTag s = new CompoundTag();
            s.put("Params", surface.params().save());
            s.put("State", surface.save());
            tag.put("Surface", s);
        }
        if (finishPaid) tag.putBoolean("FinishPaid", true);
        if (repairPaid) tag.putBoolean("RepairPaid", true);
        if (pliersCharged) tag.putBoolean("PliersCharged", true);
        if (kind == Kind.RETIGHTEN) {
            tag.putFloat("PriorSetting", priorSetting);
            tag.putFloat("Baseline", baseline);
        }
        if (lifted != 0) tag.putInt("Lifted", lifted);
        if (!original.isEmpty()) {
            ListTag list = new ListTag();
            for (GemData g : original) list.add(g.save());
            tag.put("Original", list);
        }
        if (finalized) tag.putBoolean("Finalized", true);
        return tag;
    }

    /** The work in {@code tag}; empty when it is missing, unreadable or from a newer schema (the caller keeps the raw data). */
    public static Optional<BenchWork> load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("Kind", Tag.TAG_STRING)) return Optional.empty();
        int schema = tag.getInt("Schema");
        if (schema < 1 || schema > SCHEMA) return Optional.empty();
        Kind kind;
        Stage stage;
        try {
            kind = Kind.valueOf(tag.getString("Kind"));
            stage = Stage.valueOf(tag.getString("Stage"));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        BenchWork work = new BenchWork(tag.getLong("Id"), kind, tag.getLong("Seed"));
        work.stage = stage;
        work.design = tag.contains("Design", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("Design")) : null;
        work.finish = tag.contains("Finish", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("Finish")) : null;
        work.revision = Math.max(0, tag.getInt("Revision"));
        work.committed = tag.getBoolean("Committed");
        if (tag.contains("Setting", Tag.TAG_COMPOUND)) {
            CompoundTag s = tag.getCompound("Setting");
            work.setting = SettingSim.load(SettingParams.load(s.getCompound("Params")), s.getCompound("State"));
        }
        if (tag.contains("Surface", Tag.TAG_COMPOUND)) {
            CompoundTag s = tag.getCompound("Surface");
            work.surface = SurfaceSim.load(SurfaceParams.load(s.getCompound("Params")), s.getCompound("State"));
        }
        work.finishPaid = tag.getBoolean("FinishPaid");
        work.repairPaid = tag.getBoolean("RepairPaid");
        work.pliersCharged = tag.getBoolean("PliersCharged");
        work.priorSetting = tag.getFloat("PriorSetting");
        work.baseline = tag.getFloat("Baseline");
        work.lifted = tag.getInt("Lifted") & 0xFF;
        for (Tag t : tag.getList("Original", Tag.TAG_COMPOUND)) {
            if (work.original.size() >= SettingParams.MAX_SOCKETS) break;
            GemData.load((CompoundTag) t).ifPresent(work.original::add);
        }
        work.finalized = tag.getBoolean("Finalized");
        return Optional.of(work);
    }

    public BenchWork copy() {
        return load(save()).orElseThrow();
    }
}
