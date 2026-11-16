package com.otectus.immersivejewelry.client.guide;

import com.mojang.blaze3d.platform.InputConstants;
import com.otectus.immersivejewelry.client.workstation.WorkstationClient;
import com.otectus.immersivejewelry.client.workstation.WorkstationKeys;
import com.pockyl.diegesis.api.client.DiegesisClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Client wiring of the Jeweler's Handbook: the handbook key in the world, over inventory items and at the stations, and the
 * navigator's tick. Reached only from {@code ClientSetup}.
 */
public final class Handbook {
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(Handbook::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(Handbook::onScreenKey);
        MinecraftForge.EVENT_BUS.addListener(Handbook::onLogout);
    }

    private static boolean held() {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key key = WorkstationKeys.HANDBOOK.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // In the world with no screen: the book, at the page last read.
        while (WorkstationKeys.HANDBOOK.consumeClick()) {
            if (mc.screen == null && mc.player != null && !HandbookNavigator.helpPending()) HandbookNavigator.openBook();
        }
        HandbookNavigator.tick(held());
    }

    private static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!WorkstationKeys.HANDBOOK.matches(event.getKeyCode(), event.getScanCode())) return;
        if (DiegesisClient.isStationScreen(event.getScreen())) {
            WorkstationClient.focusedStation().ifPresent(station -> {
                HandbookNavigator.help(station, WorkstationClient::releaseHeld, true, false);
                event.setCanceled(true);
            });
            return;
        }
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen) {
            Slot slot = screen.getSlotUnderMouse();
            HelpTopics.Topic topic = slot != null && slot.hasItem() ? HelpTopics.item(slot.getItem()) : null;
            if (topic != null) {
                HandbookNavigator.open(topic, false);
                event.setCanceled(true);
            }
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HandbookNavigator.reset();
    }

    private Handbook() {}
}
