package com.otectus.immersivejewelry.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An abrasive lap for the Lapidary Wheel (spec 7.2). Without one the wheel grinds on its plain sandstone. A lap wears
 * one point per finished cut.
 */
public class LapItem extends Item {
    /**
     * @param speed     grinding rate
     * @param precision widens facet windows and contact regions
     * @param stability damps the contact point's wobble
     * @param cooling   how fast heat bleeds off between passes
     */
    public enum Tier {
        SANDSTONE(1.0F, 1.0F, 1.0F, 1.0F, 0x9D8B6A),
        EMERY(1.2F, 1.12F, 1.15F, 1.1F, 0x5A5E66),
        DIAMOND(1.35F, 1.28F, 1.35F, 1.3F, 0x9FE2E6);

        public final float speed;
        public final float precision;
        public final float stability;
        public final float cooling;
        public final int color;

        Tier(float speed, float precision, float stability, float cooling, int color) {
            this.speed = speed;
            this.precision = precision;
            this.stability = stability;
            this.cooling = cooling;
            this.color = color;
        }
    }

    private final Tier tier;

    public LapItem(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.immersive_jewelry.lap").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.immersive_jewelry.lap.stats", pct(tier.speed), pct(tier.precision), pct(tier.stability))
                .withStyle(ChatFormatting.BLUE));
    }

    private static int pct(float value) {
        return Math.round((value - 1F) * 100F);
    }
}
