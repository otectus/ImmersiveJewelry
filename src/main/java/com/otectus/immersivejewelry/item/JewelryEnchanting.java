package com.otectus.immersivejewelry.item;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Enchanting support (spec 48-49). Finished jewelry has a stack-sensitive enchantability from its form, metal and
 * finish. Mods that add jewelry enchantments target {@link #CATEGORY}; other enchantments apply through their own
 * standard {@code canEnchant} checks, with no whitelist.
 */
public final class JewelryEnchanting {
    public static final EnchantmentCategory CATEGORY = EnchantmentCategory.create("IMMERSIVE_JEWELRY", item -> item instanceof JewelryItem);

    public static int enchantability(ItemStack stack) {
        JewelryData data = JewelryData.get(stack).orElse(null);
        if (data == null) return 1;
        JewelryDefinitions defs = JewelryDefinitions.current();
        FormDefinition form = defs.form(data.form());
        FinishDefinition finish = defs.finish(data.finish());
        float value = (form != null ? form.enchantability() : 12) * defs.metal(data.metal()).enchantability()
                * (finish != null ? finish.enchantability() : 1F);
        return Math.max(1, Math.round(value));
    }

    private JewelryEnchanting() {}
}
