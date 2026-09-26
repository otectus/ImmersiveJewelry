package com.otectus.immersivejewelry.client.screen;

import com.otectus.immersivejewelry.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

/**
 * The Jeweler's Handbook (spec 59): the whole loop explained in the game, one page per step. All text is translation
 * keys {@code handbook.immersive_jewelry.<page>.title/.text}.
 */
public class HandbookScreen extends JewelryScreen {
    private static final int W = 340;
    private static final int H = 246;
    private static final List<Page> PAGES = List.of(
            new Page("welcome", () -> ModItems.JEWELERS_HANDBOOK.get()),
            new Page("stones", () -> Items.DIAMOND),
            new Page("wheel", () -> ModItems.LAPIDARY_WHEEL.get()),
            new Page("cutting", () -> ModItems.CUT_GEM.get()),
            new Page("cuts", () -> ModItems.EMERY_LAP.get()),
            new Page("blanks", () -> ModItems.RING_BLANK.get()),
            new Page("bench", () -> ModItems.JEWELERS_BENCH.get()),
            new Page("setting", () -> ModItems.JEWELERS_PLIERS.get()),
            new Page("finishes", () -> ModItems.POLISHING_COMPOUND.get()),
            new Page("wearing", () -> ModItems.RING.get()),
            new Page("effects", () -> ModItems.AMULET.get()),
            new Page("care", () -> ModItems.FINE_PLIERS.get()),
            new Page("designs", () -> ModItems.DESIGN_SKETCH.get()),
            new Page("jeweler", () -> Items.EMERALD));

    private record Page(String id, Supplier<ItemLike> icon) {}

    private int page;
    private Button prev;
    private Button next;

    public HandbookScreen() {
        super(Component.translatable("item.immersive_jewelry.jewelers_handbook"), W, H);
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new HandbookScreen());
    }

    @Override
    protected void initPanel() {
        prev = addRenderableWidget(JewelryGui.button(12, H - 28, 72, 20, Component.translatable("gui.back"), b -> turn(-1)));
        next = addRenderableWidget(JewelryGui.button(W - 84, H - 28, 72, 20, Component.translatable("screen.immersive_jewelry.next_page"), b -> turn(1)));
        update();
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(PAGES.size() - 1, page + delta));
        update();
    }

    private void update() {
        prev.active = page > 0;
        next.active = page < PAGES.size() - 1;
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        JewelryGui.panel(g, left, top, W, H);
        Page p = PAGES.get(page);
        JewelryGui.heading(g, font, title, left, top, W);
        JewelryGui.well(g, 12, 34, W - 24, H - 70);
        g.renderItem(new ItemStack(p.icon().get()), 22, 42);
        g.drawString(font, JewelryGui.clipped(font, Component.translatable("handbook.immersive_jewelry." + p.id() + ".title"), W - 68), 44, 46, JewelryGui.TEXT_WARM, false);
        JewelryGui.rule(g, 22, 64, W - 44);
        int y = 74;
        for (var line : font.split(Component.translatable("handbook.immersive_jewelry." + p.id() + ".text"), W - 44)) {
            if (y > H - 48) break;
            g.drawString(font, line, 22, y, JewelryGui.TEXT, false);
            y += 11;
        }
        g.drawCenteredString(font, (page + 1) + " / " + PAGES.size(), left + W / 2, top + H - 21, JewelryGui.TEXT_DIM);
        super.renderPanel(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            turn(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) {
            turn(-1);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    protected boolean panelScrolled(double x, double y, double delta) {
        turn(delta < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
