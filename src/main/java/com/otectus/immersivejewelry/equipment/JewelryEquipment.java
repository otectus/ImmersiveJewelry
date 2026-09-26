package com.otectus.immersivejewelry.equipment;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.integration.IJewelryEquipmentProvider;
import com.otectus.immersivejewelry.config.CommonConfig;
import com.otectus.immersivejewelry.equipment.builtin.BuiltinSlots;
import com.otectus.immersivejewelry.integration.Integrations;
import com.otectus.immersivejewelry.item.JewelryItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where jewelry is worn (spec 20). Curios when it is the configured system and installed, otherwise the built-in
 * slots; any extra providers registered through the API are asked too.
 */
public final class JewelryEquipment {
    private static boolean builtin;
    private static boolean curios;

    public static void init() {
        CommonConfig.EquipmentSystem system = CommonConfig.equipmentSystem();
        boolean curiosLoaded = ModList.get().isLoaded("curios");
        curios = curiosLoaded && system != CommonConfig.EquipmentSystem.BUILTIN;
        builtin = system == CommonConfig.EquipmentSystem.BUILTIN || (system == CommonConfig.EquipmentSystem.AUTO && !curiosLoaded);
        if (builtin) ImmersiveJewelryAPI.registerEquipmentProvider(BuiltinSlots.PROVIDER);
        if (curios) Integrations.enableCurios();
        if (system == CommonConfig.EquipmentSystem.CURIOS && !curiosLoaded) {
            ImmersiveJewelry.LOGGER.warn("equipmentSystem is CURIOS but Curios is not installed: jewelry cannot be worn");
        }
        ImmersiveJewelry.LOGGER.info("Jewelry is worn in {}", curios ? "Curios slots" : builtin ? "the built-in jewelry slots" : "no slots");
    }

    public static boolean builtinActive() {
        return builtin;
    }

    public static boolean curiosActive() {
        return curios;
    }

    /** Every worn jewelry stack by stable slot key, in provider and slot order. Live stacks: do not keep them. */
    public static Map<String, ItemStack> worn(Player player) {
        Map<String, ItemStack> out = new LinkedHashMap<>();
        for (IJewelryEquipmentProvider provider : ImmersiveJewelryAPI.equipmentProviders()) {
            provider.collect(player, (key, stack) -> {
                if (!stack.isEmpty() && stack.getItem() instanceof JewelryItem) out.putIfAbsent(key, stack);
            });
        }
        return out;
    }

    /** Right-click equipping into the built-in slots (Curios handles its own). */
    public static boolean equipFromUse(Player player, InteractionHand hand) {
        return builtin && BuiltinSlots.equipFromUse(player, hand);
    }

    private JewelryEquipment() {}
}
