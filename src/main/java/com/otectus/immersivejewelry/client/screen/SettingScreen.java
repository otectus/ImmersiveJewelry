package com.otectus.immersivejewelry.client.screen;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.client.JewelryColors;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.minigame.setting.SettingParams;
import com.otectus.immersivejewelry.minigame.setting.SettingSession;
import com.otectus.immersivejewelry.minigame.setting.SettingSimulation;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.BenchActionPacket;
import com.otectus.immersivejewelry.network.packet.SettingInputPacket;
import com.otectus.immersivejewelry.network.packet.SettingResultPacket;
import com.otectus.immersivejewelry.network.packet.SettingStartPacket;
import com.otectus.immersivejewelry.quality.CutGrade;
import com.otectus.immersivejewelry.quality.WorkmanshipGrade;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The setting minigame (spec 12): the stones seen from above, their prongs (or bezel points) around them. Hold the
 * left button (or Space) to squeeze the highlighted prong and release inside the brass band. The band follows the
 * pressure already on the opposite prong, so the stone sits level: watch the bubble. In removal mode the band is low:
 * lift gently. On a vanilla container panel: the stone in a dark display, the pressure gauge and time bar beside it,
 * and feedback in a dark readout.
 */
public class SettingScreen extends MinigameScreen {
    private static final int W = 400;
    private static final int H = 244;
    /** The gauge column, right of the stone. */
    private static final int GX = 202;
    private static final int GW = W - 8 - GX;
    /** The centre of the stone being worked. */
    private static final int AREA_CX = 100;
    private static final int AREA_CY = 112;

    private final SettingStartPacket start;
    private final SettingSimulation sim;
    private final boolean[] squeeze;
    private final int[] gemColors;
    private final ItemStack[] gemStacks;
    private SettingResultPacket result;
    private String flash = "";
    private long flashUntil;
    private boolean cueGiven;

    public SettingScreen(SettingStartPacket packet) {
        super(Component.translatable("screen.immersive_jewelry.setting"), packet.sessionId(), packet.readyMs(), W, H);
        this.start = packet;
        this.sim = new SettingSimulation(packet.params());
        this.squeeze = new boolean[packet.params().maxSteps()];
        JewelryDefinitions defs = JewelryDefinitions.client();
        gemColors = new int[Math.max(1, packet.stones().size())];
        gemStacks = new ItemStack[gemColors.length];
        for (int i = 0; i < gemColors.length; i++) {
            gemColors[i] = packet.stones().isEmpty() ? 0xFFB8C4CC : 0xFF000000 | JewelryColors.gemColor(defs, packet.stones().get(i).gem());
            gemStacks[i] = packet.stones().isEmpty() ? ItemStack.EMPTY : CutGemItem.create(packet.stones().get(i));
        }
    }

    @Override
    protected void initPanel() {
        clearWidgets();
        if (result != null) {
            addRenderableWidget(VanillaGui.button(left + W - 104, top + H - 28, 96, 20, Component.translatable("gui.done"), b -> onClose()));
            if (start.mode() != SettingSession.Mode.REMOVE && result.defects().contains(SettingDefect.LOOSE)) {
                addRenderableWidget(VanillaGui.button(left + W - 204, top + H - 28, 96, 20, Component.translatable("screen.immersive_jewelry.retighten"),
                        b -> ModNetwork.CHANNEL.sendToServer(new BenchActionPacket(start.pos(), BenchActionPacket.Action.RETIGHTEN, null,
                                com.otectus.immersivejewelry.config.ClientConfig.get(com.otectus.immersivejewelry.config.ClientConfig.LARGER_TARGETS),
                                com.otectus.immersivejewelry.config.ClientConfig.get(com.otectus.immersivejewelry.config.ClientConfig.SLOWER_MINIGAMES)))));
            }
        }
    }

    public SettingParams params() {
        return start.params();
    }

    @Override
    protected int maxSteps() {
        return start.params().maxSteps();
    }

    @Override
    protected boolean simulate(int index, boolean down) {
        if (scriptedInput != null) down = scriptedInput.apply(index)[2] != 0;
        squeeze[index] = down;
        int events = sim.step(down);
        if ((events & SettingSimulation.EVENT_WINDOW) != 0 && !cueGiven) {
            cueGiven = true;
            JewelryGui.playCue();
        }
        if ((events & SettingSimulation.EVENT_SET) != 0) {
            flash("set");
            JewelryGui.playUi(ModSounds.BENCH_PRONG.get(), 1F, 0.6F);
        } else if ((events & SettingSimulation.EVENT_CHIP) != 0) {
            flash("chip");
            shake = 1F;
        } else if ((events & SettingSimulation.EVENT_OVER) != 0) {
            flash(start.mode() == SettingSession.Mode.REMOVE ? "scratch" : "bent");
            shake = 0.5F;
        } else if ((events & SettingSimulation.EVENT_LOOSE) != 0) {
            flash("loose");
        }
        if ((events & (SettingSimulation.EVENT_SET | SettingSimulation.EVENT_OVER | SettingSimulation.EVENT_LOOSE)) != 0) cueGiven = false;
        return sim.isComplete();
    }

    private void flash(String key) {
        flash = key;
        flashUntil = Util.getMillis() + com.otectus.immersivejewelry.config.ClientConfig.get(com.otectus.immersivejewelry.config.ClientConfig.FEEDBACK_LABEL_MS);
    }

    @Override
    protected void send(int first, int end) {
        long bits = 0L;
        for (int i = first; i < end; i++) if (squeeze[i]) bits |= 1L << (i - first);
        ModNetwork.CHANNEL.sendToServer(new SettingInputPacket(sessionId, first, end - first, bits));
    }

    public void onResult(SettingResultPacket packet) {
        result = packet;
        finished = true;
        init();
        JewelryGui.playUi(ModSounds.BENCH_FINISH.get(), 1F, 0.8F);
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.pose().pushPose();
        g.pose().translate(shakeOffset(false), shakeOffset(true), 0);
        VanillaGui.panel(g, left, top, W, H);
        renderTitle(g, Component.translatable("screen.immersive_jewelry.setting_title." + start.mode().name().toLowerCase(java.util.Locale.ROOT),
                start.workpiece().getHoverName()), W - 16, mouseX, mouseY);
        VanillaGui.display(g, 8, 19, 184, H - 27);
        drawStones(g);
        drawGauges(g);
        if (counting()) {
            int n = (int) Math.ceil(countdown());
            g.pose().pushPose();
            g.pose().translate(left + AREA_CX, top + AREA_CY - 8, 0);
            g.pose().scale(2.5F, 2.5F, 1F);
            g.drawCenteredString(font, n > 0 ? Component.literal(String.valueOf(n)) : Component.translatable("screen.immersive_jewelry.go"), 0, 0, JewelryGui.CUE);
            g.pose().popPose();
        }
        if (result != null) {
            g.fill(4, 18, W - 4, H - 4, 0xB0101010);
            drawResult(g);
        }
        g.pose().popPose();
        super.renderPanel(g, mouseX, mouseY, partialTick);
    }

    private void drawStones(GuiGraphics g) {
        SettingParams p = start.params();
        int stones = p.stones();
        float areaCx = left + AREA_CX;
        float areaCy = top + AREA_CY;
        float stoneR = 46F;
        JewelryDefinitions defs = JewelryDefinitions.client();
        int active = sim.activePoint();
        int activeStone = p.stoneOf()[active];
        float scale = JewelryGui.markerScale();
        // Work on one stone at a readable size, even for designs with eight stones or larger markers.
        int color = gemColors[Math.min(activeStone, gemColors.length - 1)];
        GemData stone = activeStone < start.stones().size() ? start.stones().get(activeStone) : null;
        CutDefinition cut = stone == null ? null : defs.cut(stone.cut());
        drawGem(g, areaCx, areaCy, stoneR, color, cut == null ? 1 : cut.modelIndex());
        JewelryGui.ring(g, areaCx, areaCy, stoneR + 3, 0xFFB8A070);
        for (int i = 0; i < p.points(); i++) {
            if (p.stoneOf()[i] != activeStone) continue;
            float a = p.angles()[i];
            float px = areaCx + Mth.cos(a) * (stoneR + 5);
            float py = areaCy + Mth.sin(a) * (stoneR + 5);
            drawPoint(g, i, px, py, a, i == active && !sim.isComplete(), scale);
        }
        if (stones > 1) {
            g.drawCenteredString(font, Component.translatable("screen.immersive_jewelry.stone_index", activeStone + 1, stones),
                    Math.round(areaCx), 25, VanillaGui.DISPLAY_TEXT_DIM);
            drawStoneProgress(g, p, activeStone);
        }
        // Balance: a bubble level. Centred when pressures around the stone agree.
        float align = sim.alignment();
        int bx = left + 30;
        int by = H - 22;
        JewelryGui.disc(g, bx, by, 9, 0xFF1C2A24);
        JewelryGui.ring(g, bx, by, 9, 0xFF8FB8A0);
        JewelryGui.ring(g, bx, by, 3, 0x808FB8A0);
        float off = (1F - align) * 7F;
        JewelryGui.disc(g, bx + off * 0.7F, by - off * 0.7F, 2.5F, align >= 0.7F ? JewelryGui.goodColor() : JewelryGui.missColor());
        g.drawString(font, Component.translatable("screen.immersive_jewelry.balance"), bx + 14, by - 4, VanillaGui.DISPLAY_TEXT_DIM, false);
    }

    private void drawStoneProgress(GuiGraphics g, SettingParams p, int activeStone) {
        int stones = p.stones();
        for (int s = 0; s < stones; s++) {
            int x = left + AREA_CX - stones * 10 + s * 20;
            int y = top + 188;
            // The stone being worked is framed in the target colour.
            if (s == activeStone) g.fill(x - 1, y - 1, x + 19, y + 19, JewelryGui.targetColor());
            VanillaGui.slot(g, x, y);
            g.renderItem(gemStacks[Math.min(s, gemStacks.length - 1)], x + 1, y + 1);
            boolean done = true;
            for (int i = 0; i < p.points(); i++) {
                if (p.stoneOf()[i] == s && !sim.isDone(i)) done = false;
            }
            if (done) JewelryGui.check(g, x + 10, y + 11, 0xFFFFFFFF);
        }
    }

    private void drawGem(GuiGraphics g, float cx, float cy, float r, int color, int shape) {
        int n = switch (shape) {
            case 2 -> 10;
            case 3 -> 8;
            case 4 -> 40;
            default -> 8;
        };
        float[] pts = new float[n * 2];
        for (int i = 0; i < n; i++) {
            float a = i * Mth.TWO_PI / n + (shape == 3 ? Mth.PI / 8F : 0F);
            float rr = shape == 3 ? r * (i % 2 == 0 ? 1F : 0.86F) : r;
            pts[i * 2] = cx + Mth.cos(a) * rr;
            pts[i * 2 + 1] = cy + Mth.sin(a) * rr * (shape == 3 ? 0.8F : 1F);
        }
        JewelryGui.polygon(g, pts, JewelryGui.scale(color, 0.7F));
        JewelryGui.disc(g, cx, cy, r * 0.5F, JewelryGui.scale(color, 1.05F));
        for (int i = 0; i < n; i += Math.max(1, n / 8)) {
            JewelryGui.line(g, cx, cy, pts[i * 2], pts[i * 2 + 1], 1, JewelryGui.withAlpha(JewelryGui.lighten(color, 50), 120));
        }
        JewelryGui.outline(g, pts, JewelryGui.lighten(color, 70));
        JewelryGui.disc(g, cx - r * 0.3F, cy - r * 0.35F, r * 0.12F, 0xB0FFFFFF);
    }

    private void drawPoint(GuiGraphics g, int i, float px, float py, float angle, boolean active, float scale) {
        boolean done = sim.isDone(i);
        float acc = sim.accuracy(i);
        boolean bezel = start.params().style() == SettingStyle.BEZEL;
        float size = (bezel ? 4F : 5F) * scale;
        int color;
        if (done) color = acc >= 0.6F ? 0xFFE8C878 : acc >= 0.35F ? 0xFFB8905A : JewelryGui.missColor();
        else color = active ? JewelryGui.targetColor() : VanillaGui.DISPLAY_TEXT_DIM;
        // A claw: a small wedge pointing at the stone (bezel points are squares).
        if (bezel) {
            g.fill(Math.round(px - size / 2), Math.round(py - size / 2), Math.round(px + size / 2), Math.round(py + size / 2), color);
        } else {
            float ix = px - Mth.cos(angle) * size * 1.4F;
            float iy = py - Mth.sin(angle) * size * 1.4F;
            float nx = -Mth.sin(angle) * size * 0.7F;
            float ny = Mth.cos(angle) * size * 0.7F;
            JewelryGui.polygon(g, new float[]{px + nx, py + ny, px - nx, py - ny, ix, iy}, color);
        }
        if (active) JewelryGui.ring(g, px, py, size + 3 + (JewelryGui.reducedMotion() ? 0 : (Util.getMillis() / 150) % 3), JewelryGui.targetColor());
        // Order number next to pending points; a tick or a cross on finished ones (shape, not only colour).
        int ox = Math.round(px + Mth.cos(angle) * (size + 7)) - 2;
        int oy = Math.round(py + Mth.sin(angle) * (size + 7)) - 3;
        if (done) {
            if (acc >= 0.6F) JewelryGui.check(g, ox, oy + 1, 0xFFFFFFFF);
            else JewelryGui.cross(g, ox, oy + 1, 0xFFFFFFFF);
        } else {
            int order = 0;
            for (int k = 0; k < start.params().order().length; k++) if (start.params().order()[k] == i) order = k + 1;
            g.drawString(font, String.valueOf(order), ox, oy, active ? JewelryGui.CUE : VanillaGui.DISPLAY_TEXT_DIM, false);
        }
    }

    private void drawGauges(GuiGraphics g) {
        SettingParams p = start.params();
        int gx = GX;
        int gw = GW;
        int y = 21;
        g.drawString(font, Component.translatable("screen.immersive_jewelry.prong_count", Math.min(sim.doneCount() + 1, p.points()), p.points()),
                gx, y, VanillaGui.LABEL, false);
        y += 14;
        int point = sim.activePoint();
        g.drawString(font, Component.translatable("screen.immersive_jewelry.squeeze"), gx, y, VanillaGui.LABEL, false);
        y += 10;
        // Vertical pressure gauge with the target band.
        int gh = 92;
        int barX = gx + 4;
        VanillaGui.inset(g, barX - 1, y - 1, 18, gh + 2);
        g.fill(barX, y, barX + 16, y + gh, VanillaGui.TRACK);
        float max = 1.3F;
        float target = sim.target(point);
        float lo = (target - p.window()) / max;
        float hi = (target + p.window()) / max;
        g.fill(barX, y + gh - Math.round(gh * hi), barX + 16, y + gh - Math.round(gh * lo), JewelryGui.BAND);
        g.fill(barX, y + gh - Math.round(gh * target / max), barX + 16, y + gh - Math.round(gh * target / max) + 1, JewelryGui.BRASS_LIGHT);
        if (p.chipAbove() < max) {
            int chipY = y + gh - Math.round(gh * p.chipAbove() / max);
            g.fill(barX - 3, chipY, barX + 19, chipY + 1, JewelryGui.missColor());
        }
        float current = sim.holding() ? sim.current() : sim.presses(point) > 0 ? sim.pressure(point) : 0F;
        int fill = Math.round(gh * Math.min(1F, current / max));
        boolean in = Math.abs(current - target) <= p.window();
        g.fill(barX + 4, y + gh - fill, barX + 12, y + gh, in ? JewelryGui.goodColor() : current > target + p.window() ? JewelryGui.missColor() : JewelryGui.PRESSURE);
        g.fill(barX - 2, y + gh - fill, barX + 18, y + gh - fill + 1, 0xFFFFFFFF);
        // Beside the gauge, in fixed slots so nothing jumps: the hint, "once more", "release", then the opposite prong.
        int tx = gx + 28;
        int tw = gw - 28;
        VanillaGui.wrapped(g, font, Component.translatable(p.removal() ? "screen.immersive_jewelry.lift_hint" : "screen.immersive_jewelry.squeeze_hint"),
                tx, y, tw, VanillaGui.LABEL_DIM, 3);
        if (sim.presses(point) > 0 && !sim.isDone(point)) {
            VanillaGui.wrapped(g, font, Component.translatable("screen.immersive_jewelry.once_more"), tx, y + 34, tw, VanillaGui.LABEL, 2);
        }
        if (in && sim.holding()) g.drawString(font, Component.translatable("screen.immersive_jewelry.release_now"), tx, y + 56, JewelryGui.goodText(), false);
        int opposite = p.opposite()[point];
        if (opposite >= 0 && sim.isDone(opposite) && !p.removal()) {
            VanillaGui.wrapped(g, font, Component.translatable("screen.immersive_jewelry.match_opposite"), tx, y + 68, tw, VanillaGui.LABEL_DIM, 3);
        }
        y += gh + 8;
        JewelryGui.timeBar(g, gx, y, gw, 5, 1F - step / (float) maxSteps());
        y += 12;
        // Feedback in a dark readout so the rating colours keep their contrast.
        int readoutTop = y + 3;
        VanillaGui.display(g, gx - 1, readoutTop, gw + 2, H - 8 - readoutTop);
        if (Util.getMillis() < flashUntil && !flash.isEmpty()) {
            int color = flash.equals("set") ? JewelryGui.goodColor() : JewelryGui.missColor();
            g.drawString(font, VanillaGui.clipped(font, Component.translatable("screen.immersive_jewelry.flash." + flash), gw - 8), gx + 4, readoutTop + 5, color, false);
        }
        if (awaitingResult && result == null) {
            VanillaGui.wrapped(g, font, Component.translatable("screen.immersive_jewelry.awaiting"), gx + 4, readoutTop + 17, gw - 6, VanillaGui.DISPLAY_TEXT_DIM, 2);
        }
    }

    private record Line(Component text, int color) {}

    /** The verdict on a small vanilla panel: the piece in a slot, and its grades, defects and notes in a dark readout. */
    private void drawResult(GuiGraphics g) {
        boolean numbers = start.mode() != SettingSession.Mode.REMOVE && ServerConfig.get(ServerConfig.SHOW_NUMERIC_QUALITY);
        List<Line> lines = new ArrayList<>();
        if (start.mode() == SettingSession.Mode.REMOVE) {
            lines.add(new Line(Component.translatable("screen.immersive_jewelry.removed", CutGrade.of(result.quality()).displayName()), VanillaGui.DISPLAY_TEXT));
            lines.add(new Line(Component.translatable("screen.immersive_jewelry.removed_hint"), VanillaGui.DISPLAY_TEXT_DIM));
        } else {
            lines.add(new Line(Component.translatable("screen.immersive_jewelry.setting_result", WorkmanshipGrade.of(result.quality()).displayName()), VanillaGui.DISPLAY_TEXT));
            lines.add(new Line(Component.translatable("tooltip.immersive_jewelry.workmanship", WorkmanshipGrade.of(result.workmanship()).displayName()), VanillaGui.DISPLAY_TEXT));
            if (numbers) {
                lines.add(new Line(Component.translatable("screen.immersive_jewelry.setting_numbers", Math.round(result.quality() * 100),
                        Math.round(result.workmanship() * 100)), VanillaGui.DISPLAY_TEXT_DIM));
            }
        }
        for (SettingDefect d : result.defects()) lines.add(new Line(Component.literal("• ").append(d.displayName()), VanillaGui.DISPLAY_TEXT_DIM));
        if (result.timedOut()) lines.add(new Line(Component.translatable("screen.immersive_jewelry.timed_out"), VanillaGui.DISPLAY_TEXT_DIM));
        if (start.mode() == SettingSession.Mode.SET) lines.add(new Line(Component.translatable("screen.immersive_jewelry.take_hint"), VanillaGui.DISPLAY_TEXT_DIM));
        int bw = W - 48;
        int bx = left + 24;
        int displayHeight = lines.size() * 11 + 7;
        int bh = Math.min(H - 8, 30 + displayHeight + 8);
        int by = top + Math.max(4, (H - 30 - bh) / 2);
        VanillaGui.panel(g, bx, by, bw, bh);
        VanillaGui.slot(g, bx + 8, by + 7);
        g.renderItem(result.result(), bx + 9, by + 8);
        g.drawString(font, VanillaGui.clipped(font, result.result().getHoverName(), bw - 38), bx + 30, by + 12, VanillaGui.LABEL, false);
        VanillaGui.display(g, bx + 8, by + 30, bw - 16, Math.min(displayHeight, bh - 38));
        int y = by + 35;
        int textWidth = bw - 28 - (result.xp() > 0 ? 50 : 0);
        for (int i = 0; i < lines.size() && y + 9 <= by + bh - 8; i++) {
            Line line = lines.get(i);
            int width = i == 0 ? textWidth : bw - 28;
            // Unclipped lines keep their own colours (the grades', the defects').
            Component shown = VanillaGui.isClipped(font, line.text(), width) ? Component.literal(VanillaGui.clipped(font, line.text(), width)) : line.text();
            g.drawString(font, shown, bx + 14, y, line.color(), false);
            y += 11;
        }
        if (result.xp() > 0) {
            Component xp = Component.translatable("screen.immersive_jewelry.result_xp", result.xp());
            g.drawString(font, xp, bx + bw - 14 - font.width(xp), by + 35, JewelryGui.XP, false);
        }
    }
}
