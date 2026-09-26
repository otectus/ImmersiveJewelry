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
 * Jeweler's Pliers: the bench tool. Precision widens every pressure window when setting or removing stones (tool
 * quality affects tolerance rather than speed, spec 7.1). One point of wear per finished job.
 */
public class PliersItem extends Item {
    private final float precision;

    public PliersItem(float precision, Properties properties) {
        super(properties);
        this.precision = precision;
    }

    public float precision() {
        return precision;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(net.minecraft.world.item.Items.IRON_INGOT);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.immersive_jewelry.pliers").withStyle(ChatFormatting.GRAY));
        if (precision > 1F) {
            lines.add(Component.translatable("tooltip.immersive_jewelry.pliers.precision", Math.round((precision - 1F) * 100))
                    .withStyle(ChatFormatting.BLUE));
        }
    }
}
