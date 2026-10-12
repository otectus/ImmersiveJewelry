package com.otectus.immersivejewelry.client.screen;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.DescriptionBuilder;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.OpenCutSelectPacket;
import com.otectus.immersivejewelry.network.packet.SelectCutPacket;
import com.otectus.immersivejewelry.network.packet.WheelActionPacket;
import com.otectus.immersivejewelry.quality.StoneTraits;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Choose a cut for the stone on the wheel: each cut's difficulty, the size it leaves, and how strongly it expresses the
 * gem. A vanilla container panel: the stone in a dark readout (its traits keep their tooltip colours), the cuts as
 * vanilla button rows with the creative scroller, and the chosen cut's numbers below.
 */
public class CutSelectScreen extends JewelryScreen {
    private static final int W = 360;
    private static final int H = 224;
    private static final int ROW = 28;
    /** Rows that fit above the details; more cuts (from datapacks) scroll. */
    private static final int VISIBLE = 4;
    private static final int LIST_X = 152;
    private static final int LIST_Y = 20;
    private static final int LIST_W = 196;
    private static final int LIST_H = VISIBLE * ROW - 2 + 2;

    private final OpenCutSelectPacket packet;
    private final ItemStack stoneStack;
    private int selected;
    private int scroll;
    private boolean draggingScroller;
    private final List<Button> toggles = new ArrayList<>();

    public CutSelectScreen(OpenCutSelectPacket packet) {
        super(Component.translatable("screen.immersive_jewelry.cut_select"), W, H);
        this.packet = packet;
        this.stoneStack = packet.recut() ? CutGemItem.create(packet.stone()) : RoughGemItem.create(packet.stone());
        // Default to the stone's current cut when recutting, else the first (easiest) cut.
        for (int i = 0; i < packet.options().size(); i++) {
            if (packet.options().get(i).cut().equals(packet.stone().cut())) selected = i;
        }
        reveal();
    }

    @Override
    protected void initPanel() {
        Component take = Component.translatable("screen.immersive_jewelry.take_stone");
        int takeWidth = Math.max(84, font.width(take) + 14);
        int largerWidth = Math.max(56, font.width(Component.translatable("screen.immersive_jewelry.assist_larger").append(" ✓")) + 14);
        int slowerWidth = Math.max(56, font.width(Component.translatable("screen.immersive_jewelry.assist_slower").append(" ✓")) + 14);
        int beginX = 8 + takeWidth + largerWidth + slowerWidth + 12;
        Button begin = addRenderableWidget(VanillaGui.button(beginX, H - 28, W - 8 - beginX, 20, Component.translatable("screen.immersive_jewelry.begin_cut"), b -> begin()));
        begin.active = !packet.options().isEmpty();
        addRenderableWidget(VanillaGui.button(8, H - 28, takeWidth, 20, take, b -> {
            ModNetwork.CHANNEL.sendToServer(new WheelActionPacket(packet.pos()));
            onClose();
        }));
        toggles.clear();
        toggles.add(addRenderableWidget(VanillaGui.button(12 + takeWidth, H - 28, largerWidth, 20, assistLabel(true), b -> toggle(true))));
        toggles.add(addRenderableWidget(VanillaGui.button(16 + takeWidth + largerWidth, H - 28, slowerWidth, 20, assistLabel(false), b -> toggle(false))));
    }

    private Component assistLabel(boolean larger) {
        boolean on = ClientConfig.get(larger ? ClientConfig.LARGER_TARGETS : ClientConfig.SLOWER_MINIGAMES);
        return Component.translatable(larger ? "screen.immersive_jewelry.assist_larger" : "screen.immersive_jewelry.assist_slower")
                .append(on ? " ✓" : "");
    }

    private void toggle(boolean larger) {
        var value = larger ? ClientConfig.LARGER_TARGETS : ClientConfig.SLOWER_MINIGAMES;
        value.set(!value.get());
        toggles.get(0).setMessage(assistLabel(true));
        toggles.get(1).setMessage(assistLabel(false));
    }

    private void begin() {
        if (packet.options().isEmpty()) return;
        ModNetwork.CHANNEL.sendToServer(new SelectCutPacket(packet.pos(), packet.options().get(selected).cut(),
                ClientConfig.get(ClientConfig.LARGER_TARGETS), ClientConfig.get(ClientConfig.SLOWER_MINIGAMES)));
    }

    private int maxScroll() {
        return Math.max(0, packet.options().size() - VISIBLE);
    }

    /** Row width: the full list, less the scroller's column when the cuts scroll. */
    private int rowWidth() {
        return maxScroll() > 0 ? LIST_W - VanillaGui.SCROLLBAR_WIDTH - 1 : LIST_W;
    }

    private int scrollbarX() {
        return LIST_X + rowWidth() + 2;
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VanillaGui.panel(g, left, top, W, H);
        renderTitle(g, title, W - 16, mouseX, mouseY);
        JewelryDefinitions defs = JewelryDefinitions.client();
        GemData stone = packet.stone();

        // The stone: a dark readout, so its clarity and luster keep their tooltip colours.
        VanillaGui.display(g, 8, 19, 136, LIST_H);
        VanillaGui.slot(g, 14, 25);
        g.renderItem(stoneStack, 15, 26);
        VanillaGui.wrapped(g, font, stoneStack.getHoverName(), 37, 26, 102, VanillaGui.DISPLAY_TEXT, 2);
        g.drawString(font, VanillaGui.clipped(font, Component.translatable("screen.immersive_jewelry.stone_size", StoneTraits.size(stone.size()),
                DescriptionBuilder.trim(stone.size())), 124), 14, 50, VanillaGui.DISPLAY_TEXT_DIM, false);
        g.drawString(font, VanillaGui.clipped(font, StoneTraits.clarity(stone.inclusions()), 124), 14, 62, VanillaGui.DISPLAY_TEXT_DIM, false);
        g.drawString(font, VanillaGui.clipped(font, Component.translatable("tooltip.immersive_jewelry.luster", StoneTraits.luster(stone.naturalQuality())), 124), 14, 74, VanillaGui.DISPLAY_TEXT_DIM, false);
        g.fill(14, 88, 138, 89, 0xFF4A4A4A);
        VanillaGui.wrapped(g, font, Component.translatable("screen.immersive_jewelry.lap." + packet.tier().name().toLowerCase(java.util.Locale.ROOT)),
                14, 95, 124, VanillaGui.DISPLAY_TEXT, 3);
        if (mouseX >= 14 && mouseX < 32 && mouseY >= 25 && mouseY < 43) tooltip(stoneStack);

        // The cuts, as vanilla button rows.
        int rw = rowWidth();
        VanillaGui.inset(g, LIST_X - 1, LIST_Y - 1, rw + 2, LIST_H);
        for (int i = scroll; i < Math.min(packet.options().size(), scroll + VISIBLE); i++) {
            OpenCutSelectPacket.Option o = packet.options().get(i);
            int y = LIST_Y + (i - scroll) * ROW;
            boolean hover = mouseX >= LIST_X && mouseX < LIST_X + rw && mouseY >= y && mouseY < y + ROW - 2;
            boolean current = i == selected;
            VanillaGui.row(g, LIST_X, y, rw, ROW - 2, true, hover || current);
            // The chosen cut is marked with a sign as well as the highlight.
            String marker = current ? "» " : "";
            Component name = JewelryNames.cut(defs, o.cut());
            g.drawString(font, marker + VanillaGui.clipped(font, name, rw - 54 - font.width(marker)), LIST_X + 6, y + 4,
                    current ? VanillaGui.ROW_TEXT_SELECTED : VanillaGui.ROW_TEXT, true);
            g.drawString(font, VanillaGui.clipped(font, Component.translatable("screen.immersive_jewelry.cut_row", o.facets(), DescriptionBuilder.trim(o.size())), rw - 12),
                    LIST_X + 6, y + 15, VanillaGui.ROW_TEXT_DIM, true);
            int pips = Math.max(1, Math.round(o.difficulty() * 5));
            for (int n = 0; n < 5; n++) {
                int px = LIST_X + rw - 41 + n * 7;
                if (n < pips) JewelryGui.diamond(g, px + 2, y + 7, 2, JewelryGui.BRASS_LIGHT);
                else JewelryGui.ring(g, px + 2, y + 7, 2, 0xFF2A2A2A);
            }
        }
        if (maxScroll() > 0) VanillaGui.scrollbar(g, scrollbarX(), LIST_Y - 1, LIST_H, scroll / (float) maxScroll(), true);

        // The chosen cut's numbers and description.
        VanillaGui.rule(g, 8, 138, W - 16);
        if (!packet.options().isEmpty()) {
            OpenCutSelectPacket.Option o = packet.options().get(selected);
            CutDefinition cut = defs.cut(o.cut());
            g.drawString(font, Component.translatable("screen.immersive_jewelry.cut_potency", pct(o.potency())), 8, 146, VanillaGui.LABEL, false);
            g.drawString(font, Component.translatable("screen.immersive_jewelry.cut_stability", pct(o.stability())), 116, 146, VanillaGui.LABEL, false);
            g.drawString(font, Component.translatable("screen.immersive_jewelry.cut_retained", DescriptionBuilder.trim(stone.size()), DescriptionBuilder.trim(o.size())), 228, 146, VanillaGui.LABEL, false);
            if (cut != null) VanillaGui.wrapped(g, font, cut.description(), 8, 159, W - 16, VanillaGui.LABEL_DIM, 3);
        }
        super.renderPanel(g, mouseX, mouseY, partialTick);
    }

    private static String pct(float modifier) {
        int p = Math.round((modifier - 1F) * 100F);
        return (p >= 0 ? "+" : "") + p + "%";
    }

    @Override
    protected boolean panelClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0 && VanillaGui.onScrollbar(mouseX, mouseY, scrollbarX(), LIST_Y - 1, LIST_H)) {
            draggingScroller = true;
            scroll = VanillaGui.scrollAt(mouseY, LIST_Y - 1, LIST_H, maxScroll());
            return true;
        }
        int rw = rowWidth();
        for (int i = scroll; i < Math.min(packet.options().size(), scroll + VISIBLE); i++) {
            int y = LIST_Y + (i - scroll) * ROW;
            if (mouseX >= LIST_X && mouseX < LIST_X + rw && mouseY >= y && mouseY < y + ROW - 2) {
                if (button != 0) return false;
                selected = i;
                return true;
            }
        }
        return super.panelClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean panelDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingScroller) {
            scroll = VanillaGui.scrollAt(mouseY, LIST_Y - 1, LIST_H, maxScroll());
            return true;
        }
        return super.panelDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    protected boolean panelReleased(double mouseX, double mouseY, int button) {
        draggingScroller = false;
        return super.panelReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean panelScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
        return true;
    }

    private void reveal() {
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + VISIBLE) scroll = selected - VISIBLE + 1;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key < org.lwjgl.glfw.GLFW.GLFW_KEY_1 + Math.min(9, packet.options().size())) {
            selected = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
            reveal();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            if (!packet.options().isEmpty()) {
                selected = Math.floorMod(selected + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? -1 : 1), packet.options().size());
                reveal();
            }
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            begin();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
