package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.client.workstation.render.DesignLayout;
import com.otectus.immersivejewelry.client.workstation.render.FrameRenderer;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.pockyl.diegesis.api.client.ScreenOverlay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The design folio: the bench's book of designs and its page of reworks. It lists the designs for the frame in the
 * clamp (or all of them), with favourites and a form filter, and a readable page for the chosen one: the stones it
 * needs, the cuts it accepts, its finishes (choose one here), whether its sketch is known (and where an unknown one
 * comes from), and what the tray already holds for it. "Stage stones" fetches matching stones from the inventory into
 * the tray, visibly and exactly; nothing here starts work. A finished piece in the clamp opens the rework page.
 * Everything is reachable by keys as well as by clicks.
 */
final class FolioOverlay implements ScreenOverlay {
    private enum Page { DESIGNS, REWORK }

    private final BenchBehavior bench;
    private final Runnable onClosed;
    private Page page;
    private boolean favouritesOnly;
    private int selected;
    private int finishChoice = -1;
    private int scroll;
    private final List<Button> buttons = new ArrayList<>();

    private record Button(int x, int y, int w, int h, Component label, Runnable action, boolean enabled) {
        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    FolioOverlay(BenchBehavior bench, Runnable onClosed) {
        this.bench = bench;
        this.onClosed = onClosed;
        JewelersBenchBlockEntity be = bench.bench();
        this.page = be != null && be.clamp().getItem() instanceof JewelryItem && bench.work() == null ? Page.REWORK : Page.DESIGNS;
        BenchWork work = bench.work();
        List<JewelryDesignRecipe> designs = designs();
        if (work != null && work.design() != null) {
            for (int i = 0; i < designs.size(); i++) if (designs.get(i).getId().equals(work.design())) selected = i;
        }
    }

    // ------------------------------------------------------------------------------------------------ data

    @Nullable
    private ResourceLocation clampForm() {
        JewelersBenchBlockEntity be = bench.bench();
        if (be == null) return null;
        ItemStack frame = be.clamp();
        if (frame.getItem() instanceof JewelryBlankItem blank) return blank.form();
        if (frame.getItem() instanceof JewelryItem item) return item.form();
        return null;
    }

    private List<JewelryDesignRecipe> designs() {
        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        ResourceLocation form = clampForm();
        List<? extends String> favourites = ClientConfig.FOLIO_FAVORITES.get();
        List<JewelryDesignRecipe> out = new ArrayList<>();
        for (JewelryDesignRecipe r : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get())) {
            if (form != null && !r.form().equals(form)) continue;
            if (favouritesOnly && !favourites.contains(r.getId().toString())) continue;
            out.add(r);
        }
        out.sort(Comparator.comparingInt(JewelryDesignRecipe::sortOrder).thenComparing(r -> r.getId().toString()));
        return out;
    }

    @Nullable
    private JewelryDesignRecipe current() {
        List<JewelryDesignRecipe> designs = designs();
        if (designs.isEmpty()) return null;
        selected = Mth.clamp(selected, 0, designs.size() - 1);
        return designs.get(selected);
    }

    private List<FinishDefinition> finishes(@Nullable JewelryDesignRecipe design) {
        List<FinishDefinition> out = new ArrayList<>();
        for (FinishDefinition f : JewelryDefinitions.client().finishes()) if (design == null || design.allowsFinish(f.id())) out.add(f);
        return out;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean contrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES);
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int w = Math.min(380, screenW - 24);
        int h = Math.min(250, screenH - 70);
        int x0 = 10;
        int y0 = 10;
        g.fill(x0, y0, x0 + w, y0 + h, contrast ? 0xF0000000 : 0xE6201810);
        g.renderOutline(x0, y0, w, h, contrast ? 0xFFFFFFFF : 0xFFB08A50);
        buttons.clear();
        // Tabs.
        tab(g, font, x0 + 6, y0 + 5, Component.translatable("workstation.immersive_jewelry.folio.designs"), page == Page.DESIGNS, () -> page = Page.DESIGNS);
        boolean piece = bench.bench() != null && bench.bench().clamp().getItem() instanceof JewelryItem;
        if (piece) tab(g, font, x0 + 86, y0 + 5, Component.translatable("workstation.immersive_jewelry.folio.rework"), page == Page.REWORK,
                () -> page = Page.REWORK);
        // Help: leaves the bench (nothing held, the work stays) and opens the handbook at the step under way.
        button(g, font, x0 + w - 78, y0 + 5, 14, 12, Component.literal("?"), () -> {
            JewelersBenchBlockEntity station = bench.bench();
            bench.closeFolio();
            if (station != null) com.otectus.immersivejewelry.client.guide.HandbookNavigator.help(station, bench::releaseAll, false, false);
        }, true);
        button(g, font, x0 + w - 60, y0 + 5, 54, 12, Component.translatable(favouritesOnly ? "workstation.immersive_jewelry.folio.starred"
                : "workstation.immersive_jewelry.folio.all"), () -> {
            favouritesOnly = !favouritesOnly;
            selected = 0;
        }, true);
        if (page == Page.DESIGNS) designsPage(g, font, x0, y0 + 22, w, h - 22, mouseX, mouseY, contrast);
        else reworkPage(g, font, x0, y0 + 22, w, h - 22);
        for (Button b : buttons) drawButton(g, font, b, b.over(mouseX, mouseY), contrast);
        g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.keys", WorkstationKeys.name(WorkstationKeys.CONFIRM),
                WorkstationKeys.name(WorkstationKeys.APPLY)), x0 + 6, y0 + h - 11, 0xFF8A8070, false);
    }

    private void designsPage(GuiGraphics g, Font font, int x0, int y0, int w, int h, int mouseX, int mouseY, boolean contrast) {
        List<JewelryDesignRecipe> designs = designs();
        List<? extends String> favourites = ClientConfig.FOLIO_FAVORITES.get();
        int listW = 128;
        int rows = Math.max(1, (h - 16) / 12);
        scroll = Mth.clamp(scroll, 0, Math.max(0, designs.size() - rows));
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + rows) scroll = selected - rows + 1;
        for (int i = scroll; i < designs.size() && i < scroll + rows; i++) {
            JewelryDesignRecipe d = designs.get(i);
            int y = y0 + (i - scroll) * 12;
            boolean known = WorkstationClient.knows(d);
            if (i == selected) g.fill(x0 + 4, y - 1, x0 + 4 + listW, y + 10, contrast ? 0xFF404000 : 0x60E8C46A);
            String star = favourites.contains(d.getId().toString()) ? "★ " : "  ";
            int color = known ? (contrast ? 0xFFFFFFFF : 0xFFF0E6D0) : 0xFF8A8070;
            g.drawString(font, Component.literal(star).append(d.name()), x0 + 6, y, color, false);
            int index = i;
            buttons.add(new Button(x0 + 4, y - 1, listW, 11, Component.empty(), () -> selected = index, true));
        }
        if (designs.isEmpty()) g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.none"), x0 + 6, y0, 0xFF8A8070, false);
        JewelryDesignRecipe design = current();
        if (design == null) return;
        int dx = x0 + listW + 12;
        int dw = w - listW - 18;
        int y = y0;
        g.drawString(font, design.name().copy().withStyle(ChatFormatting.BOLD), dx, y, 0xFFE8C46A, false);
        y += 12;
        schematic(g, dx + dw - 58, y, 54, design);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("workstation.immersive_jewelry.folio.form", JewelryNames.formName(JewelryDefinitions.client(), design.form())));
        lines.add(Component.translatable("workstation.immersive_jewelry.folio.stones", design.stones(),
                Component.translatable("workstation.immersive_jewelry.folio.style." + design.style().id()), design.pointsPerStone()));
        lines.add(Component.translatable("workstation.immersive_jewelry.folio.cuts", cuts(design)));
        lines.add(Component.translatable("workstation.immersive_jewelry.folio.same_gem"));
        if (design.sketch()) {
            lines.add(WorkstationClient.knows(design) ? Component.translatable("workstation.immersive_jewelry.folio.sketch_known").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("workstation.immersive_jewelry.folio.sketch_unknown").withStyle(ChatFormatting.GOLD));
        }
        int suitable = suitableInTray(design);
        lines.add(Component.translatable("workstation.immersive_jewelry.folio.tray", suitable, design.stones()));
        for (Component line : lines) {
            for (FormattedCharSequence part : font.split(line, dw - 62)) {
                g.drawString(font, part, dx, y, 0xFFE0D8C8, false);
                y += 10;
            }
        }
        y = Math.max(y, y0 + 78) + 4;
        g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.finish"), dx, y, 0xFFE8C46A, false);
        y += 11;
        List<FinishDefinition> finishes = finishes(design);
        BenchWork work = bench.work();
        ResourceLocation chosen = work != null ? work.finish() : null;
        int fx = dx;
        for (int i = 0; i < finishes.size(); i++) {
            FinishDefinition f = finishes.get(i);
            Component label = JewelryNames.finish(JewelryDefinitions.client(), f.id());
            int bw = font.width(label) + 10;
            if (fx + bw > dx + dw) {
                fx = dx;
                y += 14;
            }
            boolean on = f.id().equals(chosen) || i == finishChoice;
            int index = i;
            buttons.add(new Button(fx, y, bw, 12, on ? Component.literal("▶ ").append(label) : label, () -> chooseFinish(finishes, index), true));
            fx += bw + 4;
        }
        y += 18;
        boolean usable = clampForm() != null && clampForm().equals(design.form()) && bench.bench() != null
                && bench.bench().clamp().getItem() instanceof JewelryBlankItem && (work == null || !work.irreversible());
        buttons.add(new Button(dx, y, 96, 14, Component.translatable("workstation.immersive_jewelry.folio.use"), () -> use(design), usable
                && WorkstationClient.knows(design)));
        buttons.add(new Button(dx + 100, y, 96, 14, Component.translatable("workstation.immersive_jewelry.folio.stage"), () -> stage(design), true));
        y += 18;
        if (work != null) {
            if (!work.irreversible()) {
                buttons.add(new Button(dx, y, 96, 14, Component.translatable("workstation.immersive_jewelry.folio.cancel"),
                        () -> bench.send(WorkCommand.CANCEL_PLAN, new CompoundTag()), true));
            } else {
                buttons.add(new Button(dx, y, 96, 14, Component.translatable("workstation.immersive_jewelry.folio.pack"),
                        () -> bench.send(WorkCommand.PACK, new CompoundTag()), true));
            }
        }
        boolean starred = favourites.contains(design.getId().toString());
        buttons.add(new Button(dx + 100, y, 96, 14, Component.translatable(starred ? "workstation.immersive_jewelry.folio.unstar"
                : "workstation.immersive_jewelry.folio.star"), () -> star(design), true));
    }

    private void reworkPage(GuiGraphics g, Font font, int x0, int y0, int w, int h) {
        JewelersBenchBlockEntity be = bench.bench();
        JewelryData data = be == null ? null : JewelryData.get(be.clamp()).orElse(null);
        if (data == null) {
            g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.no_piece"), x0 + 6, y0, 0xFF8A8070, false);
            return;
        }
        int y = y0;
        g.drawString(font, be.clamp().getHoverName(), x0 + 6, y, 0xFFE8C46A, false);
        y += 14;
        rework(g, font, x0 + 6, y, "retighten", null);
        rework(g, font, x0 + 6, y += 18, "remove", null);
        rework(g, font, x0 + 6, y += 18, "repair", null);
        y += 22;
        g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.refinish"), x0 + 6, y, 0xFFE8C46A, false);
        y += 12;
        int fx = x0 + 6;
        for (FinishDefinition f : JewelryDefinitions.client().finishes()) {
            if (f.id().equals(data.finish())) continue;
            Component label = JewelryNames.finish(JewelryDefinitions.client(), f.id());
            int bw = font.width(label) + 10;
            CompoundTag args = new CompoundTag();
            args.putString("Kind", "refinish");
            args.putString("Finish", f.id().toString());
            buttons.add(new Button(fx, y, bw, 14, label, () -> {
                bench.send(WorkCommand.BEGIN, args);
                bench.closeFolio();
            }, true));
            fx += bw + 4;
        }
    }

    private void rework(GuiGraphics g, Font font, int x, int y, String kind, @Nullable ResourceLocation finish) {
        CompoundTag args = new CompoundTag();
        args.putString("Kind", kind);
        if (finish != null) args.putString("Finish", finish.toString());
        Component label = Component.translatable("workstation.immersive_jewelry.folio.rework." + kind);
        buttons.add(new Button(x, y, 150, 14, label, () -> {
            bench.send(WorkCommand.BEGIN, args);
            bench.closeFolio();
        }, true));
        g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.rework." + kind + ".about"), x + 158, y + 3, 0xFFB0A898, false);
    }

    private void tab(GuiGraphics g, Font font, int x, int y, Component label, boolean on, Runnable action) {
        int w = font.width(label) + 12;
        buttons.add(new Button(x, y, w, 13, on ? label.copy().withStyle(ChatFormatting.UNDERLINE) : label, action, true));
    }

    private void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, Runnable action, boolean enabled) {
        buttons.add(new Button(x, y, w, h, label, action, enabled));
    }

    private static void drawButton(GuiGraphics g, Font font, Button b, boolean hover, boolean contrast) {
        if (b.label().getString().isEmpty()) return;
        int bg = !b.enabled() ? 0x60303030 : hover ? (contrast ? 0xFF606000 : 0xC0705030) : (contrast ? 0xFF202020 : 0x90503820);
        g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), bg);
        g.renderOutline(b.x(), b.y(), b.w(), b.h(), contrast ? 0xFFFFFFFF : 0xFF8A6A40);
        int color = b.enabled() ? (contrast ? 0xFFFFFFFF : 0xFFF0E6D0) : 0xFF7A7A7A;
        g.drawString(font, b.label(), b.x() + (b.w() - font.width(b.label())) / 2, b.y() + (b.h() - 8) / 2, color, false);
    }

    /** The design's layout as a small drawing: the body's curve and its cups. */
    private void schematic(GuiGraphics g, int x, int y, int size, JewelryDesignRecipe design) {
        DesignLayout layout = DesignLayout.of(design.form(), design.getId(), design.stones(), design.style());
        FrameRenderer.Surface s = FrameRenderer.surface(layout.body());
        float scale = size / 12F;
        int cx = x + size / 2;
        int cy = y + size / 2;
        g.fill(x, y, x + size, y + size, 0x40000000);
        for (int i = 0; i <= 48; i++) {
            float a = s.a0() + (s.a1() - s.a0()) * i / 48F;
            float r = (s.rIn() + s.rOut()) * 0.5F;
            int px = cx + Math.round((s.cx() + Mth.cos(a) * r * s.sx()) * scale);
            int py = cy - Math.round((s.cy() + Mth.sin(a) * r * s.sy()) * scale);
            g.fill(px, py, px + 2, py + 2, 0xFFC8A060);
        }
        for (DesignLayout.Socket socket : layout.sockets()) {
            int px = cx + Math.round(socket.x() * scale);
            int py = cy - Math.round(socket.y() * scale);
            int r = Math.max(2, Math.round(socket.radius() * scale));
            g.fill(px - r, py - r, px + r, py + r, design.style() == SettingStyle.BEZEL ? 0xFFE8C46A : 0xFF8FD8E8);
        }
    }

    private Component cuts(JewelryDesignRecipe design) {
        if (design.allowedCuts().isEmpty()) return Component.translatable("workstation.immersive_jewelry.folio.any_cut");
        var out = Component.empty();
        for (int i = 0; i < design.allowedCuts().size(); i++) {
            if (i > 0) out.append(", ");
            out.append(JewelryNames.cut(JewelryDefinitions.client(), design.allowedCuts().get(i)));
        }
        return out;
    }

    private int suitableInTray(JewelryDesignRecipe design) {
        int n = 0;
        for (GemData g : bench.trayStones()) if (design.accepts(g)) n++;
        return n;
    }

    // ------------------------------------------------------------------------------------------------ actions

    private void use(JewelryDesignRecipe design) {
        CompoundTag args = new CompoundTag();
        args.putString("Design", design.getId().toString());
        bench.send(WorkCommand.SELECT_DESIGN, args);
        if (finishChoice >= 0) {
            List<FinishDefinition> finishes = finishes(design);
            if (finishChoice < finishes.size()) {
                CompoundTag f = new CompoundTag();
                f.putString("Finish", finishes.get(finishChoice).id().toString());
                bench.send(WorkCommand.SELECT_FINISH, f);
            }
        }
    }

    private void chooseFinish(List<FinishDefinition> finishes, int index) {
        finishChoice = index;
        BenchWork work = bench.work();
        if (work != null && index < finishes.size()) {
            CompoundTag args = new CompoundTag();
            args.putString("Finish", finishes.get(index).id().toString());
            bench.send(WorkCommand.SELECT_FINISH, args);
        }
    }

    /** Moves matching stones from the inventory into the tray, best cut first, never more than the design needs. */
    private void stage(JewelryDesignRecipe design) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        Inventory inv = player.getInventory();
        int need = design.stones() - suitableInTray(design);
        ResourceLocation gem = null;
        for (GemData g : bench.trayStones()) if (design.accepts(g)) gem = g.gem();
        List<int[]> candidates = new ArrayList<>();
        for (int i = 0; i < inv.items.size(); i++) {
            GemData g = CutGemItem.data(inv.items.get(i)).orElse(null);
            if (g == null || !design.accepts(g) || (gem != null && !gem.equals(g.gem()))) continue;
            candidates.add(new int[]{i, Math.round(g.cutQuality() * 1000)});
        }
        candidates.sort((a, b) -> Integer.compare(b[1], a[1]));
        for (int[] c : candidates) {
            if (need <= 0) break;
            GemData g = CutGemItem.data(inv.items.get(c[0])).orElse(null);
            if (g == null) continue;
            if (gem == null) gem = g.gem();
            if (!gem.equals(g.gem())) continue;
            CompoundTag args = new CompoundTag();
            args.putInt("From", c[0]);
            args.putString("Target", "tray");
            args.putInt("Index", -1);
            bench.send(WorkCommand.PLACE, args);
            need -= Math.min(need, inv.items.get(c[0]).getCount());
        }
        if (need > 0) bench.cueMissing(design.stones() - suitableInTray(design));
    }

    private void star(JewelryDesignRecipe design) {
        List<String> favourites = new ArrayList<>(ClientConfig.FOLIO_FAVORITES.get());
        String id = design.getId().toString();
        if (!favourites.remove(id)) favourites.add(id);
        ClientConfig.FOLIO_FAVORITES.set(favourites);
        ClientConfig.SPEC.save();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Button b : List.copyOf(buttons)) {
            if (b.over(mouseX, mouseY)) {
                if (b.enabled()) b.action().run();
                return true;
            }
        }
        // A click outside the folio closes it (the library takes a refused click as that).
        return mouseX < 10 + Math.min(380, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24) && mouseY < 260;
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll(amount);
    }

    void scroll(double amount) {
        selected = Math.max(0, selected - (int) Math.signum(amount));
    }

    /** Keyboard navigation while the folio is open. */
    boolean key(int key, int scan) {
        if (WorkstationKeys.RAISE.matches(key, scan)) {
            selected = Math.max(0, selected - 1);
            return true;
        }
        if (WorkstationKeys.LOWER.matches(key, scan)) {
            selected = Math.min(Math.max(0, designs().size() - 1), selected + 1);
            return true;
        }
        JewelryDesignRecipe design = current();
        if (WorkstationKeys.LEFT.matches(key, scan) || WorkstationKeys.RIGHT.matches(key, scan)) {
            List<FinishDefinition> finishes = finishes(design);
            if (!finishes.isEmpty()) {
                int next = Math.floorMod(Math.max(finishChoice, 0) + (WorkstationKeys.RIGHT.matches(key, scan) ? 1 : -1), finishes.size());
                chooseFinish(finishes, next);
            }
            return true;
        }
        if (WorkstationKeys.CONFIRM.matches(key, scan) && design != null) {
            use(design);
            return true;
        }
        if (WorkstationKeys.APPLY.matches(key, scan) && design != null) {
            star(design);
            return true;
        }
        if (WorkstationKeys.TOOL.matches(key, scan)) {
            page = page == Page.DESIGNS ? Page.REWORK : Page.DESIGNS;
            return true;
        }
        if (WorkstationKeys.NEXT.matches(key, scan) && design != null) {
            stage(design);
            return true;
        }
        if (WorkstationKeys.FOLIO.matches(key, scan)) {
            bench.closeFolio();
            return true;
        }
        return false;
    }

    @Override
    public void onClosed() {
        onClosed.run();
    }
}
