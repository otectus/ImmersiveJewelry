package com.otectus.immersivejewelry.minigame;

import com.otectus.immersivejewelry.advancement.ModAdvancements;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.event.GemCutEvent;
import com.otectus.immersivejewelry.api.integration.IJewelryAssistSource;
import com.otectus.immersivejewelry.api.event.JewelryCraftedEvent;
import com.otectus.immersivejewelry.api.event.JewelryReworkedEvent;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.migration.LegacyJewelcraft;
import com.otectus.immersivejewelry.minigame.cutting.CutSimulation;
import com.otectus.immersivejewelry.minigame.cutting.CuttingService;
import com.otectus.immersivejewelry.minigame.cutting.CuttingSession;
import com.otectus.immersivejewelry.minigame.setting.BenchService;
import com.otectus.immersivejewelry.minigame.setting.SettingParams;
import com.otectus.immersivejewelry.minigame.setting.SettingSession;
import com.otectus.immersivejewelry.minigame.setting.SettingSimulation;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.BenchActionPacket;
import com.otectus.immersivejewelry.network.packet.CloseSessionPacket;
import com.otectus.immersivejewelry.network.packet.CutInputPacket;
import com.otectus.immersivejewelry.network.packet.CutResultPacket;
import com.otectus.immersivejewelry.network.packet.CutStartPacket;
import com.otectus.immersivejewelry.network.packet.OpenCutSelectPacket;
import com.otectus.immersivejewelry.network.packet.SelectCutPacket;
import com.otectus.immersivejewelry.network.packet.SettingInputPacket;
import com.otectus.immersivejewelry.network.packet.SettingResultPacket;
import com.otectus.immersivejewelry.network.packet.SettingStartPacket;
import com.otectus.immersivejewelry.network.packet.StartSettingPacket;
import com.otectus.immersivejewelry.quality.CutGrade;
import com.otectus.immersivejewelry.quality.Workmanship;
import com.otectus.immersivejewelry.quality.WorkmanshipGrade;
import com.otectus.immersivejewelry.recipe.DesignKnowledge;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModSounds;
import com.otectus.immersivejewelry.util.Feedback;
import com.otectus.immersivejewelry.util.Inventories;
import com.otectus.immersivejewelry.util.StationEffects;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every cutting and setting session (spec 33-35, 61-63). The server generates the parameters, replays the
 * client's step inputs through the same simulation and computes every outcome itself; clients never submit a score or
 * an item.
 *
 * <p>Material safety. A stone being cut never leaves the wheel: a cancelled session changes nothing and only a
 * completed cut rewrites the stone, in place. Stones and finish materials for a new piece leave the player's inventory
 * only into the bench's persisted escrow, and leave the escrow exactly once: consumed on completion, refunded on
 * cancellation. On chunk unload the escrow stays in the saved bench and is dropped there on its next load. A session
 * completes at most once; replayed or duplicate input steps are ignored.</p>
 */
public final class SessionManager {
    private static final Map<UUID, JewelrySession> BY_PLAYER = new HashMap<>();
    private static final Map<GlobalPos, JewelrySession> BY_STATION = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static int nextId = new Random().nextInt(1 << 24);
    private static int tickCounter;

    public static Optional<JewelrySession> byPlayer(UUID player) {
        return Optional.ofNullable(BY_PLAYER.get(player));
    }

    @Nullable
    public static JewelrySession atStation(Level level, BlockPos pos) {
        return BY_STATION.get(GlobalPos.of(level.dimension(), pos));
    }

    public static Collection<JewelrySession> all() {
        return List.copyOf(BY_PLAYER.values());
    }

    // ============================================================================================== cutting

    public static void openCutSelect(ServerPlayer player, LapidaryWheelBlockEntity wheel) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        ItemStack stone = wheel.stone();
        boolean recut = stone.is(ModItems.CUT_GEM.get());
        GemData data = (recut ? CutGemItem.data(stone) : RoughGemItem.data(stone)).orElse(null);
        if (data == null) {
            Feedback.fail(player, "stone_unreadable");
            return;
        }
        GemDefinition gem = defs.gemOrGeneric(data.gem());
        List<OpenCutSelectPacket.Option> options = CuttingService.options(defs, gem, data, recut);
        if (options.isEmpty()) {
            Feedback.fail(player, "no_cuts");
            return;
        }
        send(player, new OpenCutSelectPacket(wheel.getBlockPos(), data, recut, wheel.tier(), options));
    }

    public static void selectCut(ServerPlayer player, SelectCutPacket packet) {
        if (!player.level().isLoaded(packet.pos()) || !near(player, packet.pos())) return;
        if (!(player.level().getBlockEntity(packet.pos()) instanceof LapidaryWheelBlockEntity wheel) || !wheel.hasStone()) return;
        if (wheel.isLockedFor(player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        JewelryDefinitions defs = JewelryDefinitions.server();
        ItemStack stone = wheel.stone();
        boolean recut = stone.is(ModItems.CUT_GEM.get());
        GemData data = (recut ? CutGemItem.data(stone) : RoughGemItem.data(stone)).orElse(null);
        CutDefinition cut = defs.cut(packet.cut());
        if (data == null || cut == null) return;
        GemDefinition gem = defs.gemOrGeneric(data.gem());
        if (!gem.supports(cut.id())) {
            Feedback.fail(player, "cut_unsupported");
            return;
        }
        cancelExisting(player);
        long now = Util.getMillis();
        int ready = ServerConfig.get(ServerConfig.READY_DELAY_MS);
        long seed = RANDOM.nextLong();
        CuttingSession session = new CuttingSession(nextId(), player.getUUID(), player.level().dimension(), wheel.getBlockPos(), now,
                now + ready, data, cut.id(), recut,
                CuttingService.params(gem, data, cut, wheel.tier(), packet.largerTargets(), packet.slower(),
                        ImmersiveJewelryAPI.assistDifficulty(player, IJewelryAssistSource.Kind.CUTTING), seed));
        register(session);
        wheel.lock(player.getUUID());
        player.level().playSound(null, wheel.getBlockPos(), ModSounds.WHEEL_SPIN.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        send(player, new CutStartPacket(session.id, wheel.getBlockPos(), data, cut.id(), session.sim.params(), ready));
    }

    public static void cutInput(ServerPlayer player, CutInputPacket packet) {
        cutInput(player, packet, Util.getMillis());
    }

    /** {@code nowMs}: the server clock when the packet arrived (tests pass their own). */
    public static void cutInput(ServerPlayer player, CutInputPacket packet, long nowMs) {
        CuttingSession session = session(player, packet.sessionId(), CuttingSession.class);
        if (session == null || session.isComplete()) return;
        int n = Math.min(packet.xs().length, packet.ys().length);
        int accepted = accept(player, session, packet.firstStep(), n, nowMs);
        int events = 0;
        for (int i = 0; i < accepted && !session.isComplete(); i++) {
            session.applyStep(session.nextStep, packet.xs()[i], packet.ys()[i], ((packet.grinding() >>> i) & 1L) != 0);
            events |= session.sim.lastEvents();
        }
        cuttingFeedback(player, session, events);
        if (session.isComplete()) finishCut(player, session);
    }

    private static void cuttingFeedback(ServerPlayer player, CuttingSession session, int events) {
        ServerLevel level = player.serverLevel();
        int color = JewelryDefinitions.server().gemOrGeneric(session.stone.gem()).color();
        if ((events & CutSimulation.EVENT_CHIP) != 0) {
            level.playSound(null, session.pos, ModSounds.WHEEL_CHIP.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            StationEffects.send(level, session.pos, StationEffects.CHIP, color, 1F);
        }
        if ((events & (CutSimulation.EVENT_FACET | CutSimulation.EVENT_OVERCUT)) != 0) {
            level.playSound(null, session.pos, ModSounds.WHEEL_FACET.get(), SoundSource.BLOCKS, 0.6F,
                    (events & CutSimulation.EVENT_OVERCUT) != 0 ? 0.7F : 1.1F);
        }
        if ((events & CutSimulation.EVENT_GRIND) != 0 && session.effectsThrottle++ % 3 == 0) {
            float pressure = session.sim.pressure();
            // Others hear the grind; the cutter hears their own client-side loop.
            level.playSound(player, session.pos, ModSounds.WHEEL_GRIND.get(), SoundSource.BLOCKS, 0.35F + 0.3F * pressure, 0.8F + 0.4F * pressure);
            StationEffects.send(level, session.pos, StationEffects.GRIND, color, pressure);
        }
    }

    private static void finishCut(ServerPlayer player, CuttingSession session) {
        LapidaryWheelBlockEntity wheel = wheelAt(player.serverLevel(), session);
        if (wheel == null) {
            cancel(session, player, Feedback.message("station_missing"));
            return;
        }
        unregister(session);
        JewelryDefinitions defs = JewelryDefinitions.server();
        GemDefinition gem = defs.gemOrGeneric(session.stone.gem());
        CutDefinition cut = defs.cut(session.cut);
        if (cut == null) {
            wheel.unlock();
            send(player, new CloseSessionPacket(session.id, Feedback.message("cut_unsupported")));
            return;
        }
        CuttingService.Outcome outcome = CuttingService.outcome(gem, cut, session.stone, session.recut, session.sim);
        ServerLevel level = player.serverLevel();
        // Progression mods hear the result before the stone goes back in the wheel: they may refine a cut stone's
        // quality or leave extra dust for a shattered one. The experience, advancements and the result packet follow.
        GemCutEvent event = new GemCutEvent(player, session.stone, outcome.after(), session.pos);
        MinecraftForge.EVENT_BUS.post(event);
        GemData after = event.getAfter();
        boolean shattered = outcome.shattered() || after == null;
        float quality = shattered ? outcome.quality() : after.cutQuality();
        if (shattered) {
            wheel.setStone(new ItemStack(ModItems.GEM_DUST.get(), Math.min(64, 1 + level.random.nextInt(2) + event.getExtraDust())));
            level.playSound(null, session.pos, ModSounds.WHEEL_CHIP.get(), SoundSource.BLOCKS, 1.0F, 0.6F);
        } else {
            wheel.setStone(CutGemItem.create(after));
            level.playSound(null, session.pos, ModSounds.WHEEL_FINISH.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            StationEffects.send(level, session.pos, StationEffects.FINISHED, gem.color(), quality);
        }
        wheel.unlock();
        if (wheel.wearLap()) Feedback.info(player, "lap_worn_out");
        int xp = shattered ? 0 : CuttingService.experience(quality, gem, cut);
        if (xp > 0) ExperienceOrb.award(level, Vec3.atCenterOf(session.pos).add(0, 0.6, 0), xp);
        if (!shattered) {
            ModAdvancements.award(player, ModAdvancements.FIRST_CUT);
            if (CutGrade.of(quality) == CutGrade.FLAWLESS) ModAdvancements.award(player, ModAdvancements.FLAWLESS);
        }
        send(player, new CutResultPacket(session.id, session.stone, after, quality, session.sim.chips(),
                session.sim.timedOut(), xp));
    }

    // ============================================================================================== bench

    public static void startSetting(ServerPlayer player, StartSettingPacket packet) {
        JewelersBenchBlockEntity bench = benchFor(player, packet.pos());
        if (bench == null) return;
        InteractionHand hand = BenchService.pliersHand(player);
        if (hand == null) {
            Feedback.fail(player, "need_pliers");
            return;
        }
        ItemStack workpiece = bench.workpiece();
        if (!(workpiece.getItem() instanceof JewelryBlankItem blankItem)) return;
        BlankData blank = BlankData.get(workpiece).orElse(null);
        JewelryDesignRecipe design = BenchService.design(player.level(), packet.design());
        if (blank == null || design == null || !design.form().equals(blankItem.form()) || !DesignKnowledge.knows(player, design)) {
            Feedback.fail(player, "design_unavailable");
            return;
        }
        int[] slots = packet.slots();
        Set<Integer> distinct = new HashSet<>();
        for (int s : slots) distinct.add(s);
        if (slots.length != design.stones() || distinct.size() != slots.length) {
            Feedback.fail(player, "stones_mismatch");
            return;
        }
        List<GemData> stones = new ArrayList<>();
        for (int slot : slots) {
            if (slot < 0 || slot >= player.getInventory().items.size()) return;
            GemData g = CutGemItem.data(player.getInventory().items.get(slot)).orElse(null);
            if (g == null || !design.accepts(g) || (!stones.isEmpty() && !stones.get(0).gem().equals(g.gem()))) {
                Feedback.fail(player, "stones_mismatch");
                return;
            }
            stones.add(g);
        }
        FinishDefinition finish = JewelryDefinitions.server().finish(packet.finish());
        if (finish == null || !design.allowsFinish(finish.id())) {
            Feedback.fail(player, "finish_unavailable");
            return;
        }
        if (!Inventories.has(player, finish.cost(), finish.costCount())) {
            Feedback.fail(player, "finish_cost", Inventories.display(finish.cost(), finish.costCount()).getHoverName());
            return;
        }
        // Progression gates decide last, once everything else is in order and before anything enters escrow.
        Optional<Component> denied = ImmersiveJewelryAPI.craftDenial(player, design, blank, stones);
        if (denied.isPresent()) {
            Feedback.fail(player, denied.get());
            return;
        }
        cancelExisting(player);
        // Escrow: the stones first (their slots were validated), then the finish material.
        List<ItemStack> escrow = new ArrayList<>();
        for (int slot : slots) escrow.add(player.getInventory().items.get(slot).split(1));
        List<ItemStack> cost = Inventories.take(player, finish.cost(), finish.costCount());
        if (cost == null) {
            escrow.forEach(s -> Inventories.give(player, s));
            Feedback.fail(player, "materials_changed");
            return;
        }
        escrow.addAll(cost);
        startBenchSession(player, bench, hand, SettingSession.Mode.SET, design, stones, finish.id(), escrow, packet.largerTargets(), packet.slower());
    }

    public static void benchAction(ServerPlayer player, BenchActionPacket packet) {
        JewelersBenchBlockEntity bench = benchFor(player, packet.pos());
        if (bench == null) return;
        switch (packet.action()) {
            case TAKE -> StationInteractions.takeWorkpiece(player, bench);
            case REPAIR -> StationInteractions.repair(player, bench);
            case REFINISH -> StationInteractions.refinish(player, bench, packet.finish());
            case REMOVE, RETIGHTEN -> {
                InteractionHand hand = BenchService.pliersHand(player);
                JewelryData data = JewelryData.get(bench.workpiece()).orElse(null);
                if (hand == null) {
                    Feedback.fail(player, "need_pliers");
                    return;
                }
                if (data == null || !data.hasGem()) return;
                cancelExisting(player);
                SettingSession.Mode mode = packet.action() == BenchActionPacket.Action.REMOVE ? SettingSession.Mode.REMOVE : SettingSession.Mode.RETIGHTEN;
                startBenchSession(player, bench, hand, mode, BenchService.design(player.level(), data.design()), data.gems(), data.finish(),
                        List.of(), packet.largerTargets(), packet.slower());
            }
        }
    }

    private static void startBenchSession(ServerPlayer player, JewelersBenchBlockEntity bench, InteractionHand hand, SettingSession.Mode mode,
                                          @Nullable JewelryDesignRecipe design, List<GemData> stones, @Nullable net.minecraft.resources.ResourceLocation finish,
                                          List<ItemStack> escrow, boolean larger, boolean slower) {
        long now = Util.getMillis();
        int ready = ServerConfig.get(ServerConfig.READY_DELAY_MS);
        SettingParams params = BenchService.params(mode, design, stones, BenchService.pliersPrecision(player.getItemInHand(hand)), larger, slower,
                ImmersiveJewelryAPI.assistDifficulty(player, IJewelryAssistSource.Kind.SETTING), RANDOM.nextLong());
        SettingSession session = new SettingSession(nextId(), player.getUUID(), player.level().dimension(), bench.getBlockPos(), now, now + ready,
                mode, hand, design != null ? design.getId() : null, stones, finish, params);
        register(session);
        ItemStack shown = stones.isEmpty() ? ItemStack.EMPTY : CutGemItem.create(stones.get(0));
        bench.beginSession(player.getUUID(), escrow, mode == SettingSession.Mode.SET ? shown : ItemStack.EMPTY);
        player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_PLACE.get(), SoundSource.BLOCKS, 0.8F, 1.1F);
        send(player, new SettingStartPacket(session.id, bench.getBlockPos(), mode, params, stones, bench.workpiece().copy(), ready));
    }

    public static void settingInput(ServerPlayer player, SettingInputPacket packet) {
        settingInput(player, packet, Util.getMillis());
    }

    /** {@code nowMs}: the server clock when the packet arrived (tests pass their own). */
    public static void settingInput(ServerPlayer player, SettingInputPacket packet, long nowMs) {
        SettingSession session = session(player, packet.sessionId(), SettingSession.class);
        if (session == null || session.isComplete()) return;
        int accepted = accept(player, session, packet.firstStep(), packet.count(), nowMs);
        for (int i = 0; i < accepted && !session.isComplete(); i++) {
            session.applyStep(session.nextStep, 0, 0, ((packet.squeezing() >>> i) & 1L) != 0);
        }
        settingFeedback(player, session, session.drainEvents());
        if (session.isComplete()) finishSetting(player, session);
    }

    private static void settingFeedback(ServerPlayer player, SettingSession session, int events) {
        ServerLevel level = player.serverLevel();
        if ((events & SettingSimulation.EVENT_SET) != 0) {
            level.playSound(player, session.pos, ModSounds.BENCH_PRONG.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
            StationEffects.send(level, session.pos, StationEffects.PRONG, 0xFFE8B0, 0.5F);
        }
        if ((events & SettingSimulation.EVENT_OVER) != 0) level.playSound(player, session.pos, ModSounds.BENCH_BEND.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        if ((events & SettingSimulation.EVENT_CHIP) != 0) level.playSound(null, session.pos, ModSounds.WHEEL_CHIP.get(), SoundSource.BLOCKS, 0.8F, 1.3F);
        JewelersBenchBlockEntity bench = benchAt(level, session);
        if (bench != null) bench.setProgress(session.sim.doneCount() / (float) session.sim.params().points());
    }

    private static void finishSetting(ServerPlayer player, SettingSession session) {
        JewelersBenchBlockEntity bench = benchAt(player.serverLevel(), session);
        if (bench == null) {
            cancel(session, player, Feedback.message("station_missing"));
            return;
        }
        unregister(session);
        switch (session.mode) {
            case SET -> completeSet(player, session, bench);
            case RETIGHTEN -> completeRetighten(player, session, bench);
            case REMOVE -> completeRemoval(player, session, bench);
        }
        ItemStack pliers = player.getItemInHand(session.hand);
        if (!player.getAbilities().instabuild && pliers.isDamageableItem()) pliers.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(session.hand));
    }

    private static void completeSet(ServerPlayer player, SettingSession session, JewelersBenchBlockEntity bench) {
        ItemStack workpiece = bench.workpiece();
        BlankData blank = BlankData.get(workpiece).orElse(null);
        if (!(workpiece.getItem() instanceof JewelryBlankItem blankItem) || blank == null) {
            refund(player, bench, session);
            return;
        }
        SettingSimulation sim = session.sim;
        float setting = sim.quality();
        List<GemData> stones = new ArrayList<>(session.stones);
        EnumSet<SettingDefect> defects = sim.defects();
        if (sim.chipped() && !stones.isEmpty()) {
            int i = player.getRandom().nextInt(stones.size());
            stones.set(i, stones.get(i).withDamage(stones.get(i).damage().worse()));
        }
        float cut = 0F;
        for (GemData g : stones) cut += g.cutQuality();
        cut /= Math.max(1, stones.size());
        float workmanship = Workmanship.compute(blank.smithingQuality(), cut, setting);
        JewelryData data = new JewelryData(blankItem.form(), blank.metal(), stones, session.design, session.finish, blank.smithingQuality(),
                setting, workmanship, blank.integrity(), blank.reworks(), defects, player.getGameProfile().getName(), false, blank.smithing());
        ItemStack result = JewelryFactory.stack(data);
        MinecraftForge.EVENT_BUS.post(new JewelryCraftedEvent(player, result, data, session.pos));
        bench.completeSession(result);

        ServerLevel level = player.serverLevel();
        level.playSound(null, session.pos, ModSounds.BENCH_SEAT.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        level.playSound(null, session.pos, ModSounds.BENCH_FINISH.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        int color = JewelryDefinitions.server().gemOrGeneric(stones.get(0).gem()).color();
        StationEffects.send(level, session.pos, StationEffects.FINISHED, color, workmanship);
        int xp = BenchService.experience(workmanship, stones.size());
        if (xp > 0) ExperienceOrb.award(level, Vec3.atCenterOf(session.pos).add(0, 0.6, 0), xp);
        ModAdvancements.award(player, ModAdvancements.FIRST_SETTING);
        WorkmanshipGrade grade = WorkmanshipGrade.of(workmanship);
        if (grade.ordinal() >= WorkmanshipGrade.FINE.ordinal()) ModAdvancements.award(player, ModAdvancements.FINE_JEWELRY);
        if (grade == WorkmanshipGrade.MASTERWORK) ModAdvancements.award(player, ModAdvancements.MASTERWORK);
        send(player, new SettingResultPacket(session.id, session.mode, setting, workmanship, defects, sim.timedOut(), result.copy(), xp));
    }

    private static void completeRetighten(ServerPlayer player, SettingSession session, JewelersBenchBlockEntity bench) {
        JewelryData data = JewelryData.get(bench.workpiece()).orElse(null);
        if (data == null) {
            bench.unlock();
            return;
        }
        SettingSimulation sim = session.sim;
        float setting = sim.quality();
        EnumSet<SettingDefect> defects = sim.defects();
        if (data.defects().contains(SettingDefect.CHIPPED_STONE)) defects.add(SettingDefect.CHIPPED_STONE);
        List<GemData> stones = new ArrayList<>(data.gems());
        if (sim.chipped() && !stones.isEmpty()) {
            int i = player.getRandom().nextInt(stones.size());
            stones.set(i, stones.get(i).withDamage(stones.get(i).damage().worse()));
        }
        JewelryData next = data.withGems(stones);
        next = next.withSetting(setting, Workmanship.compute(next.smithingQuality(), next.cutQuality(), setting), defects);
        ItemStack result = bench.workpiece().copy();
        next.apply(result);
        bench.completeSession(result);
        player.level().playSound(null, session.pos, ModSounds.BENCH_SEAT.get(), SoundSource.BLOCKS, 0.8F, 1.1F);
        MinecraftForge.EVENT_BUS.post(new JewelryReworkedEvent(player, JewelryReworkedEvent.Action.RETIGHTENED, result.copy()));
        send(player, new SettingResultPacket(session.id, session.mode, setting, next.workmanship(), defects, sim.timedOut(), result.copy(), 0));
    }

    private static void completeRemoval(ServerPlayer player, SettingSession session, JewelersBenchBlockEntity bench) {
        ItemStack piece = bench.workpiece();
        JewelryData data = JewelryData.get(piece).orElse(null);
        if (data == null) {
            bench.unlock();
            return;
        }
        SettingSimulation sim = session.sim;
        float quality = sim.quality();
        EnumSet<SettingDefect> defects = sim.defects();
        List<GemData> stones = new ArrayList<>(data.gems());
        // A clumsy removal can scratch a stone; a very clumsy one scratches it further.
        if (quality < 0.55F && !stones.isEmpty()) {
            int i = player.getRandom().nextInt(stones.size());
            stones.set(i, stones.get(i).withDamage(stones.get(i).damage().worse()));
        }
        if (sim.chipped() && !stones.isEmpty()) {
            int i = player.getRandom().nextInt(stones.size());
            stones.set(i, stones.get(i).withDamage(stones.get(i).damage().worse()));
        }
        BlankData frame = BenchService.frameAfterRemoval(data, quality, defects.contains(SettingDefect.SCRATCHED_METAL));
        var blankItem = JewelryFactory.blankItemFor(data.form());
        ItemStack blank = blankItem == null ? ItemStack.EMPTY : new ItemStack(blankItem);
        if (!blank.isEmpty()) frame.apply(blank);

        ItemStack legacyInset = data.legacyConverted() ? LegacyJewelcraft.legacyInset(piece) : ItemStack.EMPTY;
        JewelryDefinitions defs = JewelryDefinitions.server();
        for (GemData g : stones) {
            // A converted piece whose stone no definition describes gives back its original inset item.
            if (!legacyInset.isEmpty() && defs.gem(g.gem()) == null) {
                Inventories.give(player, legacyInset.copy());
                legacyInset = ItemStack.EMPTY;
            } else {
                Inventories.give(player, CutGemItem.create(g));
            }
        }
        bench.completeSession(blank);
        player.level().playSound(null, session.pos, ModSounds.BENCH_PRONG.get(), SoundSource.BLOCKS, 0.8F, 0.8F);
        ModAdvancements.award(player, ModAdvancements.RECOVERED);
        MinecraftForge.EVENT_BUS.post(new JewelryReworkedEvent(player, JewelryReworkedEvent.Action.STONES_REMOVED, blank.copy()));
        send(player, new SettingResultPacket(session.id, session.mode, quality, 0F, defects, sim.timedOut(), blank.copy(), 0));
    }

    // ============================================================================================== input validation

    /**
     * How many of the steps in an input packet to apply: the run must continue exactly where the server's copy is
     * (duplicates and gaps are ignored), and it may not run ahead of real time.
     */
    private static int accept(ServerPlayer player, JewelrySession session, int firstStep, int count, long nowMs) {
        if (firstStep != session.nextStep || count <= 0) return 0;
        long elapsed = nowMs - session.startMs;
        if (elapsed < -250L) return 0;
        return SessionTiming.acceptable(firstStep, Math.min(count, session.maxSteps() - firstStep), elapsed, player.latency);
    }

    // ============================================================================================== cancellation and upkeep

    public static void cancelFromClient(ServerPlayer player, int sessionId) {
        JewelrySession session = BY_PLAYER.get(player.getUUID());
        if (session != null && session.id == sessionId) cancel(session, player, null);
    }

    public static void onLogout(ServerPlayer player) {
        JewelrySession session = BY_PLAYER.get(player.getUUID());
        if (session != null) cancel(session, player, null);
    }

    public static void onDeath(ServerPlayer player) {
        JewelrySession session = BY_PLAYER.get(player.getUUID());
        if (session != null) cancel(session, player, Feedback.message("session_interrupted"));
    }

    /** The station was broken or replaced: refund now, before its contents drop. */
    public static void onStationRemoved(Level level, BlockPos pos) {
        JewelrySession session = atStation(level, pos);
        if (session == null || level.getServer() == null) return;
        cancel(session, level.getServer().getPlayerList().getPlayer(session.playerId), Feedback.message("station_missing"));
    }

    /** The station's chunk is unloading and was already saved: escrow stays in the bench for its next load. */
    public static void onStationUnloaded(Level level, BlockPos pos) {
        JewelrySession session = atStation(level, pos);
        if (session == null) return;
        unregister(session);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LapidaryWheelBlockEntity wheel) wheel.unlock();
        if (be instanceof JewelersBenchBlockEntity bench) bench.unlock();
        if (level.getServer() != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerId);
            if (player != null) send(player, new CloseSessionPacket(session.id, Feedback.message("session_interrupted")));
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        for (JewelrySession session : all()) cancel(session, server.getPlayerList().getPlayer(session.playerId), null);
        BY_PLAYER.clear();
        BY_STATION.clear();
    }

    public static void tick(MinecraftServer server) {
        if (BY_PLAYER.isEmpty()) return;
        tickCounter++;
        long now = Util.getMillis();
        for (JewelrySession session : all()) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                cancel(session, null, null);
                continue;
            }
            long end = session.startMs + (long) session.maxSteps() * StepClock.STEP_MS + SessionTiming.graceMs(player.latency);
            if (now > end) {
                // The client went quiet: the remaining steps run without input and the session ends by timeout.
                while (!session.isComplete() && session.nextStep < session.maxSteps()) session.applyStep(session.nextStep, 0, 0, false);
                if (session instanceof CuttingSession c) finishCut(player, c);
                else if (session instanceof SettingSession s) finishSetting(player, s);
                continue;
            }
            if (tickCounter % 5 == 0 && !stillValid(player, session)) cancel(session, player, Feedback.message("session_interrupted"));
        }
    }

    private static void cancel(JewelrySession session, @Nullable ServerPlayer player, @Nullable Component reason) {
        unregister(session);
        MinecraftServer server = player != null ? player.getServer() : ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? server.getLevel(session.dimension) : null;
        if (level != null && level.isLoaded(session.pos)) {
            BlockEntity be = level.getBlockEntity(session.pos);
            if (be instanceof LapidaryWheelBlockEntity wheel) {
                wheel.unlock(); // the stone simply stays on the wheel
            } else if (be instanceof JewelersBenchBlockEntity bench) {
                for (ItemStack stack : bench.takeEscrow()) {
                    if (player != null && player.isAlive()) player.getInventory().add(stack);
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, session.pos.getX() + 0.5, session.pos.getY() + 1.0, session.pos.getZ() + 0.5, stack);
                    }
                }
            }
        }
        if (player != null && reason != null) send(player, new CloseSessionPacket(session.id, reason));
    }

    private static void refund(ServerPlayer player, JewelersBenchBlockEntity bench, SettingSession session) {
        for (ItemStack stack : bench.takeEscrow()) Inventories.give(player, stack);
        send(player, new CloseSessionPacket(session.id, Feedback.message("materials_changed")));
    }

    private static boolean stillValid(ServerPlayer player, JewelrySession session) {
        if (!player.isAlive() || player.level().dimension() != session.dimension || !near(player, session.pos)) return false;
        if (session instanceof SettingSession s && !(player.getItemInHand(s.hand).getItem() instanceof com.otectus.immersivejewelry.item.PliersItem)) {
            return false;
        }
        return session instanceof CuttingSession ? wheelAt(player.serverLevel(), session) != null : benchAt(player.serverLevel(), session) != null;
    }

    public static boolean near(Player player, BlockPos pos) {
        double max = ServerConfig.get(ServerConfig.MAX_STATION_DISTANCE);
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= max * max;
    }

    private static void cancelExisting(ServerPlayer player) {
        JewelrySession existing = BY_PLAYER.get(player.getUUID());
        if (existing != null) cancel(existing, player, null);
    }

    @Nullable
    private static <T extends JewelrySession> T session(ServerPlayer player, int sessionId, Class<T> type) {
        JewelrySession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.id != sessionId || !type.isInstance(session)) return null;
        if (!player.level().dimension().equals(session.dimension) || !near(player, session.pos)) return null;
        return type.cast(session);
    }

    @Nullable
    private static JewelersBenchBlockEntity benchFor(ServerPlayer player, BlockPos pos) {
        if (!player.level().isLoaded(pos) || !near(player, pos)) return null;
        if (!(player.level().getBlockEntity(pos) instanceof JewelersBenchBlockEntity bench)) return null;
        if (bench.isLockedFor(player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return null;
        }
        return bench;
    }

    @Nullable
    private static LapidaryWheelBlockEntity wheelAt(ServerLevel level, JewelrySession session) {
        if (!level.isLoaded(session.pos)) return null;
        return level.getBlockEntity(session.pos) instanceof LapidaryWheelBlockEntity wheel ? wheel : null;
    }

    @Nullable
    private static JewelersBenchBlockEntity benchAt(ServerLevel level, JewelrySession session) {
        if (!level.isLoaded(session.pos)) return null;
        return level.getBlockEntity(session.pos) instanceof JewelersBenchBlockEntity bench ? bench : null;
    }

    private static void register(JewelrySession session) {
        BY_PLAYER.put(session.playerId, session);
        BY_STATION.put(session.station(), session);
    }

    private static void unregister(JewelrySession session) {
        BY_PLAYER.remove(session.playerId, session);
        BY_STATION.remove(session.station(), session);
    }

    private static int nextId() {
        return ++nextId;
    }

    private static void send(ServerPlayer player, Object packet) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private SessionManager() {}
}
