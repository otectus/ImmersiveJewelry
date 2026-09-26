package com.otectus.immersivejewelry.item;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.jewelry.JewelryTooltips;
import com.otectus.immersivejewelry.migration.LegacyJewelcraft;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A finished piece of one form. Everything else (metal, stones, cut, finish, craftsmanship) is item data, so one
 * item covers every combination (spec 21).
 */
public class JewelryItem extends Item {
    private final ResourceLocation form;

    public JewelryItem(ResourceLocation form, Properties properties) {
        super(properties);
        this.form = form;
    }

    public ResourceLocation form() {
        return form;
    }

    @Override
    public Component getName(ItemStack stack) {
        JewelryData data = JewelryData.get(stack).orElse(null);
        if (data == null) return super.getName(stack);
        JewelryDefinitions defs = JewelryDefinitions.current();
        if (data.principal() != null) {
            return Component.translatable(getDescriptionId() + ".named", JewelryNames.gem(defs, data.principal().gem()));
        }
        return Component.translatable(getDescriptionId() + ".plain", JewelryNames.metal(defs, data.metal()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        JewelryData data = JewelryData.get(stack).orElse(null);
        if (data == null) {
            lines.add(Component.translatable("tooltip.immersive_jewelry.unformed").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        JewelryTooltips.jewelry(data, JewelryDefinitions.client(), lines);
    }

    /** Converts Jewelcraft Reforged pieces as soon as their NBT is read (spec 50.3). */
    @Override
    public void verifyTagAfterLoad(CompoundTag tag) {
        LegacyJewelcraft.convertInPlace(this, tag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (JewelryEquipment.equipFromUse(player, hand)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1 && JewelryData.get(stack).isPresent();
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return JewelryEnchanting.enchantability(stack);
    }

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment.category == JewelryEnchanting.CATEGORY || super.canApplyAtEnchantingTable(stack, enchantment);
    }
}
