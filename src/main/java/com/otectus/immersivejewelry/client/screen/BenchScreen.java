package com.otectus.immersivejewelry.client.screen;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.jewelry.JewelryResolver;
import com.otectus.immersivejewelry.jewelry.JewelryTooltips;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.BenchActionPacket;
import com.otectus.immersivejewelry.network.packet.OpenBenchPacket;
import com.otectus.immersivejewelry.network.packet.StartSettingPacket;
import com.otectus.immersivejewelry.quality.CutGrade;
import com.otectus.immersivejewelry.quality.Workmanship;
import com.otectus.immersivejewelry.quality.WorkmanshipGrade;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Jeweler's Bench. With a blank on the bench: choose a design, the stones to set and a finish, see what the piece
 * will do, and begin setting. With a finished piece: remove its stones, retighten, repair the frame or refinish it.
 */
public class BenchScreen extends JewelryScreen {
    private static final int W = 456;
    private static final int H = 288;
    private static final int ROW = 24;
    /** Design and stone rows that fit above the preview; longer lists scroll. */
    private static final int LIST_ROWS = 5;

    private OpenBenchPacket packet;
    private int design;
    private final List<Integer> chosen = new ArrayList<>();
    private int finish;
    private int designScroll;
    private int stoneScroll;
    private int finishScroll;
    private ItemStack tooltipStack = ItemStack.EMPTY;
    private Component tooltipText;
    private static final int DX = 12, DW = 136, SX = 156, SW = 148, FX = 312, FW = 132;
    private static final int LIST_Y = 50;
    private Button begin;

    public BenchScreen(OpenBenchPacket packet) {
        super(Component.translatable("screen.immersive_jewelry.bench"), W, H);
        this.packet = packet;
        chooseDefaultFinish();
    }

    public void update(OpenBenchPacket next) {
        this.packet = next;
        chosen.clear();
        design = Math.min(design, Math.max(0, next.designs().size() - 1));
        designScroll = Math.min(designScroll, Math.max(0, next.designs().size() - LIST_ROWS));
        stoneScroll = 0;
        finishScroll = 0;
        chooseDefaultFinish();
        rebuild();
    }

    public net.minecraft.core.BlockPos pos() {
        return packet.pos();
    }

    private void chooseDefaultFinish() {
        finish = 0;
        for (int i = 0; i < packet.finishes().size(); i++) {
            if (packet.finishes().get(i).affordable() && allowedFinish(i)) {
                finish = i;
                return;
            }
        }
    }

    @Override
    protected void initPanel() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        if (packet.rework()) {
            int x = left + W - 140;
            int y = top + 38;
            addRenderableWidget(JewelryGui.button(x, y, 128, 20, Component.translatable("screen.immersive_jewelry.remove_stones"),
                    b -> action(BenchActionPacket.Action.REMOVE, null))).active = packet.canRemove();
            addRenderableWidget(JewelryGui.button(x, y + 24, 128, 20, Component.translatable("screen.immersive_jewelry.retighten"),
                    b -> action(BenchActionPacket.Action.RETIGHTEN, null))).active = packet.canRetighten();
            Button repair = addRenderableWidget(JewelryGui.button(x, y + 48, 128, 20, Component.translatable("screen.immersive_jewelry.repair"),
                    b -> action(BenchActionPacket.Action.REPAIR, null)));
            repair.active = !packet.repairCost().isEmpty() && packet.repairAffordable();
            addRenderableWidget(JewelryGui.button(x, top + H - 28, 128, 20, Component.translatable("screen.immersive_jewelry.take_piece"),
                    b -> action(BenchActionPacket.Action.TAKE, null)));
        } else {
            begin = addRenderableWidget(JewelryGui.button(left + W - 128, top + H - 28, 116, 20,
                    Component.translatable("screen.immersive_jewelry.begin_setting"), b -> begin()));
            addRenderableWidget(JewelryGui.button(left + W - 128, top + H - 52, 116, 20, Component.translatable("screen.immersive_jewelry.take_blank"),
                    b -> action(BenchActionPacket.Action.TAKE, null)));
            updateBegin();
        }
    }

    private void action(BenchActionPacket.Action action, ResourceLocation finishId) {
        ModNetwork.CHANNEL.sendToServer(new BenchActionPacket(packet.pos(), action, finishId,
                ClientConfig.get(ClientConfig.LARGER_TARGETS), ClientConfig.get(ClientConfig.SLOWER_MINIGAMES)));
        if (action == BenchActionPacket.Action.TAKE) onClose();
    }

    private void begin() {
        if (!ready()) return;
        OpenBenchPacket.Design d = packet.designs().get(design);
        int[] slots = chosen.stream().mapToInt(i -> packet.stones().get(i).slot()).toArray();
        ModNetwork.CHANNEL.sendToServer(new StartSettingPacket(packet.pos(), d.id(), slots, packet.finishes().get(finish).id(),
                ClientConfig.get(ClientConfig.LARGER_TARGETS), ClientConfig.get(ClientConfig.SLOWER_MINIGAMES)));
    }

    private boolean ready() {
        if (packet.designs().isEmpty() || packet.finishes().isEmpty()) return false;
        OpenBenchPacket.Design d = packet.designs().get(design);
        return chosen.size() == d.stones() && packet.finishes().get(finish).affordable() && allowedFinish(finish);
    }

    private void updateBegin() {
        if (begin != null) begin.active = ready();
    }

    private boolean allowedFinish(int index) {
        if (packet.rework() || packet.designs().isEmpty()) return true;
        List<ResourceLocation> allowed = packet.designs().get(Math.min(design, packet.designs().size() - 1)).finishes();
        return allowed.isEmpty() || allowed.contains(packet.finishes().get(index).id());
    }

    /** Stones in the inventory that the selected design accepts. */
    private List<Integer> eligible() {
        List<Integer> out = new ArrayList<>();
        if (packet.designs().isEmpty()) return out;
        OpenBenchPacket.Design d = packet.designs().get(design);
        for (int i = 0; i < packet.stones().size(); i++) {
            GemData g = packet.stones().get(i).data();
            if ((d.cuts().isEmpty() || d.cuts().contains(g.cut())) && (d.gems().isEmpty() || d.gems().contains(g.gem()))) out.add(i);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tooltipStack = ItemStack.EMPTY;
        tooltipText = null;
        JewelryGui.panel(g, left, top, W, H);
        JewelryGui.heading(g, font, Component.translatable("screen.immersive_jewelry.bench_title", packet.workpiece().getHoverName()), left, top, W);
        if (packet.rework()) renderRework(g, mouseX, mouseY);
        else renderAssemble(g, mouseX, mouseY);
        super.renderPanel(g, mouseX, mouseY, partialTick);
        if (!tooltipStack.isEmpty()) g.renderTooltip(font, tooltipStack, mouseX, mouseY);
        else if (tooltipText != null) g.renderTooltip(font, font.split(tooltipText, 240), mouseX, mouseY);
    }

    private void renderAssemble(GuiGraphics g, int mouseX, int mouseY) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        for (int[] c : new int[][]{{DX, DW}, {SX, SW}, {FX, FW}}) {
            JewelryGui.well(g, c[0] - 1, 33, c[1] + 2, 141);
        }
        g.drawString(font, Component.translatable("screen.immersive_jewelry.designs"), DX + 6, 38, JewelryGui.TEXT_WARM, false);
        if (packet.designs().isEmpty()) JewelryGui.drawWrapped(g, font, Component.translatable("screen.immersive_jewelry.no_designs"), DX + 6, 56, DW - 12, JewelryGui.TEXT_DIM, 8);
        for (int i = designScroll; i < Math.min(packet.designs().size(), designScroll + LIST_ROWS); i++) {
            OpenBenchPacket.Design d = packet.designs().get(i);
            int y = LIST_Y + (i - designScroll) * ROW;
            boolean hot = hover(mouseX, mouseY, DX, y, DW);
            row(g, DX, y, DW, i == design, hot);
            g.drawString(font, JewelryGui.clipped(font, d.name(), DW - 12), DX + 6, y + 3, JewelryGui.TEXT, false);
            Component style = Component.translatable("setting_style.immersive_jewelry." + d.style().id());
            g.drawString(font, JewelryGui.clipped(font, Component.translatable("screen.immersive_jewelry.design_summary", d.stones(), style), DW - 12), DX + 6, y + 13, JewelryGui.TEXT_DIM, false);
            if (hot) tooltipText = d.name().copy().append("\n").append(Component.translatable("screen.immersive_jewelry.design_info", d.stones(), style, d.points()));
        }
        scrollbar(g, DX + DW - 2, LIST_Y, packet.designs().size(), designScroll, LIST_ROWS);
        OpenBenchPacket.Design d = packet.designs().isEmpty() ? null : packet.designs().get(design);
        g.drawString(font, d == null ? Component.translatable("screen.immersive_jewelry.stones_none")
                : Component.translatable("screen.immersive_jewelry.stones", chosen.size(), d.stones()), SX + 6, 38, JewelryGui.TEXT_WARM, false);
        List<Integer> eligible = eligible();
        if (eligible.isEmpty()) JewelryGui.drawWrapped(g, font, Component.translatable("screen.immersive_jewelry.no_stones"), SX + 6, 56, SW - 12, JewelryGui.TEXT_DIM, 8);
        for (int r = stoneScroll; r < Math.min(eligible.size(), stoneScroll + LIST_ROWS); r++) {
            int i = eligible.get(r);
            OpenBenchPacket.Stone stone = packet.stones().get(i);
            int y = LIST_Y + (r - stoneScroll) * ROW;
            boolean hot = hover(mouseX, mouseY, SX, y, SW);
            row(g, SX, y, SW, chosen.contains(i), hot);
            ItemStack stack = CutGemItem.create(stone.data());
            g.renderItem(stack, SX + 4, y + 3);
            g.drawString(font, JewelryGui.clipped(font, JewelryNames.gem(defs, stone.data().gem()), SW - 40), SX + 24, y + 3, JewelryGui.TEXT, false);
            g.drawString(font, JewelryGui.clipped(font, CutGrade.of(stone.data().cutQuality()).displayName().copy().append(" ")
                    .append(JewelryNames.cut(defs, stone.data().cut())), SW - 30), SX + 24, y + 13, JewelryGui.TEXT_DIM, false);
            if (chosen.contains(i)) JewelryGui.check(g, SX + SW - 12, y + 5, JewelryGui.goodColor());
            if (hot) tooltipStack = stack;
        }
        scrollbar(g, SX + SW - 2, LIST_Y, eligible.size(), stoneScroll, LIST_ROWS);
        g.drawString(font, Component.translatable("screen.immersive_jewelry.finish"), FX + 6, 38, JewelryGui.TEXT_WARM, false);
        renderFinishes(g, mouseX, mouseY, FX, LIST_Y, FW, finish, LIST_ROWS);

        int py = 184;
        JewelryGui.well(g, 12, py, W - 24, H - py - 12);
        ItemStack preview = preview();
        if (!preview.isEmpty()) {
            g.renderItem(preview, 20, py + 8);
            JewelryData data = JewelryData.get(preview).orElse(null);
            if (data != null) {
                g.drawString(font, JewelryGui.clipped(font, preview.getHoverName(), W - 190), 44, py + 8, JewelryGui.TEXT, false);
                g.drawString(font, JewelryGui.clipped(font, Component.translatable("screen.immersive_jewelry.predicted", WorkmanshipGrade.of(data.workmanship()).displayName()), W - 190),
                        44, py + 20, JewelryGui.TEXT_DIM, false);
                List<Component> lines = new ArrayList<>();
                JewelryTooltips.effects(JewelryResolver.resolve(data, defs), lines);
                int ly = py + 36;
                for (Component line : lines) {
                    if (line.getString().isEmpty()) continue;
                    if (ly + 9 > H - 18) break;
                    g.drawString(font, JewelryGui.clipped(font, line, W - 172), 20, ly, JewelryGui.TEXT_DIM, false);
                    ly += 11;
                }
            }
            if (mouseX >= 20 && mouseX < 36 && mouseY >= py + 8 && mouseY < py + 24) tooltipStack = preview;
        } else {
            JewelryGui.drawWrapped(g, font, Component.translatable("screen.immersive_jewelry.choose_hint"), 20, py + 12, W - 172, JewelryGui.TEXT_DIM, 3);
        }
    }

    private void renderFinishes(GuiGraphics g, int mouseX, int mouseY, int x, int y0, int w, int selected, int visible) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        for (int i = finishScroll; i < Math.min(packet.finishes().size(), finishScroll + visible); i++) {
            OpenBenchPacket.Finish f = packet.finishes().get(i);
            int y = y0 + (i - finishScroll) * ROW;
            boolean hot = hover(mouseX, mouseY, x, y, w);
            row(g, x, y, w, i == selected, hot);
            g.drawString(font, JewelryGui.clipped(font, JewelryNames.finish(defs, f.id()), w - 32), x + 6, y + 3,
                    allowedFinish(i) && f.affordable() ? JewelryGui.TEXT : JewelryGui.TEXT_DIM, false);
            FinishDefinition def = defs.finish(f.id());
            if (def != null) {
                Component info = Component.translatable("screen.immersive_jewelry.finish_info", pct(def.gemPotency()), pct(def.integrity()));
                g.drawString(font, JewelryGui.clipped(font, info, w - 12), x + 6, y + 13, JewelryGui.TEXT_DIM, false);
                if (hot) tooltipText = JewelryNames.finish(defs, f.id()).copy().append("\n").append(info).append("\n").append(def.description());
            }
            if (!f.cost().isEmpty()) {
                g.renderItem(f.cost(), x + w - 22, y);
                if (!f.affordable()) JewelryGui.cross(g, x + w - 9, y + 9, JewelryGui.missColor());
                if (mouseX >= x + w - 22 && mouseX < x + w - 6 && mouseY >= y && mouseY < y + 13) tooltipStack = f.cost();
            }
        }
        scrollbar(g, x + w - 2, y0, packet.finishes().size(), finishScroll, visible);
        int descY = y0 + Math.min(visible, packet.finishes().size()) * ROW + 4;
        int lines = (174 - descY) / 10;
        if (!packet.rework() && selected >= 0 && selected < packet.finishes().size() && lines > 0) {
            FinishDefinition def = defs.finish(packet.finishes().get(selected).id());
            if (def != null) JewelryGui.drawWrapped(g, font, def.description(), x + 6, descY, w - 12, JewelryGui.TEXT_DIM, lines);
        }
    }

    private void renderRework(GuiGraphics g, int mouseX, int mouseY) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        ItemStack piece = packet.workpiece();
        JewelryGui.well(g, 12, 34, W - 168, H - 46);
        g.renderItem(piece, 20, 42);
        g.drawString(font, JewelryGui.clipped(font, piece.getHoverName(), W - 208), 42, 46, JewelryGui.TEXT, false);
        JewelryGui.rule(g, 20, 64, W - 184);
        JewelryData data = JewelryData.get(piece).orElse(null);
        if (data != null) {
            List<Component> lines = new ArrayList<>();
            JewelryTooltips.jewelry(data, defs, lines);
            int y = 72;
            for (Component line : lines) {
                if (y > H - 26) break;
                g.drawString(font, JewelryGui.clipped(font, line, W - 190), 20, y, JewelryGui.TEXT_DIM, false);
                y += 11;
            }
        }
        if (mouseX >= 20 && mouseX < 36 && mouseY >= 42 && mouseY < 58) tooltipStack = piece;
        int x = W - 140;
        if (!packet.repairCost().isEmpty()) {
            g.renderItem(packet.repairCost(), x, 110);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= 110 && mouseY < 126) tooltipStack = packet.repairCost();
            Component cap = Component.translatable("screen.immersive_jewelry.repair_cap", Math.round(packet.repairCap() * 100));
            g.drawString(font, JewelryGui.clipped(font, cap, 108), x + 20, 114, JewelryGui.TEXT_DIM, false);
        } else if (data != null) {
            boolean worn = data.integrity() < packet.repairCap() - 0.001F;
            Component note = worn ? Component.translatable("screen.immersive_jewelry.repair_unavailable", JewelryNames.metal(defs, data.metal()))
                    : Component.translatable("screen.immersive_jewelry.repair_none");
            g.drawString(font, JewelryGui.clipped(font, note, 128), x, 114, JewelryGui.TEXT_DIM, false);
        }
        JewelryGui.well(g, x - 2, 132, 132, 116);
        g.drawString(font, Component.translatable("screen.immersive_jewelry.refinish"), x + 4, 138, JewelryGui.TEXT_WARM, false);
        renderFinishes(g, mouseX, mouseY, x, 150, 128, -1, 4);
    }

    private ItemStack preview() {
        if (packet.designs().isEmpty() || chosen.isEmpty() || packet.finishes().isEmpty()) return ItemStack.EMPTY;
        if (!(packet.workpiece().getItem() instanceof JewelryBlankItem blankItem)) return ItemStack.EMPTY;
        BlankData blank = BlankData.get(packet.workpiece()).orElse(null);
        if (blank == null) return ItemStack.EMPTY;
        List<GemData> stones = chosen.stream().map(i -> packet.stones().get(i).data()).toList();
        float assumedSetting = 0.75F;
        float cut = 0F;
        for (GemData s : stones) cut += s.cutQuality();
        cut /= stones.size();
        JewelryData data = new JewelryData(blankItem.form(), blank.metal(), stones, packet.designs().get(design).id(), packet.finishes().get(finish).id(),
                blank.smithingQuality(), assumedSetting, Workmanship.compute(blank.smithingQuality(), cut, assumedSetting), blank.integrity(),
                blank.reworks(), Set.<SettingDefect>of(), null, false, null);
        return JewelryFactory.stack(data);
    }

    private void scrollbar(GuiGraphics g, int x, int y, int count, int scroll, int visible) {
        if (count <= visible) return;
        int track = visible * ROW - 2;
        int thumb = Math.max(8, track * visible / count);
        int ty = y + (track - thumb) * scroll / (count - visible);
        g.fill(x, y, x + 2, y + track, JewelryGui.EDGE);
        g.fill(x, ty, x + 2, ty + thumb, JewelryGui.BRASS);
    }

    @Override
    protected boolean panelScrolled(double mx, double my, double delta) {
        int step = -(int) Math.signum(delta);
        if (packet.rework()) {
            if (mx >= W - 142 && mx < W - 10 && my >= 150 && my < 246) {
                finishScroll = Math.max(0, Math.min(Math.max(0, packet.finishes().size() - 4), finishScroll + step));
                return true;
            }
        } else if (my >= LIST_Y && my < LIST_Y + LIST_ROWS * ROW) {
            if (mx >= DX && mx < DX + DW) designScroll = Math.max(0, Math.min(Math.max(0, packet.designs().size() - LIST_ROWS), designScroll + step));
            else if (mx >= SX && mx < SX + SW) stoneScroll = Math.max(0, Math.min(Math.max(0, eligible().size() - LIST_ROWS), stoneScroll + step));
            else if (mx >= FX && mx < FX + FW) finishScroll = Math.max(0, Math.min(Math.max(0, packet.finishes().size() - LIST_ROWS), finishScroll + step));
            else return super.panelScrolled(mx, my, delta);
            return true;
        }
        return super.panelScrolled(mx, my, delta);
    }

    private void row(GuiGraphics g, int x, int y, int w, boolean selected, boolean hover) {
        JewelryGui.row(g, x, y, w, ROW - 2, selected, hover);
    }

    private static boolean hover(int mx, int my, int x, int y, int w) {
        return mx >= x && mx < x + w && my >= y && my < y + ROW - 2;
    }

    private static String pct(float m) {
        int p = Math.round((m - 1F) * 100F);
        return (p >= 0 ? "+" : "") + p + "%";
    }

    @Override
    protected boolean panelClicked(double mx, double my, int button) {
        if (super.panelClicked(mx, my, button)) return true;
        if (button != 0) return false;
        int x = (int) mx, y = (int) my;
        if (packet.rework()) {
            for (int i = finishScroll; i < Math.min(packet.finishes().size(), finishScroll + 4); i++) {
                if (hover(x, y, W - 140, 150 + (i - finishScroll) * ROW, 128) && packet.finishes().get(i).affordable()) {
                    action(BenchActionPacket.Action.REFINISH, packet.finishes().get(i).id());
                    return true;
                }
            }
            return false;
        }
        for (int i = designScroll; i < Math.min(packet.designs().size(), designScroll + LIST_ROWS); i++) {
            if (hover(x, y, DX, LIST_Y + (i - designScroll) * ROW, DW)) {
                if (design != i) { chosen.clear(); stoneScroll = 0; }
                design = i;
                if (!allowedFinish(finish)) { chooseDefaultFinish(); finishScroll = Math.max(0, finish - LIST_ROWS + 1); }
                updateBegin();
                return true;
            }
        }
        List<Integer> eligible = eligible();
        for (int r = stoneScroll; r < Math.min(eligible.size(), stoneScroll + LIST_ROWS); r++) {
            if (hover(x, y, SX, LIST_Y + (r - stoneScroll) * ROW, SW)) {
                toggleStone(eligible.get(r)); updateBegin(); return true;
            }
        }
        for (int i = finishScroll; i < Math.min(packet.finishes().size(), finishScroll + LIST_ROWS); i++) {
            if (hover(x, y, FX, LIST_Y + (i - finishScroll) * ROW, FW) && allowedFinish(i)) {
                finish = i; updateBegin(); return true;
            }
        }
        return false;
    }

    /** Each row is one inventory slot; a piece's stones are one gem, so picking another gem starts over. */
    private void toggleStone(int index) {
        if (chosen.remove((Integer) index)) return;
        OpenBenchPacket.Design d = packet.designs().get(design);
        GemData stone = packet.stones().get(index).data();
        if (!chosen.isEmpty() && !packet.stones().get(chosen.get(0)).data().gem().equals(stone.gem())) chosen.clear();
        if (chosen.size() >= d.stones()) chosen.remove(0);
        chosen.add(index);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
