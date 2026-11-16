package com.otectus.immersivejewelry.workstation;

import com.otectus.immersivejewelry.advancement.ModAdvancements;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.event.GemCutEvent;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.integration.IJewelryAssistSource;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.LapItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.quality.CutGrade;
import com.otectus.immersivejewelry.quality.StoneTraits;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModSounds;
import com.otectus.immersivejewelry.util.Feedback;
import com.otectus.immersivejewelry.util.Inventories;
import com.otectus.immersivejewelry.util.StationEffects;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import com.otectus.immersivejewelry.workstation.sim.FacetParams;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Everything that changes a Lapidary Wheel, on the server: walk-up use (mount a stone, install a lap, take things), the
 * operator's discrete actions (plan, finish, take) and their continuous grinding input. One path for each change, all
 * validated here.
 */
public final class WheelOperations {

    // ------------------------------------------------------------------------------------------------ walk-up use

    /** Using the wheel without working at it: laps and stones in hand, sneak to take. Working is entered through the scene. */
    public static void use(ServerPlayer player, LapidaryWheelBlockEntity wheel, InteractionHand hand) {
        if (WorkSessionManager.heldByOther(player, wheel.getBlockPos())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof LapItem) {
            if (wheel.work().map(w -> w.sim().contact()).orElse(false)) return;
            ItemStack old = wheel.swapLap(held.split(1));
            Inventories.give(player, old);
            player.level().playSound(null, wheel.getBlockPos(), ModSounds.WHEEL_LAP.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            Feedback.info(player, "lap_installed", wheel.lap().getHoverName());
            return;
        }
        if (player.isSecondaryUseActive() && held.isEmpty()) {
            if (wheel.hasStone()) takeStone(player, wheel);
            else if (!wheel.lap().isEmpty()) Inventories.give(player, wheel.swapLap(ItemStack.EMPTY));
            return;
        }
        if (!wheel.hasStone() && mount(player, wheel, held)) return;
        if (wheel.hasStone() && wheel.stone().is(ModItems.GEM_DUST.get())) {
            takeStone(player, wheel);
            return;
        }
        if (!com.otectus.immersivejewelry.workstation.station.JewelryStations.available()) {
            // The hands-on wheel needs Diegesis; the stone, the lap and any work on them are kept as they are.
            Feedback.info(player, "hands_on_unavailable", com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.installedVersion());
            return;
        }
        Feedback.info(player, wheel.hasStone() ? "wheel_work_hint" : "wheel_hint");
    }

    /** Whether a stack is something walking up to the wheel puts on it (so using the wheel with it does not open the scene). */
    public static boolean isWalkUpItem(ItemStack held, JewelryDefinitions defs) {
        if (held.isEmpty()) return false;
        return held.getItem() instanceof LapItem || held.is(ModItems.ROUGH_GEM.get()) || held.is(ModItems.CUT_GEM.get())
                || defs.gemForSource(held) != null;
    }

    /** Where a stack the operator puts on the wheel ends up. */
    public enum Placement { NONE, QUILL, LAP }

    /**
     * Where {@link #placeFrom} would put {@code stack} now, changing nothing: a lap goes on the spindle unless the stone
     * is on the lap, a stone (or a raw source item) on the empty quill. The scene shows the answer under a carried stack.
     */
    public static Placement placement(LapidaryWheelBlockEntity wheel, ItemStack stack, JewelryDefinitions defs) {
        if (stack.isEmpty()) return Placement.NONE;
        if (stack.getItem() instanceof LapItem) return wheel.work().map(w -> w.sim().contact()).orElse(false) ? Placement.NONE : Placement.LAP;
        if (wheel.hasStone()) return Placement.NONE;
        boolean mountable = stack.is(ModItems.CUT_GEM.get()) ? CutGemItem.data(stack).isPresent()
                : stack.is(ModItems.ROUGH_GEM.get()) ? RoughGemItem.data(stack).isPresent() : defs.gemForSource(stack) != null;
        return mountable ? Placement.QUILL : Placement.NONE;
    }

    /** Puts one stone from the hand on the quill; a raw source item is appraised first, once and for all. */
    public static boolean mount(ServerPlayer player, LapidaryWheelBlockEntity wheel, ItemStack held) {
        if (held.isEmpty() || wheel.hasStone()) return false;
        JewelryDefinitions defs = JewelryDefinitions.server();
        ItemStack stone;
        GemData data;
        if (held.is(ModItems.ROUGH_GEM.get()) || held.is(ModItems.CUT_GEM.get())) {
            data = held.is(ModItems.CUT_GEM.get()) ? CutGemItem.data(held).orElse(null) : RoughGemItem.data(held).orElse(null);
            if (data == null) return false;
            stone = held.copyWithCount(1);
        } else {
            GemDefinition gem = defs.gemForSource(held);
            if (gem == null) return false;
            data = CutPlanner.appraise(gem, player.getRandom());
            stone = RoughGemItem.create(data);
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        wheel.setStone(stone);
        player.level().playSound(null, wheel.getBlockPos(), ModSounds.WHEEL_MOUNT.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        if (!data.isCut() && !CutWork.present(stone)) {
            Feedback.info(player, "appraised", StoneTraits.size(data.size()), stone.getHoverName(), StoneTraits.clarity(data.inclusions()),
                    StoneTraits.luster(data.naturalQuality()));
        } else if (CutWork.present(stone)) {
            Feedback.info(player, "work_resumable");
        }
        return true;
    }

    /** The stone (and any work on it) goes to the player: never while it is being ground. */
    public static void takeStone(ServerPlayer player, LapidaryWheelBlockEntity wheel) {
        if (!wheel.hasStone() || WorkSessionManager.heldByOther(player, wheel.getBlockPos())) return;
        if (wheel.work().map(w -> w.sim().contact()).orElse(false)) {
            Feedback.fail(player, "lift_first");
            return;
        }
        Inventories.give(player, wheel.takeStone());
        player.level().playSound(null, wheel.getBlockPos(), ModSounds.WHEEL_MOUNT.get(), SoundSource.BLOCKS, 0.6F, 1.3F);
    }

    // ------------------------------------------------------------------------------------------------ operator actions

    /** The gem data of the mounted stone, or null (gem dust, an unreadable stack). */
    @Nullable
    public static GemData stoneData(LapidaryWheelBlockEntity wheel) {
        ItemStack stone = wheel.stone();
        if (stone.is(ModItems.CUT_GEM.get())) return CutGemItem.data(stone).orElse(null);
        if (stone.is(ModItems.ROUGH_GEM.get())) return RoughGemItem.data(stone).orElse(null);
        return null;
    }

    /**
     * Plans a cut. Free before any material was removed; afterwards only confirmed, as a recut from the stone as it now
     * is (the removed material and the chips stay gone).
     */
    public static void planCut(ServerPlayer player, LapidaryWheelBlockEntity wheel, @Nullable ResourceLocation cutId, boolean confirm) {
        GemData data = stoneData(wheel);
        JewelryDefinitions defs = JewelryDefinitions.server();
        CutDefinition cut = defs.cut(cutId);
        if (data == null || cut == null) {
            Feedback.fail(player, "stone_unreadable");
            return;
        }
        GemDefinition gem = defs.gemOrGeneric(data.gem());
        if (!gem.supports(cut.id())) {
            Feedback.fail(player, "cut_unsupported");
            return;
        }
        CutWork current = wheel.work().orElse(null);
        if (current != null && current.sim().contact()) {
            Feedback.fail(player, "lift_first");
            return;
        }
        if (current != null && current.cut().equals(cut.id())) return;
        boolean recut = wheel.stone().is(ModItems.CUT_GEM.get());
        if (current != null && current.started()) {
            if (!confirm) {
                Feedback.info(player, "recut_confirm");
                return;
            }
            // The stone keeps what the work took; the new plan is a recut of what is left.
            CutDefinition old = defs.cut(current.cut());
            if (old != null) {
                GemData after = CutPlanner.afterAbandonedWork(gem, old, data, current.recut(), current.sim());
                ItemStack replaced = wheel.stone().is(ModItems.CUT_GEM.get()) ? CutGemItem.create(after) : RoughGemItem.create(after);
                wheel.setStone(replaced);
                data = after;
            }
            recut = true;
        }
        long seed = player.getRandom().nextLong();
        FacetParams params = CutPlanner.params(defs, gem, data, cut, wheel.tier(), WorkSessionManager.preferences(player.getUUID()),
                ImmersiveJewelryAPI.assistDifficulty(player, IJewelryAssistSource.Kind.CUTTING), seed);
        CutWork work = new CutWork(player.getRandom().nextLong(), cut.id(), recut, seed, params);
        wheel.setWork(work);
        player.level().playSound(null, wheel.getBlockPos(), ModSounds.WHEEL_SPIN.get(), SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    /** Drops a plan nothing was done to yet. */
    public static void clearPlan(ServerPlayer player, LapidaryWheelBlockEntity wheel) {
        CutWork work = wheel.work().orElse(null);
        if (work == null) return;
        if (work.started()) {
            Feedback.fail(player, "plan_started");
            return;
        }
        wheel.setWork(null);
    }

    /**
     * Finishes the cut: when every region reached its window, or earlier when the player accepts a rougher result. The
     * stone is replaced by one cut stone exactly once; the cut event, experience and advancements follow its final
     * values.
     */
    public static void finish(ServerPlayer player, LapidaryWheelBlockEntity wheel, long workId, boolean rough) {
        CutWork work = wheel.work().orElse(null);
        GemData stone = stoneData(wheel);
        if (work == null || stone == null || work.id() != workId) return;
        if (work.sim().contact()) {
            Feedback.fail(player, "lift_first");
            return;
        }
        if (!work.started()) {
            Feedback.fail(player, "cut_not_started");
            return;
        }
        if (!work.sim().ready() && !rough) {
            Feedback.info(player, "cut_accept_rough");
            return;
        }
        JewelryDefinitions defs = JewelryDefinitions.server();
        GemDefinition gem = defs.gemOrGeneric(stone.gem());
        CutDefinition cut = defs.cut(work.cut());
        if (cut == null) {
            Feedback.fail(player, "work_unavailable");
            return;
        }
        CutPlanner.Outcome outcome = CutPlanner.outcome(gem, cut, stone, work.recut(), work.sim());
        ServerLevel level = player.serverLevel();
        // Progression mods hear the result before the stone changes: they may refine its quality or add dust.
        GemCutEvent event = new GemCutEvent(player, stone, outcome.after(), wheel.getBlockPos());
        MinecraftForge.EVENT_BUS.post(event);
        GemData after = event.getAfter();
        boolean shattered = outcome.shattered() || after == null;
        float quality = shattered ? outcome.quality() : after.cutQuality();
        if (shattered) {
            wheel.setStone(new ItemStack(ModItems.GEM_DUST.get(), Math.min(64, 1 + level.random.nextInt(2) + event.getExtraDust())));
            level.playSound(null, wheel.getBlockPos(), ModSounds.WHEEL_CHIP.get(), SoundSource.BLOCKS, 1.0F, 0.6F);
        } else {
            wheel.setStone(CutGemItem.create(after));
            level.playSound(null, wheel.getBlockPos(), ModSounds.WHEEL_FINISH.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            StationEffects.send(level, wheel.getBlockPos(), StationEffects.FINISHED, gem.color(), quality);
        }
        int xp = shattered ? 0 : CutPlanner.experience(quality, gem, cut);
        if (xp > 0) ExperienceOrb.award(level, Vec3.atCenterOf(wheel.getBlockPos()).add(0, 0.6, 0), xp);
        if (!shattered) {
            ModAdvancements.award(player, ModAdvancements.FIRST_CUT);
            if (CutGrade.of(quality) == CutGrade.FLAWLESS) ModAdvancements.award(player, ModAdvancements.FLAWLESS);
            Feedback.info(player, "cut_finished", CutGrade.of(quality).displayName());
        }
    }

    // ------------------------------------------------------------------------------------------------ grinding input

    /** Applies validated samples to the cut; charges the lap's wear once per cut at its first contact. */
    public static void input(ServerPlayer player, LapidaryWheelBlockEntity wheel, long workId, List<WorkSample> samples) {
        CutWork work = wheel.work().orElse(null);
        if (work == null || work.id() != workId || stoneData(wheel) == null) return;
        FacetSim sim = work.sim();
        int events = 0;
        for (WorkSample sample : samples) {
            events |= sim.step(sample);
            if (sim.contact() && !work.lapWorn()) {
                work.markLapWorn();
                if (wheel.wearLap()) Feedback.info(player, "lap_worn_out");
            }
        }
        work.bump();
        wheel.workChanged();
        others(player, wheel, sim, events);
    }

    /** What players around hear of a step batch; the operator hears their own predicted sounds. */
    private static void others(ServerPlayer player, LapidaryWheelBlockEntity wheel, FacetSim sim, int events) {
        ServerLevel level = player.serverLevel();
        if ((events & FacetSim.EVENT_CHIP) != 0) {
            level.playSound(player, wheel.getBlockPos(), ModSounds.WHEEL_CHIP.get(), SoundSource.BLOCKS, 0.8F, 1.1F);
        }
        if ((events & FacetSim.EVENT_DIP) != 0) {
            level.playSound(player, wheel.getBlockPos(), ModSounds.WHEEL_DIP.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        if ((events & (FacetSim.EVENT_ENTER | FacetSim.EVENT_OVER)) != 0) {
            level.playSound(player, wheel.getBlockPos(), ModSounds.WHEEL_FACET.get(), SoundSource.BLOCKS, 0.5F,
                    (events & FacetSim.EVENT_OVER) != 0 ? 0.7F : 1.1F);
        }
    }

    // ------------------------------------------------------------------------------------------------ commands

    public static void command(ServerPlayer player, LapidaryWheelBlockEntity wheel, WorkCommand command, long workId, CompoundTag args) {
        switch (command) {
            case PLAN_CUT -> planCut(player, wheel, ResourceLocation.tryParse(args.getString("Cut")), args.getBoolean("Confirm"));
            case CLEAR_PLAN -> clearPlan(player, wheel);
            case FINISH_CUT -> finish(player, wheel, workId, args.getBoolean("Rough"));
            case TAKE_STONE -> takeStone(player, wheel);
            case PLACE -> placeFrom(player, wheel, args.getInt("From"));
            default -> {
            }
        }
    }

    /**
     * The operator puts a stack from the cursor ({@code from} -1) or an inventory slot on the wheel: a stone on the
     * empty quill (appraised once if raw) or a lap (the old one goes to the inventory).
     */
    public static void placeFrom(ServerPlayer player, LapidaryWheelBlockEntity wheel, int from) {
        ItemStack source;
        if (from < 0) source = player.inventoryMenu.getCarried();
        else if (from < player.getInventory().items.size()) source = player.getInventory().items.get(from);
        else return;
        if (source.isEmpty()) return;
        if (source.getItem() instanceof LapItem) {
            if (wheel.work().map(w -> w.sim().contact()).orElse(false)) return;
            Inventories.give(player, wheel.swapLap(player.getAbilities().instabuild ? source.copyWithCount(1) : source.split(1)));
            player.level().playSound(null, wheel.getBlockPos(), ModSounds.WHEEL_LAP.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        } else if (wheel.hasStone()) {
            Feedback.fail(player, "quill_occupied");
            return;
        } else if (!mount(player, wheel, source)) {
            Feedback.fail(player, "not_a_stone");
            return;
        }
        if (from < 0) player.inventoryMenu.setCarried(source.isEmpty() ? ItemStack.EMPTY : source);
        player.inventoryMenu.broadcastChanges();
    }

    /** The mounted stone's cut, if a work is planned, for the station overlay. */
    public static Optional<ResourceLocation> plannedCut(LapidaryWheelBlockEntity wheel) {
        return wheel.work().map(CutWork::cut);
    }

    private WheelOperations() {}
}
