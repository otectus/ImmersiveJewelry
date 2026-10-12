package com.otectus.immersivejewelry.item;

import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.config.CommonConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.jewelry.JewelryTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A ring blank, amulet frame, bracelet blank or circlet frame (spec 5.1, 21). Made by Immersive Smithing, which
 * records metal and metalwork quality, or (fallback) at a crafting table.
 */
public class JewelryBlankItem extends Item {
    private final ResourceLocation form;

    public JewelryBlankItem(ResourceLocation form, Properties properties) {
        super(properties);
        this.form = form;
    }

    public ResourceLocation form() {
        return form;
    }

    @Override
    public Component getName(ItemStack stack) {
        return BlankData.get(stack)
                .<Component>map(d -> JewelryNames.named(getDescriptionId() + ".named", JewelryNames.metal(JewelryDefinitions.current(), d.metal()), super.getName(stack)))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        BlankData.get(stack).ifPresentOrElse(d -> JewelryTooltips.blank(d, JewelryDefinitions.client(), lines),
                () -> lines.add(Component.translatable("tooltip.immersive_jewelry.blank_unworked").withStyle(ChatFormatting.DARK_GRAY)));
    }

    /** A crafting-table blank (fallback recipes) gets the configured fallback metalwork quality when it is taken. */
    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        CompoundTag root = JewelryNbt.root(stack);
        if (root != null && root.getBoolean("Fallback") && !root.contains("SmithingQuality")) {
            BlankData data = BlankData.load(root);
            new BlankData(data.metal(), (float) CommonConfig.get(CommonConfig.FALLBACK_SMITHING_QUALITY), 1F, 0, true, null).apply(stack);
        }
    }
}
