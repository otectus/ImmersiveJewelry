package com.otectus.immersivejewelry.client.screen;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.equipment.builtin.JewelrySlotsMenu;
import com.otectus.immersivejewelry.equipment.builtin.SlotType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The built-in jewelry slots (when Curios is not the equipment system), as a vanilla container: the standard panel and
 * slots, dark grey labels, and an outline of each piece in its empty slot, like the armour slots.
 */
public class JewelrySlotsScreen extends AbstractContainerScreen<JewelrySlotsMenu> {

    public JewelrySlotsScreen(JewelrySlotsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        VanillaGui.panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            VanillaGui.slot(g, x, y);
            if (slot.index < SlotType.TOTAL && !slot.hasItem()) {
                ResourceLocation icon = new ResourceLocation(ImmersiveJewelry.MOD_ID,
                        "textures/gui/slot/empty_" + SlotType.forIndex(slot.index).id() + ".png");
                g.blit(icon, x + 1, y + 1, 0, 0, 16, 16, 16, 16);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, VanillaGui.LABEL, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, VanillaGui.LABEL, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && hoveredSlot.index < SlotType.TOTAL && !hoveredSlot.hasItem()) {
            g.renderTooltip(font, Component.translatable("slot.immersive_jewelry." + SlotType.forIndex(hoveredSlot.index).id()), mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }
}
