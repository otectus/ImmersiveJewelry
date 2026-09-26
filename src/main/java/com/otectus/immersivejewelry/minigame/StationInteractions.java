package com.otectus.immersivejewelry.minigame;

import com.otectus.immersivejewelry.advancement.ModAdvancements;
import com.otectus.immersivejewelry.api.event.JewelryReworkedEvent;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.item.LapItem;
import com.otectus.immersivejewelry.item.PliersItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.minigame.cutting.CuttingService;
import com.otectus.immersivejewelry.minigame.setting.BenchService;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.quality.StoneTraits;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModSounds;
import com.otectus.immersivejewelry.util.Feedback;
import com.otectus.immersivejewelry.util.Inventories;
import com.otectus.immersivejewelry.util.StationEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** What using a station does, before any minigame: mounting stones and laps, placing and taking workpieces, repairs. */
public final class StationInteractions {

    // ------------------------------------------------------------------------------------------------ wheel

    public static void useWheel(ServerPlayer player, LapidaryWheelBlockEntity wheel, InteractionHand hand) {
        if (wheel.isLockedFor(player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof LapItem) {
            ItemStack old = wheel.swapLap(held.split(1));
            Inventories.give(player, old);
            player.level().playSound(null, wheel.getBlockPos(), ModSounds.WHEEL_LAP.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            Feedback.info(player, "lap_installed", wheel.lap().getHoverName());
            return;
        }
        if (wheel.hasStone()) {
            if (player.isSecondaryUseActive() && held.isEmpty()) {
                takeStone(player, wheel.getBlockPos());
                return;
            }
            if (wheel.stone().is(ModItems.GEM_DUST.get())) {
                takeStone(player, wheel.getBlockPos());
                return;
            }
            SessionManager.openCutSelect(player, wheel);
            return;
        }
        if (mountStone(player, wheel, held)) return;
        if (player.isSecondaryUseActive() && held.isEmpty() && !wheel.lap().isEmpty()) {
            Inventories.give(player, wheel.swapLap(ItemStack.EMPTY));
            return;
        }
        Feedback.info(player, "wheel_hint");
    }

    /** Puts one stone from the hand on the wheel; a raw source item is appraised first (once and for all). */
    static boolean mountStone(ServerPlayer player, LapidaryWheelBlockEntity wheel, ItemStack held) {
        if (held.isEmpty()) return false;
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
            data = CuttingService.appraise(gem, player.getRandom());
            stone = RoughGemItem.create(data);
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        wheel.setStone(stone);
        player.level().playSound(null, wheel.getBlockPos(), ModSounds.WHEEL_MOUNT.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        if (!data.isCut()) {
            Feedback.info(player, "appraised", StoneTraits.size(data.size()), stone.getHoverName(), StoneTraits.clarity(data.inclusions()),
                    StoneTraits.luster(data.naturalQuality()));
        }
        return true;
    }

    public static void takeStone(ServerPlayer player, BlockPos pos) {
        if (!player.level().isLoaded(pos) || !SessionManager.near(player, pos)) return;
        if (!(player.level().getBlockEntity(pos) instanceof LapidaryWheelBlockEntity wheel) || wheel.isLocked() || !wheel.hasStone()) return;
        Inventories.give(player, wheel.takeStone());
        player.level().playSound(null, pos, ModSounds.WHEEL_MOUNT.get(), SoundSource.BLOCKS, 0.6F, 1.3F);
    }

    // ------------------------------------------------------------------------------------------------ bench

    public static void useBench(ServerPlayer player, JewelersBenchBlockEntity bench, InteractionHand hand) {
        if (bench.isLockedFor(player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!bench.hasWorkpiece()) {
            if (held.getItem() instanceof JewelryBlankItem || held.getItem() instanceof JewelryItem) {
                bench.setWorkpiece(held.split(1));
                player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_PLACE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                Feedback.info(player, bench.workpiece().getItem() instanceof JewelryItem ? "bench_rework_hint" : "bench_blank_hint");
            } else {
                Feedback.info(player, "bench_hint");
            }
            return;
        }
        if (held.getItem() instanceof PliersItem) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), BenchService.options(player, bench));
            return;
        }
        if (held.isEmpty() || player.isSecondaryUseActive()) {
            takeWorkpiece(player, bench);
            return;
        }
        Feedback.info(player, "need_pliers");
    }

    public static void takeWorkpiece(ServerPlayer player, JewelersBenchBlockEntity bench) {
        if (bench.isLocked() || !bench.hasWorkpiece()) return;
        Inventories.give(player, bench.takeWorkpiece());
        player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_PLACE.get(), SoundSource.BLOCKS, 0.6F, 1.3F);
    }

    /** Frame repair (spec 41): one unit of the frame's metal restores Integrity up to the rework cap. No minigame. */
    public static void repair(ServerPlayer player, JewelersBenchBlockEntity bench) {
        ItemStack piece = bench.workpiece();
        JewelryData data = JewelryData.get(piece).orElse(null);
        if (data == null || bench.isLocked()) return;
        float cap = BenchService.repairCap(data.reworks());
        if (data.integrity() >= cap - 0.001F) {
            Feedback.info(player, "repair_not_needed");
            return;
        }
        Ingredient cost = BenchService.repairIngredient(JewelryDefinitions.server(), data.metal());
        if (cost == null) {
            Feedback.fail(player, "repair_no_material");
            return;
        }
        List<ItemStack> taken = Inventories.take(player, cost, 1);
        if (taken == null) {
            Feedback.fail(player, "repair_missing", Inventories.display(cost, 1).getHoverName());
            return;
        }
        float integrity = Math.min(cap, data.integrity() + (float) ServerConfig.get(ServerConfig.REPAIR_AMOUNT));
        JewelryData repaired = data.withIntegrity(integrity);
        ItemStack result = piece.copy();
        repaired.apply(result);
        bench.setWorkpiece(result);
        player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_REPAIR.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        StationEffects.send(player.serverLevel(), bench.getBlockPos(), StationEffects.POLISH, 0xFFE0A0, 0.6F);
        ModAdvancements.award(player, ModAdvancements.REPAIRED);
        MinecraftForge.EVENT_BUS.post(new JewelryReworkedEvent(player, JewelryReworkedEvent.Action.REPAIRED, result.copy()));
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), BenchService.options(player, bench));
    }

    /** A new finish (spec 13): a choice with a cost, applied at once. */
    public static void refinish(ServerPlayer player, JewelersBenchBlockEntity bench, @Nullable ResourceLocation finishId) {
        ItemStack piece = bench.workpiece();
        JewelryData data = JewelryData.get(piece).orElse(null);
        FinishDefinition finish = JewelryDefinitions.server().finish(finishId);
        if (data == null || finish == null || bench.isLocked() || finish.id().equals(data.finish())) return;
        JewelryDesignRecipe design = BenchService.design(player.level(), data.design());
        if (design != null && !design.allowsFinish(finish.id())) {
            Feedback.fail(player, "finish_unavailable");
            return;
        }
        List<ItemStack> taken = Inventories.take(player, finish.cost(), finish.costCount());
        if (taken == null) {
            Feedback.fail(player, "finish_cost", Inventories.display(finish.cost(), finish.costCount()).getHoverName());
            return;
        }
        ItemStack result = piece.copy();
        data.withFinish(finish.id()).apply(result);
        bench.setWorkpiece(result);
        player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_POLISH.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        StationEffects.send(player.serverLevel(), bench.getBlockPos(), StationEffects.POLISH, 0xFFFFFF, 1F);
        MinecraftForge.EVENT_BUS.post(new JewelryReworkedEvent(player, JewelryReworkedEvent.Action.REFINISHED, result.copy()));
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), BenchService.options(player, bench));
    }

    private StationInteractions() {}
}
