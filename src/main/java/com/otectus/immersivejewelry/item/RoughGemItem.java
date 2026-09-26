package com.otectus.immersivejewelry.item;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.jewelry.JewelryTooltips;
import com.otectus.immersivejewelry.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** An appraised rough stone. Its natural character is fixed the moment it is appraised, so it can never be re-rolled. */
public class RoughGemItem extends Item {

    public RoughGemItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(GemData data) {
        ItemStack stack = new ItemStack(ModItems.ROUGH_GEM.get());
        JewelryNbt.write(stack, data.save());
        return stack;
    }

    public static Optional<GemData> data(ItemStack stack) {
        return GemData.load(JewelryNbt.root(stack));
    }

    @Override
    public Component getName(ItemStack stack) {
        return data(stack).<Component>map(d -> Component.translatable(getDescriptionId() + ".named",
                JewelryNames.gem(JewelryDefinitions.current(), d.gem()))).orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        data(stack).ifPresent(d -> JewelryTooltips.gem(d, JewelryDefinitions.client(), lines));
    }
}
