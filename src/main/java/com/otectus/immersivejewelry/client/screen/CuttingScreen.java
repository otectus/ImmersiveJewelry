package com.otectus.immersivejewelry.client.screen;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.client.JewelryColors;
import com.otectus.immersivejewelry.client.sound.GrindLoopSound;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutPattern;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.jewelry.DescriptionBuilder;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.minigame.cutting.CutParams;
import com.otectus.immersivejewelry.minigame.cutting.CutSimulation;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.CutInputPacket;
import com.otectus.immersivejewelry.network.packet.CutResultPacket;
import com.otectus.immersivejewelry.network.packet.CutStartPacket;
import com.otectus.immersivejewelry.network.packet.WheelActionPacket;
import com.otectus.immersivejewelry.quality.CutGrade;
import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The cutting minigame (spec 10.2): the stone, magnified. Move the mouse to press it against the wheel, hold the left
 * button (or Space) to grind. Keep the diamond marker - where the stone really touches, wobbling under pressure - on
 * the highlighted facet; release when its depth needle sits inside the brass band. Watch the heat gauge: past its
 * mark the stone chips. On a vanilla container panel: the stone in a dark display, the gauges as vanilla bars beside
 * it, and feedback in a dark readout.
 */
public class CuttingScreen extends MinigameScreen {
    private static final int W = 400;
    private static final int H = 244;
    /** The gauge column, right of the stone. */
    private static final int GX = 212;
    private static final int GW = W - 8 - GX;

    private final CutStartPacket start;
    private final CutSimulation sim;
    private final short[] xs;
    private final short[] ys;
    private final boolean[] grind;
    private final int gemColor;
    private float cx;
    private float cy;
    private float radius;
    private int mouseFx = CutSimulation.toFixed(0F);
    private int mouseFy = CutSimulation.toFixed(0F);
    private CutResultPacket result;
    private String flash = "";
    private long flashUntil;
    private boolean cueGiven;
    private GrindLoopSound loop;

    public CuttingScreen(CutStartPacket packet) {
        super(Component.translatable("screen.immersive_jewelry.cutting"), packet.sessionId(), packet.readyMs(), W, H);
        this.start = packet;
        this.sim = new CutSimulation(packet.params());
        int max = packet.params().maxSteps();
        this.xs = new short[max];
        this.ys = new short[max];
        this.grind = new boolean[max];
        this.gemColor = 0xFF000000 | JewelryColors.gemColor(JewelryDefinitions.client(), packet.stone().gem());
    }

    @Override
    protected void initPanel() {
        radius = 82F;
        cx = 105;
        cy = 128;
        if (loop == null) {
            loop = new GrindLoopSound(this);
            Minecraft.getInstance().getSoundManager().play(loop);
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        if (result != null) {
            addRenderableWidget(VanillaGui.button(left + W - 104, top + H - 28, 96, 20, Component.translatable("screen.immersive_jewelry.take_stone"), b -> {
                ModNetwork.CHANNEL.sendToServer(new WheelActionPacket(start.pos()));
                onClose();
            }));
            addRenderableWidget(VanillaGui.button(left + W - 204, top + H - 28, 96, 20, Component.translatable("gui.done"), b -> onClose()));
        }
    }

    public boolean grinding() {
        return pressed && !finished && !counting() && !localComplete;
    }

    public float pressure() {
        return sim.pressure();
    }

    public CutParams params() {
        return start.params();
    }

    @Override
    protected int maxSteps() {
        return start.params().maxSteps();
    }

    @Override
    protected boolean simulate(int index, boolean down) {
        if (scriptedInput != null) {
            int[] in = scriptedInput.apply(index);
            mouseFx = in[0];
            mouseFy = in[1];
            down = in[2] != 0;
        }
        xs[index] = (short) mouseFx;
        ys[index] = (short) mouseFy;
        grind[index] = down;
        int events = sim.step(mouseFx, mouseFy, down);
        if ((events & CutSimulation.EVENT_CHIP) != 0) {
            shake = 1F;
            flash("chip");
        } else if ((events & CutSimulation.EVENT_OVERCUT) != 0) {
            flash("overcut");
        } else if ((events & CutSimulation.EVENT_FACET) != 0) {
            flash("facet");
            JewelryGui.playUi(ModSounds.WHEEL_FACET.get(), 1.2F, 0.5F);
        }
        if ((events & (CutSimulation.EVENT_FACET | CutSimulation.EVENT_OVERCUT)) != 0) cueGiven = false;
        int facet = sim.activeFacet();
        float lo = start.params().windowCenter()[facet] - start.params().windowHalf();
        if (!cueGiven && sim.progress(facet) >= lo) {
            cueGiven = true;
            JewelryGui.playCue();
        }
        return sim.isComplete();
    }

    private void flash(String key) {
        flash = key;
        flashUntil = Util.getMillis() + com.otectus.immersivejewelry.config.ClientConfig.get(com.otectus.immersivejewelry.config.ClientConfig.FEEDBACK_LABEL_MS);
    }

    @Override
    protected void send(int first, int end) {
        int n = end - first;
        short[] bx = new short[n];
        short[] by = new short[n];
        long bits = 0L;
        for (int i = 0; i < n; i++) {
            bx[i] = xs[first + i];
            by[i] = ys[first + i];
            if (grind[first + i]) bits |= 1L << i;
        }
        ModNetwork.CHANNEL.sendToServer(new CutInputPacket(sessionId, first, bx, by, bits));
    }

    public void onResult(CutResultPacket packet) {
        result = packet;
        finished = true;
        rebuildButtons();
        JewelryGui.playUi(ModSounds.WHEEL_FINISH.get(), 1F, 0.8F);
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        mouseFx = CutSimulation.toFixed((mouseX - cx) / radius);
        mouseFy = CutSimulation.toFixed((mouseY - cy) / radius);
        g.pose().pushPose();
        g.pose().translate(shakeOffset(false), shakeOffset(true), 0);
        VanillaGui.panel(g, left, top, W, H);
        JewelryDefinitions defs = JewelryDefinitions.client();
        renderTitle(g, Component.translatable("screen.immersive_jewelry.cutting_title", JewelryNames.cut(defs, start.cut()),
                JewelryNames.gem(defs, start.stone().gem())), W - 16, mouseX, mouseY);
        VanillaGui.display(g, 8, 19, 194, H - 27);
        drawStone(g);
        drawGauges(g);
        if (counting()) drawCountdown(g);
        if (result != null) {
            g.fill(4, 18, W - 4, H - 4, 0xB0101010);
            drawResult(g);
        }
        g.pose().popPose();
        super.renderPanel(g, mouseX, mouseY, partialTick);
    }

    private void drawStone(GuiGraphics g) {
        CutParams p = start.params();
        int k = p.facets();
        JewelryGui.disc(g, cx, cy, radius + 6, 0xFF1C1C1C);
        JewelryGui.ring(g, cx, cy, radius + 6, JewelryGui.BRASS_DARK);
        // The stone's outline.
        float[] outline = outline(p, radius);
        JewelryGui.polygon(g, outline, JewelryGui.scale(gemColor, 0.55F));
        // Facets.
        int active = sim.activeFacet();
        float r0 = p.innerRadius() * radius;
        for (int i = 0; i < k; i++) {
            float a = p.angles()[i];
            float w = p.halfWidths()[i];
            int color;
            if (sim.isLocked(i)) {
                color = JewelryGui.scale(gemColor, 0.95F + 0.25F * Math.min(1F, sim.progress(i)));
            } else if (i == active) {
                color = JewelryGui.scale(gemColor, 0.8F);
            } else {
                color = JewelryGui.scale(gemColor, 0.62F);
            }
            JewelryGui.polygon(g, JewelryGui.wedge(cx, cy, r0, radius, a - w, a + w), color);
            // Depth: the ground part of the facet, from the rim inward.
            float depth = Math.min(1.2F, sim.progress(i)) / 1.2F;
            if (depth > 0.01F) {
                float r1 = radius - (radius - r0) * depth;
                JewelryGui.polygon(g, JewelryGui.wedge(cx, cy, r1, radius, a - w, a + w),
                        JewelryGui.withAlpha(JewelryGui.lighten(gemColor, 60), sim.isLocked(i) ? 150 : 110));
            }
            if (sim.scratch(i) > 0.05F) {
                float sa = a + w * 0.3F;
                JewelryGui.line(g, cx + Mth.cos(sa) * (r0 + 4), cy + Mth.sin(sa) * (r0 + 4), cx + Mth.cos(sa - w * 0.5F) * (radius - 4),
                        cy + Mth.sin(sa - w * 0.5F) * (radius - 4), 1, 0x90E0E0E0);
            }
            if (sim.isLocked(i)) {
                float mx = cx + Mth.cos(a) * (radius + 1) * 0.84F;
                float my = cy + Mth.sin(a) * (radius + 1) * 0.84F;
                JewelryGui.check(g, Math.round(mx) - 3, Math.round(my) - 2, 0xFFFFFFFF);
            }
        }
        // The table (flat top) inside the facets.
        JewelryGui.disc(g, cx, cy, r0 - 1, JewelryGui.scale(gemColor, 1.1F));
        JewelryGui.outline(g, outline, JewelryGui.lighten(gemColor, 70));
        // Active facet: its contact region in brass, its centre line dashed.
        if (!sim.isComplete()) {
            float a = p.angles()[active];
            float w = p.halfWidths()[active];
            boolean pulse = !JewelryGui.reducedMotion() && (Util.getMillis() / 300) % 2 == 0;
            int edge = pulse ? JewelryGui.targetColor() : JewelryGui.BRASS;
            JewelryGui.outline(g, JewelryGui.wedge(cx, cy, r0, radius, a - w, a + w), edge);
            JewelryGui.dashed(g, cx + Mth.cos(a) * r0, cy + Mth.sin(a) * r0, cx + Mth.cos(a) * radius, cy + Mth.sin(a) * radius, edge);
            // Orientation arrow just outside the stone, its tip pointing in at the facet to work.
            float c = Mth.cos(a);
            float sn = Mth.sin(a);
            float bx = cx + c * (radius + 10);
            float by = cy + sn * (radius + 10);
            JewelryGui.polygon(g, new float[]{cx + c * (radius + 3), cy + sn * (radius + 3), bx - sn * 4, by + c * 4, bx + sn * 4, by - c * 4}, edge);
        }
        // Where the wheel really touches (wobbling under pressure), and the cursor it follows.
        float scale = JewelryGui.markerScale();
        float contactX = cx + sim.contactX() * radius;
        float contactY = cy + sim.contactY() * radius;
        float mx = cx + CutSimulation.toStone(mouseFx) * radius;
        float my = cy + CutSimulation.toStone(mouseFy) * radius;
        if (!counting() && !finished) {
            JewelryGui.line(g, mx, my, contactX, contactY, 1, 0x60FFFFFF);
            JewelryGui.line(g, mx - 3, my, mx + 3, my, 1, 0xB0FFFFFF);
            JewelryGui.line(g, mx, my - 3, mx, my + 3, 1, 0xB0FFFFFF);
            boolean grinding = grinding() && sim.pressure() > 0.05F;
            JewelryGui.diamond(g, contactX, contactY, (grinding ? 4.5F : 3.5F) * scale, grinding ? 0xFFFFFFFF : 0xC0FFFFFF);
            JewelryGui.ring(g, contactX, contactY, (5F + 6F * sim.pressure()) * scale, JewelryGui.withAlpha(0xFFFFFF, 90 + (int) (120 * sim.pressure())));
        }
    }

    /** The girdle outline: a polygon through the gaps between facets (round cuts), a step octagon, or a circle (dome). */
    private float[] outline(CutParams p, float r) {
        int k = p.facets();
        if (p.pattern() == CutPattern.DOME) {
            int n = 40;
            float[] pts = new float[n * 2];
            for (int i = 0; i < n; i++) {
                pts[i * 2] = cx + Mth.cos(i * Mth.TWO_PI / n) * r;
                pts[i * 2 + 1] = cy + Mth.sin(i * Mth.TWO_PI / n) * r;
            }
            return pts;
        }
        float[] pts = new float[k * 2];
        float spacing = Mth.TWO_PI / k;
        for (int i = 0; i < k; i++) {
            float a = p.angles()[i] + spacing / 2F;
            pts[i * 2] = cx + Mth.cos(a) * r;
            pts[i * 2 + 1] = cy + Mth.sin(a) * r;
        }
        return pts;
    }

    private void drawGauges(GuiGraphics g) {
        CutParams p = start.params();
        int gx = GX;
        int barW = GW;
        int y = 21;
        int facet = sim.activeFacet();

        g.drawString(font, Component.translatable("screen.immersive_jewelry.facet_count", Math.min(sim.lockedCount() + 1, p.facets()), p.facets()),
                gx, y, VanillaGui.LABEL, false);
        y += 14;

        // Depth gauge with the target band.
        g.drawString(font, Component.translatable("screen.immersive_jewelry.depth"), gx, y, VanillaGui.LABEL, false);
        y += 10;
        VanillaGui.inset(g, gx - 1, y - 1, barW + 2, 12);
        g.fill(gx, y, gx + barW, y + 10, VanillaGui.TRACK);
        float max = 1.2F;
        float lo = (p.windowCenter()[facet] - p.windowHalf()) / max;
        float hi = (p.windowCenter()[facet] + p.windowHalf()) / max;
        g.fill(gx + Math.round(barW * lo), y, gx + Math.round(barW * hi), y + 10, JewelryGui.BAND);
        g.fill(gx + Math.round(barW * (p.windowCenter()[facet] / max)), y, gx + Math.round(barW * (p.windowCenter()[facet] / max)) + 1, y + 10, JewelryGui.BRASS_LIGHT);
        float depth = Math.min(1F, sim.progress(facet) / max);
        int needle = gx + Math.min(barW - 1, Math.round(barW * depth));
        boolean inWindow = depth >= lo && depth <= hi;
        g.fill(gx, y + 3, needle, y + 7, inWindow ? JewelryGui.goodColor() : depth > hi ? JewelryGui.missColor() : JewelryGui.PRESSURE);
        g.fill(needle, y, needle + 1, y + 10, 0xFFFFFFFF);
        if (inWindow) g.drawString(font, Component.translatable("screen.immersive_jewelry.release_now"), gx, y + 13, JewelryGui.goodText(), false);
        y += 26;

        // Pressure.
        g.drawString(font, Component.translatable("screen.immersive_jewelry.pressure"), gx, y, VanillaGui.LABEL, false);
        y += 10;
        VanillaGui.bar(g, gx, y, barW, 6, sim.pressure(), JewelryGui.PRESSURE);
        y += 14;

        // Heat, with the chip threshold marked.
        g.drawString(font, Component.translatable("screen.immersive_jewelry.heat"), gx, y, VanillaGui.LABEL, false);
        y += 10;
        float heat = sim.heat() / 1.3F;
        float chip = p.chipThreshold() / 1.3F;
        boolean hot = sim.heat() > p.chipThreshold() - 0.15F;
        VanillaGui.bar(g, gx, y, barW, 6, heat, hot ? JewelryGui.missColor() : 0xFFE0A050);
        int mark = gx + Math.min(barW - 1, Math.round(barW * chip));
        g.fill(mark, y, mark + 1, y + 6, 0xFFFFFFFF);
        JewelryGui.polygon(g, new float[]{mark - 3, y - 6, mark + 4, y - 6, mark + 0.5F, y - 2}, VanillaGui.LABEL);
        if (hot && (JewelryGui.reducedMotion() || (Util.getMillis() / 250) % 2 == 0)) {
            g.drawString(font, Component.translatable("screen.immersive_jewelry.too_hot"), gx, y + 9, JewelryGui.missText(), false);
        }
        y += 22;

        g.drawString(font, Component.translatable("screen.immersive_jewelry.chips", sim.chips()), gx, y, sim.chips() > 0 ? JewelryGui.missText() : VanillaGui.LABEL, false);
        y += 14;

        // Time.
        float remaining = 1F - step / (float) maxSteps();
        JewelryGui.timeBar(g, gx, y, barW, 5, remaining);
        y += 12;

        // Feedback and the hint, in a dark readout so the rating colours keep their contrast.
        int readoutTop = y + 3;
        VanillaGui.display(g, gx - 1, readoutTop, barW + 2, H - 8 - readoutTop);
        if (Util.getMillis() < flashUntil && !flash.isEmpty()) {
            int color = flash.equals("facet") ? JewelryGui.goodColor() : JewelryGui.missColor();
            g.drawString(font, Component.translatable("screen.immersive_jewelry.flash." + flash), gx + 4, readoutTop + 5, color, false);
        }
        if (result == null) {
            Component hint = awaitingResult ? Component.translatable("screen.immersive_jewelry.awaiting")
                    : Component.translatable("screen.immersive_jewelry.cut_hint");
            int hintY = readoutTop + 17;
            VanillaGui.wrapped(g, font, hint, gx + 4, hintY, barW - 6, VanillaGui.DISPLAY_TEXT_DIM, (H - 12 - hintY) / 10);
        }
    }

    private void drawCountdown(GuiGraphics g) {
        int n = (int) Math.ceil(countdown());
        Component text = n > 0 ? Component.literal(String.valueOf(n)) : Component.translatable("screen.immersive_jewelry.go");
        g.pose().pushPose();
        g.pose().translate(cx, cy - 8, 0);
        g.pose().scale(2.5F, 2.5F, 1F);
        g.drawCenteredString(font, text, 0, 0, JewelryGui.CUE);
        g.pose().popPose();
    }

    private record Line(Component text, int color) {}

    /** The verdict on a small vanilla panel: the stone in a slot, and its grade and numbers in a dark readout. */
    private void drawResult(GuiGraphics g) {
        int bw = W - 60;
        int bx = left + 30;
        GemData after = result.after();
        if (after == null) {
            int bh = 36;
            int by = top + (H - 30 - bh) / 2;
            VanillaGui.panel(g, bx, by, bw, bh);
            VanillaGui.centered(g, font, Component.translatable("screen.immersive_jewelry.shattered"), bx + bw / 2, by + 14, JewelryGui.missText());
            return;
        }
        CutGrade grade = CutGrade.of(result.quality());
        ItemStack gem = CutGemItem.create(after);
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(grade.displayName(), VanillaGui.DISPLAY_TEXT));
        if (ServerConfig.get(ServerConfig.SHOW_NUMERIC_QUALITY)) {
            lines.add(new Line(Component.translatable("screen.immersive_jewelry.result_quality", Math.round(result.quality() * 100)), VanillaGui.DISPLAY_TEXT_DIM));
        }
        lines.add(new Line(Component.translatable("screen.immersive_jewelry.result_size", DescriptionBuilder.trim(result.before().size()),
                DescriptionBuilder.trim(after.size())), VanillaGui.DISPLAY_TEXT_DIM));
        if (result.chips() > 0) lines.add(new Line(Component.translatable("screen.immersive_jewelry.result_chips", result.chips()), JewelryGui.missColor()));
        if (after.damage() != com.otectus.immersivejewelry.api.gem.GemDamage.NONE) {
            lines.add(new Line(Component.translatable("tooltip.immersive_jewelry.stone_damage", after.damage().displayName()), VanillaGui.DISPLAY_TEXT_DIM));
        }
        if (result.timedOut()) lines.add(new Line(Component.translatable("screen.immersive_jewelry.timed_out"), VanillaGui.DISPLAY_TEXT_DIM));
        int displayHeight = lines.size() * 11 + 7;
        int bh = 30 + displayHeight + 8;
        int by = top + (H - 30 - bh) / 2;
        VanillaGui.panel(g, bx, by, bw, bh);
        VanillaGui.slot(g, bx + 8, by + 7);
        g.renderItem(gem, bx + 9, by + 8);
        g.drawString(font, VanillaGui.clipped(font, gem.getHoverName(), bw - 38), bx + 30, by + 12, VanillaGui.LABEL, false);
        VanillaGui.display(g, bx + 8, by + 30, bw - 16, displayHeight);
        int y = by + 35;
        int textWidth = bw - 28 - (result.xp() > 0 ? 50 : 0);
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            int width = i == 0 ? textWidth : bw - 28;
            // Unclipped lines keep their own colours (the grade's, the damage's).
            Component shown = VanillaGui.isClipped(font, line.text(), width) ? Component.literal(VanillaGui.clipped(font, line.text(), width)) : line.text();
            g.drawString(font, shown, bx + 14, y, line.color(), false);
            y += 11;
        }
        if (result.xp() > 0) {
            Component xp = Component.translatable("screen.immersive_jewelry.result_xp", result.xp());
            g.drawString(font, xp, bx + bw - 14 - font.width(xp), by + 35, JewelryGui.XP, false);
        }
    }

    @Override
    public void removed() {
        if (loop != null) loop.stopLoop();
        super.removed();
    }
}
