package com.otectus.immersivejewelry.workstation;

import com.otectus.immersivejewelry.advancement.ModAdvancements;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.event.JewelryCraftedEvent;
import com.otectus.immersivejewelry.api.event.JewelryReworkedEvent;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.integration.IJewelryAssistSource;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.item.PliersItem;
import com.otectus.immersivejewelry.item.UnfinishedJewelryItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.migration.LegacyJewelcraft;
import com.otectus.immersivejewelry.quality.Workmanship;
import com.otectus.immersivejewelry.quality.WorkmanshipGrade;
import com.otectus.immersivejewelry.recipe.DesignKnowledge;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModSounds;
import com.otectus.immersivejewelry.util.Feedback;
import com.otectus.immersivejewelry.util.Inventories;
import com.otectus.immersivejewelry.util.StationEffects;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.otectus.immersivejewelry.workstation.sim.SettingParams;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Everything that changes a Jeweler's Bench, on the server: walk-up use, every custody move of a real stack, the plan,
 * seating, the commit at the first irreversible closure, setting and finishing input, completion, and the four reworks
 * of a finished piece. Each transition validates first and then applies all of its changes together; a refused one
 * changes nothing.
 */
public final class BenchOperations {
    public static final String CLAMP = "clamp", TRAY = "tray", SUPPLY = "supply", DOCK = "dock";

    // ================================================================================================ walk-up use

    /** Using the bench without working at it: frames, carriers, stones, supplies and pliers in hand; sneak to take. */
    public static void use(ServerPlayer player, JewelersBenchBlockEntity bench, InteractionHand hand) {
        if (WorkSessionManager.heldByOther(player, bench.getBlockPos())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && held.isEmpty()) {
            takeAny(player, bench);
            return;
        }
        if (!held.isEmpty() && place(player, bench, held, null, -1, true)) return;
        Feedback.info(player, bench.clamp().isEmpty() ? "bench_hint" : "bench_work_hint");
    }

    /** Whether a stack is something walking up to the bench puts on it (so using the bench with it does not open the scene). */
    public static boolean isWalkUpItem(ItemStack held) {
        if (held.isEmpty()) return false;
        return held.getItem() instanceof JewelryBlankItem || held.getItem() instanceof JewelryItem || held.getItem() instanceof UnfinishedJewelryItem
                || held.getItem() instanceof PliersItem || held.getItem() instanceof CutGemItem || isSupply(held, null);
    }

    /** Whether the bench has any use for a stack: what {@link #place} takes somewhere, the clamped piece's repair metal included. */
    public static boolean isWorkItem(ItemStack stack, @Nullable JewelersBenchBlockEntity bench) {
        return isWalkUpItem(stack) || isSupply(stack, bench);
    }

    /**
     * Whether {@link #place} would take {@code stack} at {@code target} position {@code index} now, changing nothing: the
     * same rules in the same order. The one refusal only the real move finds is a carrier whose packed work cannot be read.
     * The scene shows the answer under a carried stack.
     */
    public static boolean accepts(JewelersBenchBlockEntity bench, ItemStack stack, String target, int index) {
        if (stack.isEmpty()) return false;
        return switch (target) {
            case CLAMP -> (stack.getItem() instanceof UnfinishedJewelryItem || stack.getItem() instanceof JewelryBlankItem
                    || stack.getItem() instanceof JewelryItem) && bench.clamp().isEmpty() && bench.work() == null && bench.seatedCount() == 0;
            case DOCK -> stack.getItem() instanceof PliersItem && bench.dock().isEmpty();
            case TRAY -> stack.getItem() instanceof CutGemItem && fits(bench, TRAY, stack, index, JewelersBenchBlockEntity.TRAY);
            case SUPPLY -> isSupply(stack, bench) && fits(bench, SUPPLY, stack, index, JewelersBenchBlockEntity.SUPPLIES);
            default -> false;
        };
    }

    /** {@link #insert}'s rule: the position (any, when it is out of range) is empty or holds the same stack with room. */
    private static boolean fits(JewelersBenchBlockEntity bench, String group, ItemStack source, int index, int size) {
        int start = index >= 0 && index < size ? index : 0;
        int end = index >= 0 && index < size ? index + 1 : size;
        for (int i = start; i < end; i++) {
            ItemStack at = TRAY.equals(group) ? bench.tray(i) : bench.supply(i);
            if (at.isEmpty() || ItemStack.isSameItemSameTags(at, source) && at.getCount() < at.getMaxStackSize()) return true;
        }
        return false;
    }

    /** Sneak-use with an empty hand: the finished piece first, then an idle frame, packed work, stones, supplies, pliers. */
    private static void takeAny(ServerPlayer player, JewelersBenchBlockEntity bench) {
        BenchWork work = bench.work();
        if (!bench.clamp().isEmpty() && work == null) {
            take(player, bench, CLAMP, 0);
            return;
        }
        if (bench.committed()) {
            pack(player, bench);
            return;
        }
        if (!bench.clamp().isEmpty()) {
            take(player, bench, CLAMP, 0);
            return;
        }
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) {
            if (!bench.tray(i).isEmpty()) {
                take(player, bench, TRAY, i);
                return;
            }
        }
        for (int i = 0; i < JewelersBenchBlockEntity.SUPPLIES; i++) {
            if (!bench.supply(i).isEmpty()) {
                take(player, bench, SUPPLY, i);
                return;
            }
        }
        if (!bench.dock().isEmpty()) take(player, bench, DOCK, 0);
    }

    // ================================================================================================ custody

    /**
     * Puts (part of) a real stack on the bench. {@code target} null: wherever the item belongs. The stack is
     * {@code source} (the hand, the cursor or an inventory slot, modified in place); a refused placement leaves it
     * untouched and says why.
     */
    public static boolean place(ServerPlayer player, JewelersBenchBlockEntity bench, ItemStack source, @Nullable String target, int index,
                                boolean quietIfNotAccepted) {
        if (source.isEmpty()) return false;
        boolean creative = player.getAbilities().instabuild;
        if (source.getItem() instanceof UnfinishedJewelryItem && (target == null || CLAMP.equals(target))) {
            if (!bench.clamp().isEmpty() || bench.work() != null || bench.seatedCount() > 0) return refuse(player, "clamp_occupied", quietIfNotAccepted);
            if (!bench.unpack(source)) return refuse(player, "carrier_unreadable", quietIfNotAccepted);
            if (!creative) source.shrink(1);
            sound(player, bench, ModSounds.BENCH_PLACE.get(), 0.8F, 0.9F);
            Feedback.info(player, "unpacked");
            return true;
        }
        if ((source.getItem() instanceof JewelryBlankItem || source.getItem() instanceof JewelryItem) && (target == null || CLAMP.equals(target))) {
            if (!bench.clamp().isEmpty() || bench.work() != null || bench.seatedCount() > 0) return refuse(player, "clamp_occupied", quietIfNotAccepted);
            bench.setClamp(creative ? source.copyWithCount(1) : source.split(1));
            sound(player, bench, ModSounds.BENCH_PLACE.get(), 0.8F, 1.0F);
            Feedback.info(player, bench.clamp().getItem() instanceof JewelryItem ? "bench_rework_hint" : "bench_blank_hint");
            return true;
        }
        if (source.getItem() instanceof PliersItem && (target == null || DOCK.equals(target))) {
            if (!bench.dock().isEmpty()) return refuse(player, "dock_occupied", quietIfNotAccepted);
            bench.setDock(creative ? source.copyWithCount(1) : source.split(1));
            sound(player, bench, ModSounds.BENCH_PLACE.get(), 0.6F, 1.4F);
            return true;
        }
        if (source.getItem() instanceof CutGemItem && (target == null || TRAY.equals(target))) {
            return insert(player, bench, source, TRAY, index, JewelersBenchBlockEntity.TRAY, "tray_full", quietIfNotAccepted, creative);
        }
        if (isSupply(source, bench) && (target == null || SUPPLY.equals(target))) {
            return insert(player, bench, source, SUPPLY, index, JewelersBenchBlockEntity.SUPPLIES, "supplies_full", quietIfNotAccepted, creative);
        }
        return refuse(player, "bench_cannot_take", quietIfNotAccepted || target == null);
    }

    private static boolean insert(ServerPlayer player, JewelersBenchBlockEntity bench, ItemStack source, String group, int index, int size,
                                  String fullKey, boolean quiet, boolean creative) {
        int start = index >= 0 && index < size ? index : 0;
        int end = index >= 0 && index < size ? index + 1 : size;
        // Merge first, then the first empty position.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = start; i < end; i++) {
                ItemStack at = TRAY.equals(group) ? bench.tray(i) : bench.supply(i);
                boolean ok = pass == 0 ? !at.isEmpty() && ItemStack.isSameItemSameTags(at, source) && at.getCount() < at.getMaxStackSize()
                        : at.isEmpty();
                if (!ok) continue;
                int room = at.isEmpty() ? source.getMaxStackSize() : at.getMaxStackSize() - at.getCount();
                int moved = Math.min(room, source.getCount());
                ItemStack next = at.isEmpty() ? source.copyWithCount(moved) : at.copyWithCount(at.getCount() + moved);
                if (TRAY.equals(group)) bench.setTray(i, next);
                else bench.setSupply(i, next);
                if (!creative) source.shrink(moved);
                sound(player, bench, ModSounds.BENCH_PLACE.get(), 0.5F, 1.5F);
                return true;
            }
        }
        return refuse(player, fullKey, quiet);
    }

    /** Places from the cursor ({@code from} -1) or an inventory slot, at the operator's request. */
    public static void placeFrom(ServerPlayer player, JewelersBenchBlockEntity bench, int from, @Nullable String target, int index) {
        if (target != null && target.isEmpty()) target = null;
        ItemStack source;
        if (from < 0) {
            source = player.inventoryMenu.getCarried();
        } else if (from < player.getInventory().items.size()) {
            source = player.getInventory().items.get(from);
        } else {
            return;
        }
        if (source.isEmpty()) return;
        if (place(player, bench, source, target, index, false)) {
            if (from < 0) player.inventoryMenu.setCarried(source.isEmpty() ? ItemStack.EMPTY : source);
            player.inventoryMenu.broadcastChanges();
        }
    }

    /** Takes a real stack from the bench into the player's inventory. */
    public static void take(ServerPlayer player, JewelersBenchBlockEntity bench, String target, int index) {
        BenchWork work = bench.work();
        ItemStack out;
        switch (target) {
            case CLAMP -> {
                if (bench.clamp().isEmpty()) return;
                if (work != null && work.irreversible()) {
                    Feedback.fail(player, "work_committed_pack");
                    return;
                }
                if (work != null) cancelPlan(player, bench, true);
                out = bench.clamp();
                bench.setClamp(ItemStack.EMPTY);
            }
            case TRAY -> {
                out = bench.tray(index);
                if (out.isEmpty()) return;
                bench.setTray(index, ItemStack.EMPTY);
            }
            case SUPPLY -> {
                out = bench.supply(index);
                if (out.isEmpty()) return;
                bench.setSupply(index, ItemStack.EMPTY);
            }
            case DOCK -> {
                out = bench.dock();
                if (out.isEmpty()) return;
                if (work != null && work.setting() != null && work.setting().gripping()) {
                    Feedback.fail(player, "lift_first");
                    return;
                }
                bench.setDock(ItemStack.EMPTY);
            }
            default -> {
                return;
            }
        }
        Inventories.give(player, out);
        sound(player, bench, ModSounds.BENCH_PLACE.get(), 0.6F, 1.3F);
    }

    /** Items the bench accepts as supplies: a finish's cost, polishing compound, or metal for repairs. */
    public static boolean isSupply(ItemStack stack, @Nullable JewelersBenchBlockEntity bench) {
        if (stack.isEmpty()) return false;
        if (stack.is(ModItems.POLISHING_COMPOUND.get()) || stack.is(Items.GUNPOWDER)) return true;
        JewelryDefinitions defs = JewelryDefinitions.current();
        for (FinishDefinition f : defs.finishes()) {
            if (f.cost() != null && !Inventories.isEmpty(f.cost()) && f.cost().test(stack)) return true;
        }
        if (bench != null) {
            JewelryData piece = JewelryData.get(bench.clamp()).orElse(null);
            if (piece != null) {
                Ingredient repair = BenchPlanner.repairIngredient(defs, piece.metal());
                if (repair != null && repair.test(stack)) return true;
            }
        }
        return stack.is(ItemTags.create(new ResourceLocation("forge", "nuggets")));
    }

    // ================================================================================================ the plan

    /** Chooses the design for the blank in the clamp. Free (and reversible) until the first closure. */
    public static void selectDesign(ServerPlayer player, JewelersBenchBlockEntity bench, @Nullable ResourceLocation id) {
        ItemStack frame = bench.clamp();
        if (!(frame.getItem() instanceof JewelryBlankItem blankItem) || BlankData.get(frame).isEmpty()) {
            Feedback.fail(player, "need_blank");
            return;
        }
        BenchWork work = bench.work();
        if (work != null && (work.kind() != BenchWork.Kind.ASSEMBLE || work.irreversible())) {
            Feedback.fail(player, "work_committed_pack");
            return;
        }
        JewelryDesignRecipe design = BenchPlanner.design(player.level(), id);
        if (design == null || !design.form().equals(blankItem.form()) || !DesignKnowledge.knows(player, design)) {
            Feedback.fail(player, "design_unavailable");
            return;
        }
        if (work == null) work = new BenchWork(player.getRandom().nextLong(), BenchWork.Kind.ASSEMBLE, player.getRandom().nextLong());
        work.setDesign(design.getId());
        // Stones resting in sockets the new design does not have go back to the tray.
        for (int s = design.stones(); s < JewelersBenchBlockEntity.SOCKETS; s++) {
            if (!bench.socket(s).isEmpty()) returnToTray(player, bench, s);
        }
        SettingSim old = work.setting();
        SettingParams provisional = BenchPlanner.settingParams(JewelryDefinitions.server(), design, design.stones(), seatedStones(bench, design.stones()),
                BenchPlanner.pliersPrecision(bench.dock()), WorkSessionManager.preferences(player.getUUID()), 1F);
        SettingSim sim = new SettingSim(provisional, SettingSim.Mode.SET);
        if (old != null) {
            float[] seats = new float[provisional.sockets()];
            for (int s = 0; s < seats.length && s < old.params().sockets(); s++) seats[s] = old.seat(s);
            sim.restore(new float[provisional.sockets() * provisional.points()], seats);
        }
        work.setSetting(sim);
        if (work.finish() == null || !design.allowsFinish(work.finish())) work.setFinish(defaultFinish(design));
        work.bump();
        bench.setWork(work);
        sound(player, bench, ModSounds.SKETCH_LEARN.get(), 0.4F, 1.3F);
    }

    @Nullable
    private static ResourceLocation defaultFinish(JewelryDesignRecipe design) {
        for (FinishDefinition f : JewelryDefinitions.server().finishes()) {
            if (design.allowsFinish(f.id())) return f.id();
        }
        return null;
    }

    public static void selectFinish(ServerPlayer player, JewelersBenchBlockEntity bench, @Nullable ResourceLocation id) {
        BenchWork work = bench.work();
        FinishDefinition finish = JewelryDefinitions.server().finish(id);
        if (work == null || finish == null) return;
        boolean refinish = work.kind() == BenchWork.Kind.REFINISH;
        if (!(work.kind() == BenchWork.Kind.ASSEMBLE || refinish) || work.finishPaid() || (work.surface() != null && work.surface().started())) {
            Feedback.fail(player, "finish_locked");
            return;
        }
        JewelryDesignRecipe design = BenchPlanner.design(player.level(), work.design());
        if (design != null && !design.allowsFinish(finish.id())) {
            Feedback.fail(player, "finish_unavailable");
            return;
        }
        if (refinish && JewelryData.get(bench.clamp()).map(d -> finish.id().equals(d.finish())).orElse(false)) {
            Feedback.fail(player, "finish_same");
            return;
        }
        work.setFinish(finish.id());
        if (work.stage() == BenchWork.Stage.FINISHING) {
            work.setSurface(new SurfaceSim(BenchPlanner.surfaceParams(JewelryDefinitions.server(),
                    SurfaceParams.Mode.forFinish(finish.visualProfile()), finish, work.seed(), 0F, WorkSessionManager.preferences(player.getUUID()))));
        }
        work.bump();
        bench.setWork(work);
    }

    /** The tweezers let a tray stone go into a socket: it rests there, not yet held. */
    public static void seat(ServerPlayer player, JewelersBenchBlockEntity bench, int trayIndex, int socket, float dx, float dy, float turn) {
        BenchWork work = bench.work();
        if (work == null || work.kind() != BenchWork.Kind.ASSEMBLE || work.committed() || work.setting() == null) {
            Feedback.fail(player, work == null ? "choose_design" : "socket_held");
            return;
        }
        JewelryDesignRecipe design = BenchPlanner.design(player.level(), work.design());
        if (design == null || socket < 0 || socket >= design.stones() || socket >= work.setting().params().sockets()) return;
        if (!bench.socket(socket).isEmpty()) {
            Feedback.fail(player, "socket_taken");
            return;
        }
        ItemStack stone = bench.tray(trayIndex);
        GemData data = CutGemItem.data(stone).orElse(null);
        if (data == null) return;
        if (!design.accepts(data)) {
            Feedback.fail(player, "stone_not_accepted");
            return;
        }
        for (int s = 0; s < design.stones(); s++) {
            GemData other = CutGemItem.data(bench.socket(s)).orElse(null);
            if (other != null && !other.gem().equals(data.gem())) {
                Feedback.fail(player, "stones_mismatch");
                return;
            }
        }
        ItemStack one = stone.copyWithCount(1);
        ItemStack rest = stone.copyWithCount(stone.getCount() - 1);
        bench.setTray(trayIndex, rest.isEmpty() ? ItemStack.EMPTY : rest);
        bench.setSocket(socket, one);
        work.setting().seat(socket, dx, dy, turn);
        work.bump();
        bench.changed();
        sound(player, bench, ModSounds.BENCH_SEAT.get(), 0.6F, 1.25F);
    }

    /** Lifts a stone out: a resting one while planning, or (taking a piece apart) one whose points are open. */
    public static void lift(ServerPlayer player, JewelersBenchBlockEntity bench, int socket, int trayIndex) {
        BenchWork work = bench.work();
        if (work == null || work.setting() == null || socket < 0 || socket >= work.setting().params().sockets()) return;
        if (work.kind() == BenchWork.Kind.REMOVE) {
            liftForRemoval(player, bench, work, socket, trayIndex);
            return;
        }
        if (work.kind() != BenchWork.Kind.ASSEMBLE || work.committed() || bench.socket(socket).isEmpty()) {
            Feedback.fail(player, "socket_held");
            return;
        }
        int into = trayIndex >= 0 && trayIndex < JewelersBenchBlockEntity.TRAY && bench.tray(trayIndex).isEmpty() ? trayIndex : bench.firstEmptyTray();
        if (into < 0) {
            Feedback.fail(player, "tray_full");
            return;
        }
        bench.setTray(into, bench.socket(socket));
        bench.setSocket(socket, ItemStack.EMPTY);
        work.setting().unseat(socket);
        work.bump();
        bench.changed();
        sound(player, bench, ModSounds.BENCH_SEAT.get(), 0.5F, 1.5F);
    }

    /** Lets go of an uncommitted plan: resting stones go back to the tray (or the player when it is full). */
    public static void cancelPlan(ServerPlayer player, JewelersBenchBlockEntity bench, boolean quiet) {
        BenchWork work = bench.work();
        if (work == null) return;
        if (work.irreversible()) {
            if (!quiet) Feedback.fail(player, "work_committed_pack");
            return;
        }
        for (int s = 0; s < JewelersBenchBlockEntity.SOCKETS; s++) {
            if (!bench.socket(s).isEmpty()) returnToTray(player, bench, s);
        }
        bench.setWork(null);
    }

    private static void returnToTray(ServerPlayer player, JewelersBenchBlockEntity bench, int socket) {
        ItemStack stone = bench.socket(socket);
        int into = bench.firstEmptyTray();
        if (into >= 0) bench.setTray(into, stone);
        else Inventories.give(player, stone);
        bench.setSocket(socket, ItemStack.EMPTY);
    }

    private static List<GemData> seatedStones(JewelersBenchBlockEntity bench, int count) {
        List<GemData> out = new ArrayList<>();
        for (int s = 0; s < count; s++) CutGemItem.data(bench.socket(s)).ifPresent(out::add);
        return out;
    }

    // ================================================================================================ commit

    /**
     * The first irreversible operation of an assembly: everything is checked once more (design, knowledge, every socket
     * filled with an accepted stone of one gem, a finish, the tool, the craft gates) and the parameters are fixed. From
     * here the frame and the seated stones belong to the work.
     */
    static boolean commit(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work) {
        ItemStack frame = bench.clamp();
        BlankData blank = BlankData.get(frame).orElse(null);
        JewelryDesignRecipe design = BenchPlanner.design(player.level(), work.design());
        if (!(frame.getItem() instanceof JewelryBlankItem blankItem) || blank == null || design == null || !design.form().equals(blankItem.form())
                || !DesignKnowledge.knows(player, design)) {
            Feedback.fail(player, "design_unavailable");
            return false;
        }
        List<GemData> stones = new ArrayList<>();
        for (int s = 0; s < design.stones(); s++) {
            GemData g = CutGemItem.data(bench.socket(s)).orElse(null);
            if (g == null) {
                Feedback.fail(player, "design_needs_stones", design.stones());
                return false;
            }
            if (!design.accepts(g) || (!stones.isEmpty() && !stones.get(0).gem().equals(g.gem()))) {
                Feedback.fail(player, "stones_mismatch");
                return false;
            }
            stones.add(g);
        }
        FinishDefinition finish = JewelryDefinitions.server().finish(work.finish());
        if (finish == null || !design.allowsFinish(finish.id())) {
            Feedback.fail(player, "choose_finish");
            return false;
        }
        if (design.style() == SettingStyle.PRONG && !(bench.dock().getItem() instanceof PliersItem)) {
            Feedback.fail(player, "need_pliers");
            return false;
        }
        // Progression gates decide last, once everything else is in order and before anything is committed.
        Optional<Component> denied = ImmersiveJewelryAPI.craftDenial(player, design, blank, stones);
        if (denied.isPresent()) {
            Feedback.fail(player, denied.get());
            return false;
        }
        SettingSim provisional = work.setting();
        SettingParams params = BenchPlanner.settingParams(JewelryDefinitions.server(), design, design.stones(), stones,
                BenchPlanner.pliersPrecision(bench.dock()), WorkSessionManager.preferences(player.getUUID()),
                ImmersiveJewelryAPI.assistDifficulty(player, IJewelryAssistSource.Kind.SETTING));
        SettingSim sim = new SettingSim(params, SettingSim.Mode.SET);
        float[] seats = new float[params.sockets()];
        for (int s = 0; s < seats.length; s++) seats[s] = provisional != null && s < provisional.params().sockets() ? provisional.seat(s) : 0.9F;
        sim.restore(new float[params.sockets() * params.points()], seats);
        work.setSetting(sim);
        work.commit();
        work.setStage(BenchWork.Stage.SETTING);
        work.bump();
        bench.setWork(work);
        return true;
    }

    // ================================================================================================ continuous input

    /** Applies validated samples to the bench's current work. */
    public static void input(ServerPlayer player, JewelersBenchBlockEntity bench, long workId, List<WorkSample> samples) {
        BenchWork work = bench.work();
        if (work == null || work.id() != workId) return;
        switch (work.stage()) {
            case PLAN, SETTING -> setting(player, bench, work, samples);
            case FINISHING -> surface(player, bench, work, samples);
        }
    }

    private static void setting(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work, List<WorkSample> samples) {
        SettingSim sim = work.setting();
        if (sim == null) return;
        boolean prong = sim.params().style() == SettingStyle.PRONG;
        int tool = prong ? SettingSim.TOOL_PLIERS : SettingSim.TOOL_PUSHER;
        boolean pliers = bench.dock().getItem() instanceof PliersItem;
        List<WorkSample> usable = new ArrayList<>(samples.size());
        boolean touches = false;
        for (WorkSample s : samples) {
            boolean contact = s.has(WorkSample.FLAG_CONTACT) && s.aux() == tool && (!prong || pliers);
            touches |= contact;
            usable.add(contact ? s : new WorkSample(s.a(), s.b(), s.c(), s.target(), s.aux(), s.flags() & ~WorkSample.FLAG_CONTACT));
        }
        if (touches && work.kind() == BenchWork.Kind.ASSEMBLE && !work.committed()) {
            if (!commit(player, bench, work)) return;
            sim = work.setting();
        }
        if (touches && prong && !work.pliersCharged()) {
            work.markPliersCharged();
            wearPliers(player, bench);
            if (!(bench.dock().getItem() instanceof PliersItem)) return;
        }
        int events = 0;
        for (WorkSample s : usable) events |= sim.step(s);
        work.bump();
        bench.workChanged();
        settingFeedback(player, bench, events);
        if ((events & SettingSim.EVENT_SECURED) != 0 && work.kind() == BenchWork.Kind.ASSEMBLE && sim.allSecured()) {
            Feedback.info(player, "all_secured");
        }
    }

    private static void wearPliers(ServerPlayer player, JewelersBenchBlockEntity bench) {
        ItemStack pliers = bench.dock().copy();
        if (player.getAbilities().instabuild || !pliers.isDamageableItem()) return;
        pliers.setDamageValue(pliers.getDamageValue() + 1);
        if (pliers.getDamageValue() >= pliers.getMaxDamage()) {
            bench.setDock(ItemStack.EMPTY);
            player.level().playSound(null, bench.getBlockPos(), net.minecraft.sounds.SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
            Feedback.fail(player, "pliers_broke");
        } else {
            bench.setDock(pliers);
        }
    }

    private static void settingFeedback(ServerPlayer player, JewelersBenchBlockEntity bench, int events) {
        if ((events & SettingSim.EVENT_CHIP) != 0) sound(player, bench, ModSounds.WHEEL_CHIP.get(), 0.7F, 1.4F);
        if ((events & SettingSim.EVENT_OVER) != 0) othersSound(player, bench, ModSounds.BENCH_BEND.get(), 0.6F, 0.9F);
        if ((events & SettingSim.EVENT_CONTACT) != 0) othersSound(player, bench, ModSounds.BENCH_PRONG.get(), 0.5F, 1.1F);
    }

    /** Every stone is secured: the piece moves on to its finish. */
    private static void beginFinishing(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work) {
        FinishDefinition finish = JewelryDefinitions.server().finish(work.finish());
        SurfaceParams.Mode mode = finish != null ? SurfaceParams.Mode.forFinish(finish.visualProfile()) : SurfaceParams.Mode.POLISH;
        work.setSurface(new SurfaceSim(BenchPlanner.surfaceParams(JewelryDefinitions.server(), mode, finish, work.seed(), 0F,
                WorkSessionManager.preferences(player.getUUID()))));
        work.setStage(BenchWork.Stage.FINISHING);
        work.bump();
        bench.setWork(work);
        sound(player, bench, ModSounds.BENCH_SEAT.get(), 0.8F, 1.0F);
        Feedback.info(player, "stones_secured");
    }

    private static void surface(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work, List<WorkSample> samples) {
        SurfaceSim sim = work.surface();
        if (sim == null) return;
        int tool = sim.params().mode().tool(sim.phase());
        boolean touches = false;
        for (WorkSample s : samples) touches |= s.has(WorkSample.FLAG_CONTACT) && s.aux() == tool;
        if (touches && !paid(player, bench, work)) return;
        int events = 0;
        for (WorkSample s : samples) {
            events |= sim.step(s);
            if (sim.complete()) break;
        }
        work.bump();
        bench.workChanged();
        if ((events & SurfaceSim.EVENT_PHASE) != 0) Feedback.info(player, "patina_wipe");
        if (sim.complete()) complete(player, bench, work);
    }

    /** Charges the finish or repair material at its first effective use, once per work. */
    private static boolean paid(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work) {
        if (work.kind() == BenchWork.Kind.REPAIR) {
            if (work.repairPaid()) return true;
            JewelryData piece = JewelryData.get(bench.clamp()).orElse(null);
            Ingredient cost = piece == null ? null : BenchPlanner.repairIngredient(JewelryDefinitions.server(), piece.metal());
            if (cost == null) {
                Feedback.fail(player, "repair_no_material");
                return false;
            }
            if (!takeSupply(player, bench, cost, 1)) {
                Feedback.fail(player, "repair_missing", Inventories.display(cost, 1).getHoverName());
                return false;
            }
            work.markRepairPaid();
            return true;
        }
        if (work.finishPaid()) return true;
        FinishDefinition finish = JewelryDefinitions.server().finish(work.finish());
        if (finish == null) {
            Feedback.fail(player, "choose_finish");
            return false;
        }
        if (finish.cost() != null && finish.costCount() > 0 && !Inventories.isEmpty(finish.cost())
                && !takeSupply(player, bench, finish.cost(), finish.costCount())) {
            Feedback.fail(player, "finish_cost", Inventories.display(finish.cost(), finish.costCount()).getHoverName());
            return false;
        }
        work.markFinishPaid();
        return true;
    }

    /** Takes {@code count} matching items from the bench's supplies, all or nothing. Creative players pay nothing. */
    private static boolean takeSupply(ServerPlayer player, JewelersBenchBlockEntity bench, Ingredient cost, int count) {
        if (player.getAbilities().instabuild) return true;
        int have = 0;
        for (int i = 0; i < JewelersBenchBlockEntity.SUPPLIES; i++) if (cost.test(bench.supply(i))) have += bench.supply(i).getCount();
        if (have < count) return false;
        int left = count;
        for (int i = 0; i < JewelersBenchBlockEntity.SUPPLIES && left > 0; i++) {
            ItemStack s = bench.supply(i);
            if (!cost.test(s)) continue;
            int n = Math.min(left, s.getCount());
            ItemStack rest = s.copyWithCount(s.getCount() - n);
            bench.setSupply(i, rest);
            left -= n;
        }
        return true;
    }

    // ================================================================================================ completion

    private static void complete(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work) {
        if (work.finalized()) return;
        switch (work.kind()) {
            case ASSEMBLE -> completeAssembly(player, bench, work);
            case REPAIR -> completeRepair(player, bench, work);
            case REFINISH -> completeRefinish(player, bench, work);
            default -> {
            }
        }
    }

    /** The assembly's finish took: frame and stones become one finished piece, exactly once. */
    private static void completeAssembly(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work) {
        ItemStack frame = bench.clamp();
        BlankData blank = BlankData.get(frame).orElse(null);
        SettingSim sim = work.setting();
        if (!(frame.getItem() instanceof JewelryBlankItem blankItem) || blank == null || sim == null) return;
        List<GemData> stones = new ArrayList<>();
        for (int s = 0; s < sim.params().sockets(); s++) {
            GemData g = CutGemItem.data(bench.socket(s)).orElse(null);
            if (g == null) continue;
            stones.add(sim.chipped(s) ? g.withDamage(g.damage().worse()) : g);
        }
        if (stones.isEmpty()) return;
        work.markFinalized();
        float setting = sim.quality();
        EnumSet<SettingDefect> defects = sim.defects();
        float cut = 0F;
        for (GemData g : stones) cut += g.cutQuality();
        cut /= stones.size();
        float workmanship = Workmanship.compute(blank.smithingQuality(), cut, setting);
        JewelryData data = new JewelryData(blankItem.form(), blank.metal(), stones, work.design(), work.finish(), blank.smithingQuality(),
                setting, workmanship, blank.integrity(), blank.reworks(), defects, player.getGameProfile().getName(), false, blank.smithing());
        ItemStack result = JewelryFactory.stack(data);
        ForeignData.carry(frame, result);
        MinecraftForge.EVENT_BUS.post(new JewelryCraftedEvent(player, result, data, bench.getBlockPos()));
        for (int s = 0; s < JewelersBenchBlockEntity.SOCKETS; s++) bench.setSocket(s, ItemStack.EMPTY);
        bench.setClamp(result);
        bench.setWork(null);

        ServerLevel level = player.serverLevel();
        level.playSound(null, bench.getBlockPos(), ModSounds.BENCH_FINISH.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        int color = JewelryDefinitions.server().gemOrGeneric(stones.get(0).gem()).color();
        StationEffects.send(level, bench.getBlockPos(), StationEffects.FINISHED, color, workmanship);
        int xp = BenchPlanner.experience(workmanship, stones.size());
        if (xp > 0) ExperienceOrb.award(level, Vec3.atCenterOf(bench.getBlockPos()).add(0, 0.6, 0), xp);
        ModAdvancements.award(player, ModAdvancements.FIRST_SETTING);
        WorkmanshipGrade grade = WorkmanshipGrade.of(workmanship);
        if (grade.ordinal() >= WorkmanshipGrade.FINE.ordinal()) ModAdvancements.award(player, ModAdvancements.FINE_JEWELRY);
        if (grade == WorkmanshipGrade.MASTERWORK) ModAdvancements.award(player, ModAdvancements.MASTERWORK);
        Feedback.info(player, "piece_finished", grade.displayName());
    }

    private static void completeRepair(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work) {
        ItemStack piece = bench.clamp();
        JewelryData data = JewelryData.get(piece).orElse(null);
        if (data == null) return;
        work.markFinalized();
        float cap = BenchPlanner.repairCap(data.reworks());
        float integrity = Math.min(cap, data.integrity() + (float) ServerConfig.get(ServerConfig.REPAIR_AMOUNT));
        ItemStack result = piece.copy();
        data.withIntegrity(integrity).apply(result);
        bench.setClamp(result);
        bench.setWork(null);
        sound(player, bench, ModSounds.BENCH_REPAIR.get(), 0.9F, 1.0F);
        StationEffects.send(player.serverLevel(), bench.getBlockPos(), StationEffects.POLISH, 0xFFE0A0, 0.6F);
        ModAdvancements.award(player, ModAdvancements.REPAIRED);
        MinecraftForge.EVENT_BUS.post(new JewelryReworkedEvent(player, JewelryReworkedEvent.Action.REPAIRED, result.copy()));
    }

    private static void completeRefinish(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work) {
        ItemStack piece = bench.clamp();
        JewelryData data = JewelryData.get(piece).orElse(null);
        if (data == null || work.finish() == null) return;
        work.markFinalized();
        ItemStack result = piece.copy();
        data.withFinish(work.finish()).apply(result);
        bench.setClamp(result);
        bench.setWork(null);
        sound(player, bench, ModSounds.BENCH_POLISH.get(), 0.9F, 1.0F);
        StationEffects.send(player.serverLevel(), bench.getBlockPos(), StationEffects.POLISH, 0xFFFFFF, 1F);
        MinecraftForge.EVENT_BUS.post(new JewelryReworkedEvent(player, JewelryReworkedEvent.Action.REFINISHED, result.copy()));
    }

    // ================================================================================================ reworking a finished piece

    /** Starts retightening, taking apart, repairing or refinishing the finished piece in the clamp. */
    public static void begin(ServerPlayer player, JewelersBenchBlockEntity bench, String kindId, @Nullable ResourceLocation finishId) {
        if (bench.work() != null) {
            Feedback.fail(player, "work_in_progress");
            return;
        }
        ItemStack piece = bench.clamp();
        JewelryData data = JewelryData.get(piece).orElse(null);
        if (!(piece.getItem() instanceof JewelryItem) || data == null) {
            Feedback.fail(player, "rework_needs_piece");
            return;
        }
        BenchWork.Kind kind;
        try {
            kind = BenchWork.Kind.valueOf(kindId.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        long seed = player.getRandom().nextLong();
        BenchWork work = new BenchWork(player.getRandom().nextLong(), kind, seed);
        JewelryDesignRecipe design = BenchPlanner.design(player.level(), data.design());
        work.setDesign(data.design());
        JewelryDefinitions defs = JewelryDefinitions.server();
        WorkSessionManager.Preferences prefs = WorkSessionManager.preferences(player.getUUID());
        switch (kind) {
            case RETIGHTEN, REMOVE -> {
                if (!data.hasGem()) {
                    Feedback.fail(player, "no_stones");
                    return;
                }
                SettingStyle style = BenchPlanner.style(design);
                if (style == SettingStyle.PRONG && !(bench.dock().getItem() instanceof PliersItem)) {
                    Feedback.fail(player, "need_pliers");
                    return;
                }
                SettingParams params = BenchPlanner.settingParams(defs, design, data.gems().size(), data.gems(),
                        BenchPlanner.pliersPrecision(bench.dock()), prefs, ImmersiveJewelryAPI.assistDifficulty(player, IJewelryAssistSource.Kind.SETTING));
                SettingSim sim = new SettingSim(params, kind == BenchWork.Kind.REMOVE ? SettingSim.Mode.REMOVE : SettingSim.Mode.RETIGHTEN);
                BenchPlanner.restore(sim, data, seed, kind == BenchWork.Kind.REMOVE);
                work.setSetting(sim);
                if (kind == BenchWork.Kind.RETIGHTEN) work.setRetightenBaseline(data.settingQuality(), sim.quality());
                else work.setOriginal(data.gems());
            }
            case REPAIR -> {
                float cap = BenchPlanner.repairCap(data.reworks());
                if (data.integrity() >= cap - 0.001F) {
                    Feedback.info(player, "repair_not_needed");
                    return;
                }
                if (BenchPlanner.repairIngredient(defs, data.metal()) == null) {
                    Feedback.fail(player, "repair_no_material");
                    return;
                }
                float damage = (cap - data.integrity()) / Math.max(0.05F, cap);
                work.setSurface(new SurfaceSim(BenchPlanner.surfaceParams(defs, SurfaceParams.Mode.REPAIR, null, seed, damage, prefs)));
            }
            case REFINISH -> {
                FinishDefinition finish = defs.finish(finishId);
                if (finish == null || finish.id().equals(data.finish())) {
                    Feedback.fail(player, "finish_same");
                    return;
                }
                if (design != null && !design.allowsFinish(finish.id())) {
                    Feedback.fail(player, "finish_unavailable");
                    return;
                }
                work.setFinish(finish.id());
                work.setSurface(new SurfaceSim(BenchPlanner.surfaceParams(defs, SurfaceParams.Mode.forFinish(finish.visualProfile()), finish,
                        seed, 0F, prefs)));
            }
            case ASSEMBLE -> {
                return;
            }
        }
        bench.setWork(work);
        sound(player, bench, ModSounds.BENCH_PLACE.get(), 0.6F, 1.1F);
    }

    /**
     * The worker declares the setting done. An assembly moves on to its finish once every stone is held (the player decides
     * when the points are as good as they want them); retightening is re-evaluated from what was fixed (no experience).
     */
    public static void finishWork(ServerPlayer player, JewelersBenchBlockEntity bench, long workId) {
        BenchWork work = bench.work();
        if (work == null || work.id() != workId) return;
        if (work.kind() == BenchWork.Kind.ASSEMBLE && work.stage() == BenchWork.Stage.SETTING && work.setting() != null) {
            if (work.setting().gripping()) {
                Feedback.fail(player, "lift_first");
            } else if (!work.setting().allSecured()) {
                Feedback.fail(player, "stones_not_secured");
            } else {
                beginFinishing(player, bench, work);
            }
            return;
        }
        if (work.kind() != BenchWork.Kind.RETIGHTEN) return;
        SettingSim sim = work.setting();
        ItemStack piece = bench.clamp();
        JewelryData data = JewelryData.get(piece).orElse(null);
        if (sim == null || data == null || sim.gripping() || work.finalized()) return;
        work.markFinalized();
        float setting = Math.max(0F, Math.min(1F, work.priorSetting() + (sim.quality() - work.baseline())));
        EnumSet<SettingDefect> defects = sim.defects();
        if (data.defects().contains(SettingDefect.CHIPPED_STONE)) defects.add(SettingDefect.CHIPPED_STONE);
        if (data.defects().contains(SettingDefect.SCRATCHED_METAL)) defects.add(SettingDefect.SCRATCHED_METAL);
        List<GemData> stones = new ArrayList<>(data.gems());
        for (int s = 0; s < stones.size() && s < sim.params().sockets(); s++) {
            if (sim.chipped(s)) stones.set(s, stones.get(s).withDamage(stones.get(s).damage().worse()));
        }
        JewelryData next = data.withGems(stones);
        next = next.withSetting(setting, Workmanship.compute(next.smithingQuality(), next.cutQuality(), setting), defects);
        ItemStack result = piece.copy();
        next.apply(result);
        bench.setClamp(result);
        bench.setWork(null);
        sound(player, bench, ModSounds.BENCH_SEAT.get(), 0.8F, 1.1F);
        MinecraftForge.EVENT_BUS.post(new JewelryReworkedEvent(player, JewelryReworkedEvent.Action.RETIGHTENED, result.copy()));
        Feedback.info(player, "retighten_done");
    }

    /** Taking apart: one opened stone into the tray. The piece in the clamp loses exactly that stone in the same step. */
    private static void liftForRemoval(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work, int socket, int trayIndex) {
        SettingSim sim = work.setting();
        if (sim == null || work.lifted(socket) || socket >= work.original().size()) return;
        if (!sim.opened(socket)) {
            Feedback.fail(player, "socket_closed");
            return;
        }
        ItemStack piece = bench.clamp();
        JewelryData data = JewelryData.get(piece).orElse(null);
        if (data == null) return;
        int into = trayIndex >= 0 && trayIndex < JewelersBenchBlockEntity.TRAY && bench.tray(trayIndex).isEmpty() ? trayIndex : bench.firstEmptyTray();
        if (into < 0) {
            Feedback.fail(player, "tray_full");
            return;
        }
        GemData stone = work.original().get(socket);
        // A clumsy removal marks the stone it worked on.
        if (sim.strain(socket) > 0.3F) stone = stone.withDamage(stone.damage().worse());
        ItemStack loose;
        ItemStack legacy = data.legacyConverted() ? LegacyJewelcraft.legacyInset(piece) : ItemStack.EMPTY;
        if (!legacy.isEmpty() && JewelryDefinitions.server().gem(stone.gem()) == null) loose = legacy.copy();
        else loose = CutGemItem.create(stone);
        work.markLifted(socket);
        List<GemData> remaining = new ArrayList<>();
        for (int s = 0; s < work.original().size(); s++) if (!work.lifted(s)) remaining.add(work.original().get(s));
        ItemStack updated = piece.copy();
        data.withGems(remaining).apply(updated);
        bench.setTray(into, loose);
        bench.setClamp(updated);
        work.bump();
        bench.setWork(work);
        sound(player, bench, ModSounds.BENCH_PRONG.get(), 0.7F, 0.8F);
        if (work.liftedCount() >= work.original().size()) completeRemoval(player, bench, work, data);
    }

    /** The last stone is out: the frame becomes a blank, worn by the rework, keeping everything else it carried. */
    private static void completeRemoval(ServerPlayer player, JewelersBenchBlockEntity bench, BenchWork work, JewelryData data) {
        SettingSim sim = work.setting();
        float quality = sim != null ? sim.removalQuality() : 1F;
        BlankData frame = BenchPlanner.frameAfterRemoval(data, quality, sim != null && sim.defects().contains(SettingDefect.SCRATCHED_METAL));
        var blankItem = JewelryFactory.blankItemFor(data.form());
        ItemStack blank = blankItem == null ? ItemStack.EMPTY : new ItemStack(blankItem);
        if (!blank.isEmpty()) {
            frame.apply(blank);
            ForeignData.carry(bench.clamp(), blank);
        }
        work.markFinalized();
        bench.setClamp(blank);
        bench.setWork(null);
        ModAdvancements.award(player, ModAdvancements.RECOVERED);
        MinecraftForge.EVENT_BUS.post(new JewelryReworkedEvent(player, JewelryReworkedEvent.Action.STONES_REMOVED, blank.copy()));
        Feedback.info(player, "removal_done");
    }

    // ================================================================================================ packing

    /** Packs committed work into a carrier for the player; the bench keeps the uncommitted tray, supplies and tools. */
    public static void pack(ServerPlayer player, JewelersBenchBlockEntity bench) {
        BenchWork work = bench.work();
        if (work != null && ((work.setting() != null && work.setting().gripping()) || (work.surface() != null && work.surface().contact()))) {
            Feedback.fail(player, "lift_first");
            return;
        }
        ItemStack carrier = bench.pack();
        if (carrier.isEmpty()) {
            Feedback.fail(player, "nothing_to_pack");
            return;
        }
        Inventories.give(player, carrier);
        sound(player, bench, ModSounds.BENCH_PLACE.get(), 0.7F, 0.8F);
        Feedback.info(player, "packed");
    }

    // ================================================================================================ commands

    public static void command(ServerPlayer player, JewelersBenchBlockEntity bench, WorkCommand command, long workId, CompoundTag args) {
        switch (command) {
            case PLACE -> placeFrom(player, bench, args.getInt("From"), args.getString("Target"), args.getInt("Index"));
            case TAKE -> take(player, bench, args.getString("Target"), args.getInt("Index"));
            case SELECT_DESIGN -> selectDesign(player, bench, ResourceLocation.tryParse(args.getString("Design")));
            case SELECT_FINISH -> selectFinish(player, bench, ResourceLocation.tryParse(args.getString("Finish")));
            case SEAT -> seat(player, bench, args.getInt("Tray"), args.getInt("Socket"), args.getFloat("Dx"), args.getFloat("Dy"), args.getFloat("Turn"));
            case LIFT -> lift(player, bench, args.getInt("Socket"), args.contains("Tray") ? args.getInt("Tray") : -1);
            case CANCEL_PLAN -> cancelPlan(player, bench, false);
            case BEGIN -> begin(player, bench, args.getString("Kind"), ResourceLocation.tryParse(args.getString("Finish")));
            case FINISH_WORK -> finishWork(player, bench, workId);
            case PACK -> pack(player, bench);
            default -> {
            }
        }
    }

    // ================================================================================================ helpers

    private static boolean refuse(ServerPlayer player, String key, boolean quiet) {
        if (!quiet) Feedback.fail(player, key);
        return false;
    }

    private static void sound(ServerPlayer player, JewelersBenchBlockEntity bench, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, bench.getBlockPos(), sound, SoundSource.BLOCKS, volume, pitch);
    }

    /** A sound for everyone around but the operator, who hears their own predicted one. */
    private static void othersSound(ServerPlayer player, JewelersBenchBlockEntity bench, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player.level().playSound(player, bench.getBlockPos(), sound, SoundSource.BLOCKS, volume, pitch);
    }

    private BenchOperations() {}
}
