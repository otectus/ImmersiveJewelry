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
 * A vanilla container panel: the lists are vanilla button rows with the creative scroller, and what the piece will do
 * sits in a dark readout so its tooltip colours keep their contrast.
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
    private static final int DX = 12, DW = 136, SX = 156, SW = 148, FX = 312, FW = 132;
    private static final int LIST_Y = 36;
    /** The last pixel row the assembly lists may draw on. */
    private static final int LIST_BOTTOM = LIST_Y + LIST_ROWS * ROW - 2;
    private static final int PREVIEW_Y = 164;
    /** The rework column: its x, and the top of its refinish list. */
    private static final int RX = W - 140, RW = 128, REFINISH_Y = 136, REFINISH_ROWS = 4;
    private Button begin;
    /** The list whose scroller is being dragged: 'd'esigns, 's'tones, 'f'inishes, or 0. */
    private char dragging;

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
            int x = left + RX;
            int y = top + 20;
            addRenderableWidget(VanillaGui.button(x, y, RW, 20, Component.translatable("screen.immersive_jewelry.remove_stones"),
                    b -> action(BenchActionPacket.Action.REMOVE, null))).active = packet.canRemove();
            addRenderableWidget(VanillaGui.button(x, y + 24, RW, 20, Component.translatable("screen.immersive_jewelry.retighten"),
                    b -> action(BenchActionPacket.Action.RETIGHTEN, null))).active = packet.canRetighten();
            Button repair = addRenderableWidget(VanillaGui.button(x, y + 48, RW, 20, Component.translatable("screen.immersive_jewelry.repair"),
                    b -> action(BenchActionPacket.Action.REPAIR, null)));
            repair.active = !packet.repairCost().isEmpty() && packet.repairAffordable();
            addRenderableWidget(VanillaGui.button(x, top + H - 28, RW, 20, Component.translatable("screen.immersive_jewelry.take_piece"),
                    b -> action(BenchActionPacket.Action.TAKE, null)));
        } else {
            begin = addRenderableWidget(VanillaGui.button(left + W - 128, top + H - 28, 116, 20,
                    Component.translatable("screen.immersive_jewelry.begin_setting"), b -> begin()));
            addRenderableWidget(VanillaGui.button(left + W - 128, top + H - 52, 116, 20, Component.translatable("screen.immersive_jewelry.take_blank"),
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
        VanillaGui.panel(g, left, top, W, H);
        renderTitle(g, Component.translatable("screen.immersive_jewelry.bench_title", packet.workpiece().getHoverName()), W - 16, mouseX, mouseY);
        if (packet.rework()) renderRework(g, mouseX, mouseY);
        else renderAssemble(g, mouseX, mouseY);
        super.renderPanel(g, mouseX, mouseY, partialTick);
    }

    private void renderAssemble(GuiGraphics g, int mouseX, int mouseY) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        for (int[] c : new int[][]{{DX, DW}, {SX, SW}, {FX, FW}}) {
            VanillaGui.inset(g, c[0] - 1, LIST_Y - 1, c[1] + 2, LIST_ROWS * ROW);
        }
        g.drawString(font, Component.translatable("screen.immersive_jewelry.designs"), DX, LIST_Y - 12, VanillaGui.LABEL, false);
        if (packet.designs().isEmpty()) VanillaGui.wrapped(g, font, Component.translatable("screen.immersive_jewelry.no_designs"), DX + 5, LIST_Y + 5, DW - 10, VanillaGui.LABEL, 11);
        int dw = rowWidth(DW, packet.designs().size(), LIST_ROWS);
        for (int i = designScroll; i < Math.min(packet.designs().size(), designScroll + LIST_ROWS); i++) {
            OpenBenchPacket.Design d = packet.designs().get(i);
            int y = LIST_Y + (i - designScroll) * ROW;
            boolean hot = hover(mouseX, mouseY, DX, y, dw);
            boolean current = i == design;
            row(g, DX, y, dw, true, hot || current);
            // The chosen design is marked with a sign as well as the highlight.
            String marker = current ? "» " : "";
            g.drawString(font, marker + VanillaGui.clipped(font, d.name(), dw - 12 - font.width(marker)), DX + 6, y + 3,
                    current ? VanillaGui.ROW_TEXT_SELECTED : VanillaGui.ROW_TEXT, true);
            Component style = Component.translatable("setting_style.immersive_jewelry." + d.style().id());
            g.drawString(font, VanillaGui.clipped(font, Component.translatable("screen.immersive_jewelry.design_summary", d.stones(), style), dw - 12), DX + 6, y + 13, VanillaGui.ROW_TEXT_DIM, true);
            if (hot) tooltip(d.name().copy().append("\n").append(Component.translatable("screen.immersive_jewelry.design_info", d.stones(), style, d.points())));
        }
        scrollbar(g, DX, DW, packet.designs().size(), designScroll, LIST_Y, LIST_ROWS);
        OpenBenchPacket.Design d = packet.designs().isEmpty() ? null : packet.designs().get(design);
        g.drawString(font, d == null ? Component.translatable("screen.immersive_jewelry.stones_none")
                : Component.translatable("screen.immersive_jewelry.stones", chosen.size(), d.stones()), SX, LIST_Y - 12, VanillaGui.LABEL, false);
        List<Integer> eligible = eligible();
        if (eligible.isEmpty()) VanillaGui.wrapped(g, font, Component.translatable("screen.immersive_jewelry.no_stones"), SX + 5, LIST_Y + 5, SW - 10, VanillaGui.LABEL, 11);
        int sw = rowWidth(SW, eligible.size(), LIST_ROWS);
        for (int r = stoneScroll; r < Math.min(eligible.size(), stoneScroll + LIST_ROWS); r++) {
            int i = eligible.get(r);
            OpenBenchPacket.Stone stone = packet.stones().get(i);
            int y = LIST_Y + (r - stoneScroll) * ROW;
            boolean hot = hover(mouseX, mouseY, SX, y, sw);
            boolean picked = chosen.contains(i);
            row(g, SX, y, sw, true, hot || picked);
            ItemStack stack = CutGemItem.create(stone.data());
            g.renderItem(stack, SX + 4, y + 3);
            g.drawString(font, VanillaGui.clipped(font, JewelryNames.gem(defs, stone.data().gem()), sw - 40), SX + 24, y + 3,
                    picked ? VanillaGui.ROW_TEXT_SELECTED : VanillaGui.ROW_TEXT, true);
            g.drawString(font, VanillaGui.clipped(font, Component.translatable("tooltip.immersive_jewelry.cut",
                    CutGrade.of(stone.data().cutQuality()).displayName(), JewelryNames.cut(defs, stone.data().cut())), sw - 30),
                    SX + 24, y + 13, VanillaGui.ROW_TEXT_DIM, true);
            if (picked) JewelryGui.check(g, SX + sw - 12, y + 5, JewelryGui.goodColor());
            if (hot) tooltip(stack);
        }
        scrollbar(g, SX, SW, eligible.size(), stoneScroll, LIST_Y, LIST_ROWS);
        g.drawString(font, Component.translatable("screen.immersive_jewelry.finish"), FX, LIST_Y - 12, VanillaGui.LABEL, false);
        renderFinishes(g, mouseX, mouseY, FX, LIST_Y, FW, finish, LIST_ROWS);

        // What the piece will do, in a dark readout: its effect lines keep their tooltip colours.
        int pw = W - 128 - 6 - 12;
        int ph = H - 12 - PREVIEW_Y;
        VanillaGui.display(g, 12, PREVIEW_Y, pw, ph);
        ItemStack preview = preview();
        if (!preview.isEmpty()) {
            VanillaGui.slot(g, 18, PREVIEW_Y + 6);
            g.renderItem(preview, 19, PREVIEW_Y + 7);
            JewelryData data = JewelryData.get(preview).orElse(null);
            if (data != null) {
                g.drawString(font, VanillaGui.clipped(font, preview.getHoverName(), pw - 40), 40, PREVIEW_Y + 7, VanillaGui.DISPLAY_TEXT, false);
                g.drawString(font, VanillaGui.clipped(font, Component.translatable("screen.immersive_jewelry.predicted", WorkmanshipGrade.of(data.workmanship()).displayName()), pw - 40),
                        40, PREVIEW_Y + 17, VanillaGui.DISPLAY_TEXT_DIM, false);
                List<Component> lines = new ArrayList<>();
                JewelryTooltips.effects(JewelryResolver.resolve(data, defs), lines);
                int ly = PREVIEW_Y + 30;
                for (Component line : lines) {
                    if (line.getString().isEmpty()) continue;
                    if (ly + 9 > PREVIEW_Y + ph - 4) break;
                    readoutLine(g, line, 18, ly, pw - 12);
                    ly += 11;
                }
            }
            if (mouseX >= 18 && mouseX < 36 && mouseY >= PREVIEW_Y + 6 && mouseY < PREVIEW_Y + 24) tooltip(preview);
        } else {
            VanillaGui.wrapped(g, font, Component.translatable("screen.immersive_jewelry.choose_hint"), 18, PREVIEW_Y + 8, pw - 12, VanillaGui.DISPLAY_TEXT_DIM, 3);
        }
    }

    /** A readout line: in its own colours when it fits, else clipped in the readout's grey. */
    private void readoutLine(GuiGraphics g, Component line, int x, int y, int width) {
        if (VanillaGui.isClipped(font, line, width)) g.drawString(font, VanillaGui.clipped(font, line, width), x, y, VanillaGui.DISPLAY_TEXT_DIM, false);
        else g.drawString(font, line, x, y, VanillaGui.DISPLAY_TEXT_DIM, false);
    }

    /** A finish can be picked when the design allows it (assembly) or when it is affordable (refinishing). */
    private boolean finishActive(int index) {
        return packet.rework() ? packet.finishes().get(index).affordable() : allowedFinish(index);
    }

    private void renderFinishes(GuiGraphics g, int mouseX, int mouseY, int x, int y0, int w, int selected, int visible) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        int rw = rowWidth(w, packet.finishes().size(), visible);
        for (int i = finishScroll; i < Math.min(packet.finishes().size(), finishScroll + visible); i++) {
            OpenBenchPacket.Finish f = packet.finishes().get(i);
            int y = y0 + (i - finishScroll) * ROW;
            boolean hot = hover(mouseX, mouseY, x, y, rw);
            boolean active = finishActive(i);
            boolean current = i == selected;
            row(g, x, y, rw, active, active && (hot || current));
            String marker = current ? "» " : "";
            int nameColor = !active ? VanillaGui.ROW_TEXT_DISABLED : !f.affordable() ? VanillaGui.ROW_TEXT_DIM
                    : current ? VanillaGui.ROW_TEXT_SELECTED : VanillaGui.ROW_TEXT;
            g.drawString(font, marker + VanillaGui.clipped(font, JewelryNames.finish(defs, f.id()), rw - 32 - font.width(marker)), x + 6, y + 3, nameColor, true);
            FinishDefinition def = defs.finish(f.id());
            if (def != null) {
                Component info = Component.translatable("screen.immersive_jewelry.finish_info", pct(def.gemPotency()), pct(def.integrity()));
                g.drawString(font, VanillaGui.clipped(font, info, rw - 30), x + 6, y + 13, active ? VanillaGui.ROW_TEXT_DIM : VanillaGui.ROW_TEXT_DISABLED, true);
                if (hot) tooltip(JewelryNames.finish(defs, f.id()).copy().append("\n").append(info).append("\n").append(def.description()));
            }
            if (!f.cost().isEmpty()) {
                g.renderItem(f.cost(), x + rw - 21, y + 3);
                g.renderItemDecorations(font, f.cost(), x + rw - 21, y + 3);
                if (!f.affordable()) JewelryGui.cross(g, x + rw - 8, y + 13, JewelryGui.missColor());
                if (mouseX >= x + rw - 21 && mouseX < x + rw - 5 && mouseY >= y + 3 && mouseY < y + 19) tooltip(f.cost());
            }
        }
        scrollbar(g, x, w, packet.finishes().size(), finishScroll, y0, visible);
        int descY = y0 + Math.min(visible, packet.finishes().size()) * ROW + 4;
        int lines = (LIST_BOTTOM - descY) / 10;
        if (!packet.rework() && selected >= 0 && selected < packet.finishes().size() && lines > 0) {
            FinishDefinition def = defs.finish(packet.finishes().get(selected).id());
            if (def != null) VanillaGui.wrapped(g, font, def.description(), x + 5, descY, w - 10, VanillaGui.LABEL, lines);
        }
    }

    private void renderRework(GuiGraphics g, int mouseX, int mouseY) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        ItemStack piece = packet.workpiece();
        // The piece and everything its tooltip says, in a dark readout so the lines keep their colours.
        VanillaGui.display(g, 12, 20, W - 168, H - 32);
        VanillaGui.slot(g, 18, 26);
        g.renderItem(piece, 19, 27);
        g.drawString(font, VanillaGui.clipped(font, piece.getHoverName(), W - 208), 40, 31, VanillaGui.DISPLAY_TEXT, false);
        g.fill(18, 48, W - 162, 49, 0xFF4A4A4A);
        JewelryData data = JewelryData.get(piece).orElse(null);
        if (data != null) {
            List<Component> lines = new ArrayList<>();
            JewelryTooltips.jewelry(data, defs, lines);
            int y = 56;
            for (Component line : lines) {
                if (y > H - 26) break;
                readoutLine(g, line, 18, y, W - 190);
                y += 11;
            }
        }
        if (mouseX >= 18 && mouseX < 36 && mouseY >= 26 && mouseY < 44) tooltip(piece);
        int x = RX;
        if (!packet.repairCost().isEmpty()) {
            VanillaGui.slot(g, x - 1, 95);
            g.renderItem(packet.repairCost(), x, 96);
            g.renderItemDecorations(font, packet.repairCost(), x, 96);
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= 95 && mouseY < 113) tooltip(packet.repairCost());
            Component cap = Component.translatable("screen.immersive_jewelry.repair_cap", Math.round(packet.repairCap() * 100));
            g.drawString(font, VanillaGui.clipped(font, cap, RW - 22), x + 22, 100, VanillaGui.LABEL, false);
        } else if (data != null) {
            boolean worn = data.integrity() < packet.repairCap() - 0.001F;
            Component note = worn ? Component.translatable("screen.immersive_jewelry.repair_unavailable", JewelryNames.metal(defs, data.metal()))
                    : Component.translatable("screen.immersive_jewelry.repair_none");
            g.drawString(font, VanillaGui.clipped(font, note, RW), x, 100, VanillaGui.LABEL, false);
        }
        g.drawString(font, Component.translatable("screen.immersive_jewelry.refinish"), x, REFINISH_Y - 12, VanillaGui.LABEL, false);
        VanillaGui.inset(g, x - 1, REFINISH_Y - 1, RW + 2, REFINISH_ROWS * ROW);
        renderFinishes(g, mouseX, mouseY, x, REFINISH_Y, RW, -1, REFINISH_ROWS);
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

    /** Row width in a list column: the whole column, less the scroller's when the list scrolls. */
    private static int rowWidth(int w, int count, int visible) {
        return count > visible ? w - VanillaGui.SCROLLBAR_WIDTH - 1 : w;
    }

    private static int scrollbarX(int x, int w) {
        return x + w - VanillaGui.SCROLLBAR_WIDTH + 1;
    }

    /** The vanilla scroller at a list column's right edge, when the list scrolls. */
    private void scrollbar(GuiGraphics g, int x, int w, int count, int scroll, int y0, int visible) {
        if (count <= visible) return;
        VanillaGui.scrollbar(g, scrollbarX(x, w), y0 - 1, visible * ROW, scroll / (float) (count - visible), true);
    }

    @Override
    protected boolean panelScrolled(double mx, double my, double delta) {
        int step = -(int) Math.signum(delta);
        if (packet.rework()) {
            if (mx >= RX - 2 && mx < RX + RW + 2 && my >= REFINISH_Y && my < REFINISH_Y + REFINISH_ROWS * ROW) {
                finishScroll = Math.max(0, Math.min(Math.max(0, packet.finishes().size() - REFINISH_ROWS), finishScroll + step));
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

    private void row(GuiGraphics g, int x, int y, int w, boolean active, boolean highlighted) {
        VanillaGui.row(g, x, y, w, ROW - 2, active, highlighted);
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
        if (pressScroller(mx, my)) return true;
        int x = (int) mx, y = (int) my;
        if (packet.rework()) {
            int rw = rowWidth(RW, packet.finishes().size(), REFINISH_ROWS);
            for (int i = finishScroll; i < Math.min(packet.finishes().size(), finishScroll + REFINISH_ROWS); i++) {
                if (hover(x, y, RX, REFINISH_Y + (i - finishScroll) * ROW, rw) && packet.finishes().get(i).affordable()) {
                    action(BenchActionPacket.Action.REFINISH, packet.finishes().get(i).id());
                    return true;
                }
            }
            return false;
        }
        int dw = rowWidth(DW, packet.designs().size(), LIST_ROWS);
        for (int i = designScroll; i < Math.min(packet.designs().size(), designScroll + LIST_ROWS); i++) {
            if (hover(x, y, DX, LIST_Y + (i - designScroll) * ROW, dw)) {
                if (design != i) { chosen.clear(); stoneScroll = 0; }
                design = i;
                if (!allowedFinish(finish)) { chooseDefaultFinish(); finishScroll = Math.max(0, finish - LIST_ROWS + 1); }
                updateBegin();
                return true;
            }
        }
        List<Integer> eligible = eligible();
        int sw = rowWidth(SW, eligible.size(), LIST_ROWS);
        for (int r = stoneScroll; r < Math.min(eligible.size(), stoneScroll + LIST_ROWS); r++) {
            if (hover(x, y, SX, LIST_Y + (r - stoneScroll) * ROW, sw)) {
                toggleStone(eligible.get(r)); updateBegin(); return true;
            }
        }
        int fw = rowWidth(FW, packet.finishes().size(), LIST_ROWS);
        for (int i = finishScroll; i < Math.min(packet.finishes().size(), finishScroll + LIST_ROWS); i++) {
            if (hover(x, y, FX, LIST_Y + (i - finishScroll) * ROW, fw) && allowedFinish(i)) {
                finish = i; updateBegin(); return true;
            }
        }
        return false;
    }

    /** Starts dragging the scroller under the pointer, if any, and jumps the list there. */
    private boolean pressScroller(double mx, double my) {
        if (packet.rework()) {
            if (packet.finishes().size() > REFINISH_ROWS && VanillaGui.onScrollbar(mx, my, scrollbarX(RX, RW), REFINISH_Y - 1, REFINISH_ROWS * ROW)) {
                dragging = 'f';
                dragScroller(my);
                return true;
            }
            return false;
        }
        int[][] lists = {{'d', DX, DW, packet.designs().size()}, {'s', SX, SW, eligible().size()}, {'f', FX, FW, packet.finishes().size()}};
        for (int[] list : lists) {
            if (list[3] > LIST_ROWS && VanillaGui.onScrollbar(mx, my, scrollbarX(list[1], list[2]), LIST_Y - 1, LIST_ROWS * ROW)) {
                dragging = (char) list[0];
                dragScroller(my);
                return true;
            }
        }
        return false;
    }

    private void dragScroller(double my) {
        int rows = packet.rework() ? REFINISH_ROWS : LIST_ROWS;
        int y0 = packet.rework() ? REFINISH_Y : LIST_Y;
        switch (dragging) {
            case 'd' -> designScroll = VanillaGui.scrollAt(my, y0 - 1, rows * ROW, Math.max(0, packet.designs().size() - rows));
            case 's' -> stoneScroll = VanillaGui.scrollAt(my, y0 - 1, rows * ROW, Math.max(0, eligible().size() - rows));
            case 'f' -> finishScroll = VanillaGui.scrollAt(my, y0 - 1, rows * ROW, Math.max(0, packet.finishes().size() - rows));
            default -> { }
        }
    }

    @Override
    protected boolean panelDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != 0) {
            dragScroller(my);
            return true;
        }
        return super.panelDragged(mx, my, button, dx, dy);
    }

    @Override
    protected boolean panelReleased(double mx, double my, int button) {
        dragging = 0;
        return super.panelReleased(mx, my, button);
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
