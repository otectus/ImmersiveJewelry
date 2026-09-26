package com.otectus.immersivejewelry.equipment.builtin;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the built-in slots capability (mod bus). */
@Mod.EventBusSubscriber(modid = ImmersiveJewelry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BuiltinSlotCapability {

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(BuiltinSlots.class);
    }

    private BuiltinSlotCapability() {}
}
