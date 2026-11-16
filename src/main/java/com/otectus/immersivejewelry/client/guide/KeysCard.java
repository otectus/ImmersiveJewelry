package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.client.workstation.WorkstationKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import vazkii.patchouli.api.IComponentRenderContext;

/** The station keys as they are bound now (rebinding shows here at once), with what each does. */
public class KeysCard extends GuideCard {
    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        int top = header(g, context, Component.translatable("book.immersive_jewelry.card.keys"), y);
        for (KeyMapping key : WorkstationKeys.HELP_ORDER) {
            Component row = Component.literal("[").append(key.getTranslatedKeyMessage()).append(Component.literal("] "))
                    .append(Component.translatable(key.getName()).withStyle(s -> s.withColor(MUTED & 0xFFFFFF)));
            top = line(g, context, row, top);
            if (top > y + PAGE_HEIGHT - 9) break;
        }
    }
}
