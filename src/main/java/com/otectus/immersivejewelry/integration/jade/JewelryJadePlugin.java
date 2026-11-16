package com.otectus.immersivejewelry.integration.jade;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.block.JewelersBenchBlock;
import com.otectus.immersivejewelry.block.LapidaryWheelBlock;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.DescriptionBuilder;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.quality.CutGrade;
import com.otectus.immersivejewelry.quality.MetalworkGrade;
import com.otectus.immersivejewelry.quality.StoneTraits;
import com.otectus.immersivejewelry.quality.WorkmanshipGrade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade overlays for the two stations. Discovered by Jade through the annotation, so nothing here loads without it.
 * The stations already sync what they hold and the state of their work (for their scenes), so the overlays read the
 * client's block entity and need no server data. Short lines: what is there, the stage, who works it, what is missing.
 * While the local player works a station its scene is open and Jade's overlay is not drawn over the work.
 */
@WailaPlugin
public class JewelryJadePlugin implements IWailaPlugin {
    static final ResourceLocation WHEEL = ImmersiveJewelry.id("wheel");
    static final ResourceLocation BENCH = ImmersiveJewelry.id("bench");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(WheelProvider.INSTANCE, LapidaryWheelBlock.class);
        registration.registerBlockComponent(BenchProvider.INSTANCE, JewelersBenchBlock.class);
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.translatable("jade.immersive_jewelry." + key, args);
    }

    enum WheelProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof LapidaryWheelBlockEntity wheel)) return;
            JewelryDefinitions defs = JewelryDefinitions.client();
            ItemStack stone = wheel.stone();
            GemData cut = CutGemItem.data(stone).orElse(null);
            GemData rough = cut == null ? RoughGemItem.data(stone).orElse(null) : null;
            if (cut != null) {
                tooltip.add(line("stone", JewelryNames.gem(defs, cut.gem()), Component.translatable("tooltip.immersive_jewelry.cut",
                        CutGrade.of(cut.cutQuality()).displayName(), JewelryNames.cut(defs, cut.cut()))).withStyle(ChatFormatting.GOLD));
            } else if (rough != null) {
                tooltip.add(line("rough", JewelryNames.gem(defs, rough.gem()), StoneTraits.size(rough.size()), DescriptionBuilder.trim(rough.size()))
                        .withStyle(ChatFormatting.GOLD));
            } else {
                tooltip.add(line("wheel.empty").withStyle(ChatFormatting.GRAY));
            }
            ItemStack lap = wheel.lap();
            if (lap.isEmpty()) {
                tooltip.add(line("wheel.no_lap").withStyle(ChatFormatting.GRAY));
            } else {
                MutableComponent text = line("wheel.lap", lap.getHoverName());
                if (lap.isDamageableItem()) text.append(" ").append(line("wear", percent(1F - lap.getDamageValue() / (float) lap.getMaxDamage())));
                tooltip.add(text.withStyle(ChatFormatting.GRAY));
            }
            wheel.work().ifPresent(work -> {
                tooltip.add(line("wheel.cut", JewelryNames.cut(defs, work.cut()), percent(work.sim().overallProgress())).withStyle(ChatFormatting.AQUA));
                if (work.sim().ready()) tooltip.add(line("wheel.ready").withStyle(ChatFormatting.GREEN));
                if (work.sim().chips() > 0) tooltip.add(line("wheel.chips", work.sim().chips()).withStyle(ChatFormatting.RED));
                if (defs.cut(work.cut()) == null) tooltip.add(line("work_unavailable").withStyle(ChatFormatting.RED));
            });
            if (wheel.operatorName() != null) tooltip.add(line("operator", wheel.operatorName()).withStyle(ChatFormatting.RED));
        }

        @Override
        public ResourceLocation getUid() {
            return WHEEL;
        }
    }

    enum BenchProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof JewelersBenchBlockEntity bench)) return;
            ItemStack piece = bench.clamp();
            if (piece.isEmpty()) {
                tooltip.add(line("bench.empty").withStyle(ChatFormatting.GRAY));
                stock(tooltip, bench);
                return;
            }
            JewelryData jewelry = JewelryData.get(piece).orElse(null);
            BlankData blank = jewelry == null ? BlankData.get(piece).orElse(null) : null;
            MutableComponent name = piece.getHoverName().copy().withStyle(ChatFormatting.GOLD);
            if (jewelry != null) {
                tooltip.add(line("bench.piece", name, WorkmanshipGrade.of(jewelry.workmanship()).displayName()));
                if (jewelry.integrity() < 0.999F) tooltip.add(line("integrity", percent(jewelry.integrity())).withStyle(ChatFormatting.GRAY));
            } else if (blank != null) {
                tooltip.add(line("bench.blank", name, MetalworkGrade.of(blank.smithingQuality()).displayName()));
            } else {
                tooltip.add(name);
            }
            var work = bench.work();
            if (work != null) {
                Component stage = line("stage." + work.kind().id() + "." + work.stage().name().toLowerCase(java.util.Locale.ROOT));
                float progress = work.surface() != null ? work.surface().progress() : work.setting() != null ? secured(work.setting()) : 0F;
                tooltip.add(line("bench.work", stage, percent(progress)).withStyle(ChatFormatting.AQUA));
                if (work.stage() == com.otectus.immersivejewelry.workstation.order.BenchWork.Stage.PLAN && work.setting() != null
                        && bench.seatedCount() < work.setting().params().sockets()) {
                    tooltip.add(line("bench.needs_stones", work.setting().params().sockets() - bench.seatedCount()).withStyle(ChatFormatting.GOLD));
                }
            } else if (bench.hasUnreadableWork()) {
                tooltip.add(line("work_unavailable").withStyle(ChatFormatting.RED));
            }
            stock(tooltip, bench);
            if (bench.operatorName() != null) tooltip.add(line("operator", bench.operatorName()).withStyle(ChatFormatting.RED));
        }

        private static float secured(com.otectus.immersivejewelry.workstation.sim.SettingSim sim) {
            int done = 0;
            for (int s = 0; s < sim.params().sockets(); s++) if (sim.secured(s)) done++;
            return done / (float) sim.params().sockets();
        }

        private static void stock(ITooltip tooltip, JewelersBenchBlockEntity bench) {
            int stones = 0;
            for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) stones += bench.tray(i).getCount();
            if (stones > 0) tooltip.add(line("bench.tray", stones).withStyle(ChatFormatting.GRAY));
            if (!bench.dock().isEmpty()) tooltip.add(line("bench.dock", bench.dock().getHoverName()).withStyle(ChatFormatting.GRAY));
            int supplies = 0;
            for (int i = 0; i < JewelersBenchBlockEntity.SUPPLIES; i++) supplies += bench.supply(i).getCount();
            if (supplies > 0) tooltip.add(line("bench.supplies", supplies).withStyle(ChatFormatting.GRAY));
            // How many stacks the drawer keeps, never what they are: a shut drawer's contents stay out of sight.
            int staged = 0;
            for (int i = 0; i < JewelersBenchBlockEntity.STAGING; i++) if (!bench.staged(i).isEmpty()) staged++;
            if (staged > 0 || bench.drawerOpen()) {
                tooltip.add(line(bench.drawerOpen() ? "bench.drawer_open" : "bench.drawer_shut", staged, JewelersBenchBlockEntity.STAGING)
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return BENCH;
        }
    }

    private static int percent(float value) {
        return Math.round(Math.max(0F, Math.min(1F, value)) * 100F);
    }
}
