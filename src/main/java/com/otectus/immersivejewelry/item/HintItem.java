package com.otectus.immersivejewelry.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A plain item with a one-line usage hint. */
public class HintItem extends Item {
    private final String hint;

    public HintItem(Properties properties, String hint) {
        super(properties);
        this.hint = hint;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.immersive_jewelry." + hint).withStyle(ChatFormatting.GRAY));
    }
}
