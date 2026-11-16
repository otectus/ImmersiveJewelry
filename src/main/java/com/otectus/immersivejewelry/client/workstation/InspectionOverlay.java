package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.config.ClientConfig;
import com.pockyl.diegesis.api.client.ScreenOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The loupe's notes: plain observations about the work in a panel at the side of the screen, never over the work
 * itself. Inspecting changes nothing and costs nothing; a click or Escape returns.
 */
final class InspectionOverlay implements ScreenOverlay {
    private final List<Component> lines;
    private final Runnable onClosed;

    InspectionOverlay(List<Component> lines, Runnable onClosed) {
        this.lines = lines;
        this.onClosed = onClosed;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int screenW = graphics.guiWidth();
        int width = Math.min(200, screenW / 3);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        wrapped.addAll(font.split(Component.translatable("workstation.immersive_jewelry.loupe.title"), width));
        for (Component line : lines) wrapped.addAll(font.split(line, width));
        wrapped.addAll(font.split(Component.translatable("workstation.immersive_jewelry.loupe.close"), width));
        boolean contrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES);
        int x = screenW - width - 12;
        int y = 12;
        int height = wrapped.size() * 10 + 8;
        graphics.fill(x - 6, y - 6, x + width + 6, y + height, contrast ? 0xF0000000 : 0xC0101216);
        graphics.renderOutline(x - 6, y - 6, width + 12, height + 6, contrast ? 0xFFFFFFFF : 0x80E8C46A);
        for (int i = 0; i < wrapped.size(); i++) {
            int color = i == 0 ? 0xFFE8C46A : i == wrapped.size() - 1 ? 0xFF9A9A9A : contrast ? 0xFFFFFFFF : 0xFFE6E6E6;
            graphics.drawString(font, wrapped.get(i), x, y + i * 10, color, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public void onClosed() {
        onClosed.run();
    }
}
