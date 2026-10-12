package com.otectus.immersivejewelry.client.screen;

import com.otectus.immersivejewelry.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * The Jeweler's Handbook (spec 59): the whole loop explained in the game, one chapter per step, as a vanilla book:
 * Minecraft's own book page with black ink, "Page x of y", the vanilla page buttons and a Done button. Each chapter
 * opens with its icon and title; longer text continues on the next pages. All text is translation keys
 * {@code handbook.immersive_jewelry.<page>.title/.text}.
 */
public class HandbookScreen extends JewelryScreen {
    private static final int W = 200;
    private static final int H = 216;
    private static final int BOOK_LEFT = (W - VanillaGui.BOOK_WIDTH) / 2;
    private static final int BOOK_TOP = 2;
    private static final int LINE_HEIGHT = 9;
    private static final int TEXT_WIDTH = 114;
    private static final int TEXT_LINES = 14;
    /** Lines a chapter's first page gives to its icon and title, and the gap below them. */
    private static final int HEADER_LINES = 3;
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
    private record Sheet(Page page, boolean opening, List<FormattedCharSequence> lines) {}

    private int page;
    private final List<Sheet> sheets = new ArrayList<>();
    private PageButton prev;
    private PageButton next;

    public HandbookScreen() {
        super(Component.translatable("item.immersive_jewelry.jewelers_handbook"), W, H);
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new HandbookScreen());
    }

    @Override
    protected void initPanel() {
        sheets.clear();
        for (Page entry : PAGES) {
            var lines = font.split(Component.translatable("handbook.immersive_jewelry." + entry.id() + ".text"), TEXT_WIDTH);
            // Translations and resource packs may need more room than the original English page: the text flows on.
            int start = 0;
            boolean opening = true;
            do {
                int end = Math.min(lines.size(), start + (opening ? TEXT_LINES - HEADER_LINES : TEXT_LINES));
                sheets.add(new Sheet(entry, opening, List.copyOf(lines.subList(start, end))));
                start = end;
                opening = false;
            } while (start < lines.size());
        }
        page = Math.min(page, sheets.size() - 1);
        addRenderableWidget(VanillaGui.button(0, BOOK_TOP + VanillaGui.BOOK_HEIGHT + 2, W, 20, CommonComponents.GUI_DONE, b -> onClose()));
        prev = addRenderableWidget(new PageButton(BOOK_LEFT + 43, BOOK_TOP + 159, false, b -> turn(-1), true));
        next = addRenderableWidget(new PageButton(BOOK_LEFT + 116, BOOK_TOP + 159, true, b -> turn(1), true));
        update();
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(sheets.size() - 1, page + delta));
        update();
    }

    private void update() {
        // As in a vanilla book, a page button shows only where there is a page to turn to.
        prev.visible = prev.active = page > 0;
        next.visible = next.active = page < sheets.size() - 1;
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VanillaGui.bookPage(g, BOOK_LEFT, BOOK_TOP);
        Sheet sheet = sheets.get(page);
        Page p = sheet.page();
        Component indicator = Component.translatable("book.pageIndicator", page + 1, sheets.size());
        g.drawString(font, indicator, BOOK_LEFT + 148 - font.width(indicator), BOOK_TOP + 16, VanillaGui.INK, false);
        int y = BOOK_TOP + 30;
        if (sheet.opening()) {
            g.renderItem(new ItemStack(p.icon().get()), BOOK_LEFT + 36, y);
            Component chapter = Component.translatable("handbook.immersive_jewelry." + p.id() + ".title").withStyle(ChatFormatting.BOLD);
            List<FormattedCharSequence> titleLines = font.split(chapter, TEXT_WIDTH - 20);
            int titleY = titleLines.size() > 1 ? y : y + 4;
            for (int i = 0; i < Math.min(2, titleLines.size()); i++) {
                g.drawString(font, titleLines.get(i), BOOK_LEFT + 56, titleY + i * LINE_HEIGHT, VanillaGui.INK, false);
            }
            y += HEADER_LINES * LINE_HEIGHT;
        }
        for (var line : sheet.lines()) {
            g.drawString(font, line, BOOK_LEFT + 36, y, VanillaGui.INK, false);
            y += LINE_HEIGHT;
        }
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
