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

/** Choose a cut for the stone on the wheel: each cut's difficulty, the size it leaves, and how strongly it expresses the gem. */
public class CutSelectScreen extends JewelryScreen {
    private static final int W = 360;
    private static final int H = 252;
    private static final int ROW = 28;
    /** Rows that fit above the details; more cuts (from datapacks) scroll. */
    private static final int VISIBLE = 4;

    private final OpenCutSelectPacket packet;
    private final ItemStack stoneStack;
    private int selected;
    private int scroll;
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
        Button begin = addRenderableWidget(JewelryGui.button(226, H - 28, 122, 20, Component.translatable("screen.immersive_jewelry.begin_cut"), b -> begin()));
        begin.active = !packet.options().isEmpty();
        addRenderableWidget(JewelryGui.button(12, H - 28, 84, 20, Component.translatable("screen.immersive_jewelry.take_stone"), b -> {
            ModNetwork.CHANNEL.sendToServer(new WheelActionPacket(packet.pos()));
            onClose();
        }));
        toggles.clear();
        toggles.add(addRenderableWidget(JewelryGui.button(102, H - 28, 56, 20, assistLabel(true), b -> toggle(true))));
        toggles.add(addRenderableWidget(JewelryGui.button(164, H - 28, 56, 20, assistLabel(false), b -> toggle(false))));
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

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        JewelryGui.panel(g, left, top, W, H);
        JewelryGui.heading(g, font, title, left, top, W);
        JewelryDefinitions defs = JewelryDefinitions.client();
        GemData stone = packet.stone();
        JewelryGui.well(g, 12, 34, 132, 118);
        JewelryGui.slot(g, 19, 43);
        g.renderItem(stoneStack, 20, 44);
        JewelryGui.drawWrapped(g, font, stoneStack.getHoverName(), 44, 44, 94, JewelryGui.TEXT, 2);
        g.drawString(font, StoneTraits.size(stone.size()).append(" · " + DescriptionBuilder.trim(stone.size()) + " ct"), 20, 72, JewelryGui.TEXT_DIM, false);
        g.drawString(font, JewelryGui.clipped(font, StoneTraits.clarity(stone.inclusions()), 116), 20, 86, JewelryGui.TEXT_DIM, false);
        g.drawString(font, JewelryGui.clipped(font, Component.translatable("tooltip.immersive_jewelry.luster", StoneTraits.luster(stone.naturalQuality())), 116), 20, 100, JewelryGui.TEXT_DIM, false);
        JewelryGui.rule(g, 20, 115, 116);
        JewelryGui.drawWrapped(g, font, Component.translatable("screen.immersive_jewelry.lap." + packet.tier().name().toLowerCase(java.util.Locale.ROOT)),
                20, 124, 116, JewelryGui.TEXT_WARM, 2);

        int listX = 152, listY = 36;
        JewelryGui.well(g, listX - 1, 34, 198, 118);
        for (int i = scroll; i < Math.min(packet.options().size(), scroll + VISIBLE); i++) {
            OpenCutSelectPacket.Option o = packet.options().get(i);
            int y = listY + (i - scroll) * ROW;
            boolean hover = mouseX >= listX && mouseX < listX + 196 && mouseY >= y && mouseY < y + ROW - 2;
            JewelryGui.row(g, listX, y, 196, ROW - 2, i == selected, hover);
            g.drawString(font, JewelryGui.clipped(font, JewelryNames.cut(defs, o.cut()), 142), listX + 8, y + 4, JewelryGui.TEXT, false);
            g.drawString(font, JewelryGui.clipped(font, Component.translatable("screen.immersive_jewelry.cut_row", o.facets(), DescriptionBuilder.trim(o.size())), 180),
                    listX + 8, y + 15, JewelryGui.TEXT_DIM, false);
            int pips = Math.max(1, Math.round(o.difficulty() * 5));
            for (int n = 0; n < 5; n++) {
                int px = listX + 155 + n * 7;
                if (n < pips) JewelryGui.diamond(g, px + 2, y + 7, 2, JewelryGui.BRASS_LIGHT);
                else JewelryGui.ring(g, px + 2, y + 7, 2, JewelryGui.EDGE_LIGHT);
            }
        }
        if (packet.options().size() > VISIBLE) {
            int track = VISIBLE * ROW - 2;
            int thumb = Math.max(8, track * VISIBLE / packet.options().size());
            int ty = listY + (track - thumb) * scroll / (packet.options().size() - VISIBLE);
            g.fill(346, listY, 348, listY + track, JewelryGui.EDGE);
            g.fill(346, ty, 348, ty + thumb, JewelryGui.BRASS);
        }
        JewelryGui.well(g, 12, 160, W - 24, 56);
        if (!packet.options().isEmpty()) {
            OpenCutSelectPacket.Option o = packet.options().get(selected);
            CutDefinition cut = defs.cut(o.cut());
            g.drawString(font, Component.translatable("screen.immersive_jewelry.cut_potency", pct(o.potency())), 20, 168, JewelryGui.TEXT, false);
            g.drawString(font, Component.translatable("screen.immersive_jewelry.cut_stability", pct(o.stability())), 128, 168, JewelryGui.TEXT, false);
            g.drawString(font, Component.translatable("screen.immersive_jewelry.cut_retained", DescriptionBuilder.trim(stone.size()), DescriptionBuilder.trim(o.size())), 240, 168, JewelryGui.TEXT, false);
            if (cut != null) JewelryGui.drawWrapped(g, font, cut.description(), 20, 183, W - 40, JewelryGui.TEXT_DIM, 3);
        }
        super.renderPanel(g, mouseX, mouseY, partialTick);
        if (mouseX >= 19 && mouseX < 37 && mouseY >= 43 && mouseY < 61) g.renderTooltip(font, stoneStack, mouseX, mouseY);
    }

    private static String pct(float modifier) {
        int p = Math.round((modifier - 1F) * 100F);
        return (p >= 0 ? "+" : "") + p + "%";
    }

    @Override
    protected boolean panelClicked(double mouseX, double mouseY, int button) {
        int listX = 152;
        int listY = 36;
        for (int i = scroll; i < Math.min(packet.options().size(), scroll + VISIBLE); i++) {
            int y = listY + (i - scroll) * ROW;
            if (mouseX >= listX && mouseX < listX + 196 && mouseY >= y && mouseY < y + ROW - 2) {
                if (button != 0) return false;
                selected = i;
                return true;
            }
        }
        return super.panelClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean panelScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, packet.options().size() - VISIBLE);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
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
