package com.otectus.immersivejewelry.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A fixed pixel layout fitted to the viewport, with the same transform for drawing and input. */
public abstract class JewelryScreen extends Screen {
    private final int panelWidth;
    private final int panelHeight;
    protected final int left = 0;
    protected final int top = 0;
    private float scale = 1;
    private float originX;
    private float originY;

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
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1);
        renderPanel(g, (int) Math.floor(localX(mouseX)), (int) Math.floor(localY(mouseY)), partialTick);
        g.pose().popPose();
    }

    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
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
        return super.mouseDragged(localX(x), localY(y), button, dx / scale, dy / scale);
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(localX(x), localY(y));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
