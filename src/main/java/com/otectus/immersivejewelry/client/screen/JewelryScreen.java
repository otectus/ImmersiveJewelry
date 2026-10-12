package com.otectus.immersivejewelry.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A fixed pixel layout fitted to the viewport, with the same transform for drawing and input. Tooltips are collected
 * while the panel draws and drawn afterwards in real screen space at normal size, so they stay legible and on screen
 * however the panel is fitted. Widget tooltips take vanilla's own deferred path.
 */
public abstract class JewelryScreen extends Screen {
    private final int panelWidth;
    private final int panelHeight;
    protected final int left = 0;
    protected final int top = 0;
    private float scale = 1;
    private float originX;
    private float originY;
    private ItemStack pendingItemTooltip = ItemStack.EMPTY;
    private List<FormattedCharSequence> pendingTooltip;

    protected JewelryScreen(Component title, int panelWidth, int panelHeight) {
        super(title);
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
    }

    @Override
    protected final void init() {
        scale = Math.min(1F, Math.min((width - 12F) / panelWidth, (height - 12F) / panelHeight));
        scale = Math.max(0.1F, scale);
        originX = (width - panelWidth * scale) / 2F;
        originY = (height - panelHeight * scale) / 2F;
        initPanel();
    }

    protected abstract void initPanel();

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        pendingItemTooltip = ItemStack.EMPTY;
        pendingTooltip = null;
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1);
        renderPanel(g, (int) Math.floor(localX(mouseX)), (int) Math.floor(localY(mouseY)), partialTick);
        g.pose().popPose();
        if (!pendingItemTooltip.isEmpty()) g.renderTooltip(font, pendingItemTooltip, mouseX, mouseY);
        else if (pendingTooltip != null && !pendingTooltip.isEmpty()) g.renderTooltip(font, pendingTooltip, mouseX, mouseY);
    }

    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** The screen's title in the panel's top-left corner, as on a vanilla container; clipped text shows in full on hover. */
    protected void renderTitle(GuiGraphics g, Component text, int width, int mouseX, int mouseY) {
        g.drawString(font, VanillaGui.clipped(font, text, width), left + 8, top + 7, VanillaGui.LABEL, false);
        if (VanillaGui.isClipped(font, text, width) && mouseX >= left + 8 && mouseX < left + 8 + width
                && mouseY >= top + 6 && mouseY < top + 16) tooltip(text);
    }

    /** Shows the item's own tooltip (with every tooltip mod's additions) after the panel is drawn. */
    protected void tooltip(ItemStack stack) {
        pendingItemTooltip = stack;
        pendingTooltip = null;
    }

    /** Shows text lines, wrapped to a readable width, after the panel is drawn. */
    protected void tooltip(List<Component> lines) {
        int wrap = Math.max(120, Math.min(240, width - 24));
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) out.addAll(font.split(line, wrap));
        pendingTooltip = out;
        pendingItemTooltip = ItemStack.EMPTY;
    }

    protected void tooltip(Component line) {
        tooltip(List.of(line));
    }

    private double localX(double x) { return (x - originX) / scale; }
    private double localY(double y) { return (y - originY) / scale; }

    @Override
    public final boolean mouseClicked(double x, double y, int button) {
        return panelClicked(localX(x), localY(y), button);
    }

    protected boolean panelClicked(double x, double y, int button) {
        return super.mouseClicked(x, y, button);
    }

    @Override
    public final boolean mouseReleased(double x, double y, int button) {
        return panelReleased(localX(x), localY(y), button);
    }

    protected boolean panelReleased(double x, double y, int button) {
        return super.mouseReleased(x, y, button);
    }

    @Override
    public final boolean mouseScrolled(double x, double y, double delta) {
        return panelScrolled(localX(x), localY(y), delta);
    }

    protected boolean panelScrolled(double x, double y, double delta) {
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return panelDragged(localX(x), localY(y), button, dx / scale, dy / scale);
    }

    protected boolean panelDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(localX(x), localY(y));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
