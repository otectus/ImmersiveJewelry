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

/**
 * The setting minigame (spec 12): the stones seen from above, their prongs (or bezel points) around them. Hold the
 * left button (or Space) to squeeze the highlighted prong and release inside the brass band. The band follows the
 * pressure already on the opposite prong, so the stone sits level: watch the bubble. In removal mode the band is low:
 * lift gently.
 */
public class SettingScreen extends MinigameScreen {
    private static final int W = 400;
    private static final int H = 264;

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
            addRenderableWidget(JewelryGui.button(left + W - 110, top + H - 28, 96, 20, Component.translatable("gui.done"), b -> onClose()));
            if (start.mode() != SettingSession.Mode.REMOVE && result.defects().contains(SettingDefect.LOOSE)) {
                addRenderableWidget(JewelryGui.button(left + W - 214, top + H - 28, 96, 20, Component.translatable("screen.immersive_jewelry.retighten"),
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
        flashUntil = Util.getMillis() + 900;
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
        JewelryGui.panel(g, left, top, W, H);
        JewelryGui.heading(g, font, Component.translatable("screen.immersive_jewelry.setting_title." + start.mode().name().toLowerCase(java.util.Locale.ROOT),
                start.workpiece().getHoverName()), left, top, W);
        JewelryGui.well(g, 200, 34, W - 212, H - 46);
        drawStones(g);
        drawGauges(g);
        if (counting()) {
            int n = (int) Math.ceil(countdown());
            g.pose().pushPose();
            g.pose().translate(left + 100, top + 100, 0);
            g.pose().scale(2.5F, 2.5F, 1F);
            g.drawCenteredString(font, n > 0 ? Component.literal(String.valueOf(n)) : Component.translatable("screen.immersive_jewelry.go"), 0, 0, JewelryGui.TEXT_WARM);
            g.pose().popPose();
        }
        if (result != null) {
            g.fill(6, 30, W - 6, H - 6, 0xB0151310);
            drawResult(g);
        }
        g.pose().popPose();
        super.renderPanel(g, mouseX, mouseY, partialTick);
    }

    private void drawStones(GuiGraphics g) {
        SettingParams p = start.params();
        int stones = p.stones();
        float areaCx = left + 100;
        float areaCy = 130;
        float stoneR = 46F;
        JewelryGui.well(g, 12, 34, 176, H - 46);
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
                    Math.round(areaCx), 43, JewelryGui.TEXT_DIM);
            drawStoneProgress(g, p, activeStone);
        }
        // Balance: a bubble level. Centred when pressures around the stone agree.
        float align = sim.alignment();
        int bx = left + 30;
        int by = H - 26;
        JewelryGui.disc(g, bx, by, 9, 0xFF1C2A24);
        JewelryGui.ring(g, bx, by, 9, 0xFF8FB8A0);
        JewelryGui.ring(g, bx, by, 3, 0x808FB8A0);
        float off = (1F - align) * 7F;
        JewelryGui.disc(g, bx + off * 0.7F, by - off * 0.7F, 2.5F, align >= 0.7F ? JewelryGui.goodColor() : JewelryGui.missColor());
        g.drawString(font, Component.translatable("screen.immersive_jewelry.balance"), bx + 14, by - 4, JewelryGui.TEXT_DIM, false);
    }

    private void drawStoneProgress(GuiGraphics g, SettingParams p, int activeStone) {
        int stones = p.stones();
        for (int s = 0; s < stones; s++) {
            int x = left + 100 - stones * 10 + s * 20;
            g.fill(x, 206, x + 18, 224, s == activeStone ? JewelryGui.targetColor() : JewelryGui.BORDER);
            g.fill(x + 1, 207, x + 17, 223, JewelryGui.INSET);
            g.renderItem(gemStacks[Math.min(s, gemStacks.length - 1)], x + 1, 207);
            boolean done = true;
            for (int i = 0; i < p.points(); i++) {
                if (p.stoneOf()[i] == s && !sim.isDone(i)) done = false;
            }
            if (done) JewelryGui.check(g, x + 10, 217, JewelryGui.TEXT);
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
        else color = active ? JewelryGui.targetColor() : JewelryGui.TEXT_DIM;
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
            g.drawString(font, String.valueOf(order), ox, oy, active ? JewelryGui.TEXT_WARM : JewelryGui.TEXT_DIM, false);
        }
    }

    private void drawGauges(GuiGraphics g) {
        SettingParams p = start.params();
        int gx = 210;
        int gw = W - 232;
        int y = 44;
        g.drawString(font, Component.translatable("screen.immersive_jewelry.prong_count", Math.min(sim.doneCount() + 1, p.points()), p.points()),
                gx, y, JewelryGui.TEXT, false);
        y += 14;
        int point = sim.activePoint();
        g.drawString(font, Component.translatable("screen.immersive_jewelry.squeeze"), gx, y, JewelryGui.TEXT_DIM, false);
        y += 10;
        // Vertical pressure gauge with the target band.
        int gh = 92;
        int barX = gx + 4;
        JewelryGui.well(g, barX - 1, y - 1, 18, gh + 2);
        float max = 1.3F;
        float target = sim.target(point);
        float lo = (target - p.window()) / max;
        float hi = (target + p.window()) / max;
        g.fill(barX, y + gh - Math.round(gh * hi), barX + 16, y + gh - Math.round(gh * lo), 0x80C9A45C);
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
        JewelryGui.drawWrapped(g, font, Component.translatable(p.removal() ? "screen.immersive_jewelry.lift_hint" : "screen.immersive_jewelry.squeeze_hint"),
                tx, y, tw, JewelryGui.TEXT_DIM, 3);
        if (sim.presses(point) > 0 && !sim.isDone(point)) {
            JewelryGui.drawWrapped(g, font, Component.translatable("screen.immersive_jewelry.once_more"), tx, y + 34, tw, JewelryGui.TEXT_WARM, 2);
        }
        if (in && sim.holding()) g.drawString(font, Component.translatable("screen.immersive_jewelry.release_now"), tx, y + 56, JewelryGui.goodColor(), false);
        int opposite = p.opposite()[point];
        if (opposite >= 0 && sim.isDone(opposite) && !p.removal()) {
            JewelryGui.drawWrapped(g, font, Component.translatable("screen.immersive_jewelry.match_opposite"), tx, y + 68, tw, JewelryGui.TEXT_DIM, 3);
        }
        y += gh + 8;
        JewelryGui.timeBar(g, gx, y, gw, 5, 1F - step / (float) maxSteps());
        y += 12;
        if (Util.getMillis() < flashUntil && !flash.isEmpty()) {
            int color = flash.equals("set") ? JewelryGui.goodColor() : JewelryGui.missColor();
            g.drawString(font, Component.translatable("screen.immersive_jewelry.flash." + flash), gx, y, color, false);
        }
        y += 12;
        if (awaitingResult && result == null) g.drawString(font, Component.translatable("screen.immersive_jewelry.awaiting"), gx, y, JewelryGui.TEXT_DIM, false);
    }

    private void drawResult(GuiGraphics g) {
        // Sized to its lines: defects and the timeout note never run into the closing hint.
        boolean numbers = start.mode() != SettingSession.Mode.REMOVE && ServerConfig.get(ServerConfig.SHOW_NUMERIC_QUALITY);
        int content = 34 + 22 + (numbers ? 11 : 0) + result.defects().size() * 10 + (result.timedOut() ? 11 : 0)
                + (start.mode() == SettingSession.Mode.SET ? 16 : 0) + 6;
        int bw = W - 48;
        int bh = Math.min(H - 8, Math.max(96, content));
        int bx = left + 24;
        int by = top + (H - bh) / 2;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xE0151310);
        JewelryGui.dialog(g, bx, by, bw, bh);
        g.renderItem(result.result(), bx + 12, by + 12);
        g.drawString(font, JewelryGui.clipped(font, result.result().getHoverName(), bw - 50), bx + 34, by + 12, JewelryGui.TEXT, false);
        int y = by + 34;
        if (start.mode() == SettingSession.Mode.REMOVE) {
            g.drawString(font, Component.translatable("screen.immersive_jewelry.removed", CutGrade.of(result.quality()).displayName()), bx + 12, y, JewelryGui.TEXT, false);
            y += 11;
            g.drawString(font, Component.translatable("screen.immersive_jewelry.removed_hint"), bx + 12, y, JewelryGui.TEXT_DIM, false);
            y += 11;
        } else {
            g.drawString(font, Component.translatable("screen.immersive_jewelry.setting_result", WorkmanshipGrade.of(result.quality()).displayName()),
                    bx + 12, y, JewelryGui.TEXT, false);
            y += 11;
            g.drawString(font, Component.translatable("tooltip.immersive_jewelry.workmanship", WorkmanshipGrade.of(result.workmanship()).displayName()),
                    bx + 12, y, JewelryGui.TEXT, false);
            y += 11;
            if (numbers) {
                g.drawString(font, Component.translatable("screen.immersive_jewelry.setting_numbers", Math.round(result.quality() * 100),
                        Math.round(result.workmanship() * 100)), bx + 12, y, JewelryGui.TEXT_DIM, false);
                y += 11;
            }
        }
        for (SettingDefect d : result.defects()) {
            g.drawString(font, Component.literal("• ").append(d.displayName()), bx + 12, y, JewelryGui.TEXT_DIM, false);
            y += 10;
        }
        if (result.timedOut()) g.drawString(font, Component.translatable("screen.immersive_jewelry.timed_out"), bx + 12, y, JewelryGui.TEXT_DIM, false);
        if (result.xp() > 0) g.drawString(font, Component.translatable("screen.immersive_jewelry.result_xp", result.xp()), bx + bw - 60, by + 34, 0xFF9CDB5A, false);
        if (start.mode() == SettingSession.Mode.SET) {
            g.drawString(font, Component.translatable("screen.immersive_jewelry.take_hint"), bx + 12, by + bh - 14, JewelryGui.TEXT_DIM, false);
        }
    }
}
