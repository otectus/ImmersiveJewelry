package com.otectus.immersivejewelry.registry;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.equipment.builtin.JewelrySlotsMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ImmersiveJewelry.MOD_ID);

    /** The built-in jewelry slots, used only when Curios is not the equipment system. */
    public static final RegistryObject<MenuType<JewelrySlotsMenu>> JEWELRY_SLOTS = MENUS.register("jewelry_slots",
            () -> IForgeMenuType.create((id, inventory, buf) -> new JewelrySlotsMenu(id, inventory)));

    private ModMenus() {}
}
