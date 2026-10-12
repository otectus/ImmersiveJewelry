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
 * The stations already sync what they hold (for their renderers), so the overlays read the client's block entity
 * and need no server data.
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
            if (wheel.isWorking()) tooltip.add(line("in_use").withStyle(ChatFormatting.RED));
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
            ItemStack piece = bench.workpiece();
            if (piece.isEmpty()) {
                tooltip.add(line("bench.empty").withStyle(ChatFormatting.GRAY));
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
            if (bench.isWorking()) {
                tooltip.add(line("bench.progress", percent(bench.progress())).withStyle(ChatFormatting.AQUA));
                tooltip.add(line("in_use").withStyle(ChatFormatting.RED));
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
