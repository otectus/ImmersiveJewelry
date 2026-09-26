package com.otectus.immersivejewelry.item;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
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

/** A cut stone, ready to set (or to recut at the wheel). */
public class CutGemItem extends Item {

    public CutGemItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(GemData data) {
        ItemStack stack = new ItemStack(ModItems.CUT_GEM.get());
        JewelryNbt.write(stack, data.save());
        return stack;
    }

    public static Optional<GemData> data(ItemStack stack) {
        return GemData.load(JewelryNbt.root(stack)).filter(GemData::isCut);
    }

    @Override
    public Component getName(ItemStack stack) {
        GemData data = data(stack).orElse(null);
        if (data == null) return super.getName(stack);
        JewelryDefinitions defs = JewelryDefinitions.current();
        CutDefinition cut = defs.cut(data.cut());
        Component adjective = cut != null ? cut.adjective() : JewelryNames.cut(defs, data.cut());
        return Component.translatable(getDescriptionId() + ".named", adjective, JewelryNames.gem(defs, data.gem()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        data(stack).ifPresent(d -> JewelryTooltips.gem(d, JewelryDefinitions.client(), lines));
    }
}
