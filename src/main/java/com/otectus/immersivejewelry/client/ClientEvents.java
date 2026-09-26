package com.otectus.immersivejewelry.client;

import com.otectus.immersivejewelry.client.screen.JewelryGui;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.effect.ClientProfile;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.OpenJewelrySlotsPacket;
import com.otectus.immersivejewelry.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Client-only Forge-bus handlers: stone hints in tooltips, the jewelry-slots button and key, and logout cleanup. */
public final class ClientEvents {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!ClientConfig.get(ClientConfig.SHOW_GEM_SOURCE_HINTS)) return;
        ItemStack stack = event.getItemStack();
        if (stack.is(ModItems.ROUGH_GEM.get()) || stack.is(ModItems.CUT_GEM.get())) return;
        GemDefinition gem = JewelryDefinitions.client().gemForSource(stack);
        if (gem != null) event.getToolTip().add(Component.translatable("tooltip.immersive_jewelry.gem_source", gem.name()).withStyle(ChatFormatting.DARK_AQUA));
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !JewelryEquipment.builtinActive()) return;
        if (!ClientConfig.get(ClientConfig.SHOW_INVENTORY_BUTTON)) return;
        event.addListener(new SlotsButton(screen));
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !JewelryEquipment.builtinActive()) return;
        while (ClientSetup.OPEN_SLOTS.consumeClick()) ModNetwork.CHANNEL.sendToServer(new OpenJewelrySlotsPacket());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        JewelryDefinitions.setClient(JewelryDefinitions.EMPTY);
        ClientProfile.clear();
        JewelryColors.clearSamples();
    }

    /** A small ring button beside the player preview that follows the recipe book's shift. */
    private static final class SlotsButton extends Button {
        private final InventoryScreen screen;

        SlotsButton(InventoryScreen screen) {
            super(screen.getGuiLeft() + 64, screen.getGuiTop() + 8, 10, 10, Component.translatable("key.immersive_jewelry.jewelry_slots"),
                    b -> ModNetwork.CHANNEL.sendToServer(new OpenJewelrySlotsPacket()), DEFAULT_NARRATION);
            this.screen = screen;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            setX(screen.getGuiLeft() + 64);
            setY(screen.getGuiTop() + 8);
            boolean hot = isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + 10, getY() + 10, hot ? JewelryGui.BRASS_LIGHT : JewelryGui.BRASS_DARK);
            g.fill(getX() + 1, getY() + 1, getX() + 9, getY() + 9, hot ? JewelryGui.ROW_HOVER : JewelryGui.SURFACE);
            JewelryGui.ring(g, getX() + 5F, getY() + 5.5F, 2.6F, JewelryGui.BRASS_LIGHT);
            JewelryGui.diamond(g, getX() + 5F, getY() + 2.5F, 1.5F, 0xFFE0405A);
            if (hot) g.renderTooltip(Minecraft.getInstance().font, getMessage(), mouseX, mouseY);
        }
    }

    private ClientEvents() {}
}
