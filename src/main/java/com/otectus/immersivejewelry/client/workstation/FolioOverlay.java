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
import com.otectus.immersivejewelry.util.Inventories;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.pockyl.diegesis.api.client.ScreenOverlay;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The design folio: the bench's book of designs and its page of reworks. It lists the designs (searchable by name, form, gem,
 * cut, finish or id; filtered to known sketches, to the frame in the clamp, to designs whose stones the player has, and to
 * favourites), with a readable page for the chosen one: the stones it needs, the cuts it accepts, its finishes (choose one
 * here), whether its sketch is known (and where an unknown one comes from), and what the tray already holds for it. The chosen
 * design is held by its id, so a filter, a new language or a reload never quietly selects another one; a design that is gone
 * is simply not chosen any more.
 *
 * <p>Preparing a design's stones shows the real candidates (gem, cut, quality, damage, where they are), lets the player pick
 * the gem and the stones they prefer or keep the best cuts, and says which finishing material the piece will use and when
 * (nothing is paid here). Its button sends one request; the server counts again, moves exactly the stones still needed into the
 * tray and says what moved and what is missing. Nothing in the folio starts work: the design is checked again at the first
 * closure. A finished piece in the clamp opens the rework page. Everything is reachable by keys as well as by clicks.</p>
 *
 * <p>Typing: the search field has the keyboard only after a click on it or the search key ({@code /} or Control+F); entering it
 * lets go of every held tool. While it has the keyboard every key is text (no station, help or recipe viewer key acts), the
 * first Escape (or Enter) leaves the field, and only the next Escape closes the folio.</p>
 */
final class FolioOverlay implements ScreenOverlay {
    private enum Page { DESIGNS, PREPARE, REWORK }

    private static final int MAX_QUERY = 48;
    private static final int MAX_CANDIDATES = 6;

    private final BenchBehavior bench;
    private final Runnable onClosed;
    private Page page;
    private boolean favouritesOnly;
    private boolean knownOnly;
    private boolean fitsFrame = true;
    private boolean haveStones;
    @Nullable
    private ResourceLocation selected;
    private int finishChoice = -1;
    private int scroll;
    private final List<Button> buttons = new ArrayList<>();
    private final EditBox search;
    private long lastTick;
    // Preparation.
    @Nullable
    private ResourceLocation gemChoice;
    private final List<Integer> preferred = new ArrayList<>();
    private boolean fromStaging;
    private int candidateCursor;
    // The filtered list, kept until what it was made from changes.
    private List<JewelryDesignRecipe> results = List.of();
    @Nullable
    private Object resultStamp;

    private record Button(int x, int y, int w, int h, Component label, Runnable action, boolean enabled) {
        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** A stone a preparation could use: where it is ({@code staged}, or an inventory slot) and what it is. */
    private record Candidate(boolean staged, int index, GemData gem, int count) {}

    FolioOverlay(BenchBehavior bench, Runnable onClosed) {
        this.bench = bench;
        this.onClosed = onClosed;
        JewelersBenchBlockEntity be = bench.bench();
        this.page = be != null && be.clamp().getItem() instanceof JewelryItem && bench.work() == null ? Page.REWORK : Page.DESIGNS;
        BenchWork work = bench.work();
        if (work != null) selected = work.design();
        Font font = Minecraft.getInstance().font;
        search = new EditBox(font, 0, 0, 120, 12, Component.translatable("workstation.immersive_jewelry.folio.search"));
        search.setMaxLength(MAX_QUERY);
        search.setHint(Component.translatable("workstation.immersive_jewelry.folio.search_hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setFocused(false);
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

    /** The designs the list shows now: searched and filtered from the cached catalogue, made again only when needed. */
    private List<JewelryDesignRecipe> designs() {
        var player = Minecraft.getInstance().player;
        JewelersBenchBlockEntity be = bench.bench();
        ResourceLocation form = fitsFrame ? clampForm() : null;
        List<? extends String> favourites = ClientConfig.FOLIO_FAVORITES.get();
        Object stamp = List.of(FolioCatalogue.version(), search.getValue(), favouritesOnly, knownOnly, haveStones, Objects.toString(form),
                favourites.hashCode(), WorkstationClient.knownVersion(), haveStones && player != null ? player.getInventory().getTimesChanged() : 0,
                haveStones && be != null ? be.clientVersion() : 0);
        if (stamp.equals(resultStamp)) return results;
        String[] words = FolioCatalogue.words(search.getValue());
        List<JewelryDesignRecipe> out = new ArrayList<>();
        for (FolioCatalogue.Entry e : FolioCatalogue.all()) {
            JewelryDesignRecipe r = e.design();
            if (form != null && !r.form().equals(form)) continue;
            if (favouritesOnly && !favourites.contains(r.getId().toString())) continue;
            if (knownOnly && !WorkstationClient.knows(r)) continue;
            if (!e.matches(words)) continue;
            if (haveStones && bestFamilyCount(r) < r.stones()) continue;
            out.add(r);
        }
        results = List.copyOf(out);
        resultStamp = stamp;
        return results;
    }

    /** The chosen design, if it is in the list now; a filter that hides it leaves nothing chosen rather than another one. */
    @Nullable
    private JewelryDesignRecipe current() {
        List<JewelryDesignRecipe> designs = designs();
        if (selected == null && !designs.isEmpty()) selected = designs.get(0).getId();
        for (JewelryDesignRecipe d : designs) if (d.getId().equals(selected)) return d;
        return null;
    }

    private int selectedIndex(List<JewelryDesignRecipe> designs) {
        for (int i = 0; i < designs.size(); i++) if (designs.get(i).getId().equals(selected)) return i;
        return -1;
    }

    private void move(int by) {
        List<JewelryDesignRecipe> designs = designs();
        if (designs.isEmpty()) return;
        int at = selectedIndex(designs);
        int next = at < 0 ? 0 : Mth.clamp(at + by, 0, designs.size() - 1);
        selected = designs.get(next).getId();
    }

    private List<FinishDefinition> finishes(@Nullable JewelryDesignRecipe design) {
        List<FinishDefinition> out = new ArrayList<>();
        for (FinishDefinition f : JewelryDefinitions.client().finishes()) if (design == null || design.allowsFinish(f.id())) out.add(f);
        return out;
    }

    /** The gem already chosen on the bench for this design: the stones seated for it or lying in the tray. */
    @Nullable
    private ResourceLocation benchFamily(JewelryDesignRecipe design) {
        JewelersBenchBlockEntity be = bench.bench();
        if (be == null) return null;
        BenchWork work = bench.work();
        if (work != null && design.getId().equals(work.design())) {
            for (int s = 0; s < design.stones(); s++) {
                GemData g = CutGemItem.data(be.socket(s)).orElse(null);
                if (g != null && design.accepts(g)) return g.gem();
            }
        }
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) {
            GemData g = CutGemItem.data(be.tray(i)).orElse(null);
            if (g != null && design.accepts(g)) return g.gem();
        }
        return null;
    }

    /** Accepted stones of the bench's gem already in the sockets and the tray (as the server counts them). */
    private int onBench(JewelryDesignRecipe design) {
        JewelersBenchBlockEntity be = bench.bench();
        if (be == null) return 0;
        ResourceLocation family = benchFamily(design);
        int have = 0;
        BenchWork work = bench.work();
        if (work != null && design.getId().equals(work.design())) {
            for (int s = 0; s < design.stones(); s++) {
                GemData g = CutGemItem.data(be.socket(s)).orElse(null);
                if (g != null && design.accepts(g) && g.gem().equals(family)) have++;
            }
        }
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) {
            GemData g = CutGemItem.data(be.tray(i)).orElse(null);
            if (g != null && design.accepts(g) && g.gem().equals(family)) have += be.tray(i).getCount();
        }
        return have;
    }

    /** The stones a preparation could take: the inventory's, and the drawer's when asked for. */
    private List<Candidate> candidates(JewelryDesignRecipe design, boolean withStaged) {
        List<Candidate> out = new ArrayList<>();
        var player = Minecraft.getInstance().player;
        if (player == null) return out;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            GemData g = CutGemItem.data(inv.items.get(i)).orElse(null);
            if (g != null && design.accepts(g)) out.add(new Candidate(false, i, g, inv.items.get(i).getCount()));
        }
        JewelersBenchBlockEntity be = bench.bench();
        if (withStaged && be != null) {
            for (int i = 0; i < JewelersBenchBlockEntity.STAGING; i++) {
                GemData g = CutGemItem.data(be.staged(i)).orElse(null);
                if (g != null && design.accepts(g)) out.add(new Candidate(true, i, g, be.staged(i).getCount()));
            }
        }
        out.sort((a, b) -> Float.compare(b.gem().cutQuality(), a.gem().cutQuality()));
        return out;
    }

    /** The most stones of one accepted gem the player has for a design, bench and inventory together (the "have stones" filter). */
    private int bestFamilyCount(JewelryDesignRecipe design) {
        ResourceLocation family = benchFamily(design);
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        for (Candidate c : candidates(design, true)) counts.merge(c.gem().gem(), c.count(), Integer::sum);
        int bench = onBench(design);
        if (family != null) return bench + counts.getOrDefault(family, 0);
        int best = 0;
        for (int n : counts.values()) best = Math.max(best, n);
        return best;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean contrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES);
        long now = Util.getMillis();
        if (now - lastTick >= 50L) {
            search.tick();
            lastTick = now;
        }
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int w = Math.min(380, screenW - 24);
        int h = Math.min(250, screenH - 70);
        int x0 = 10;
        int y0 = 10;
        g.fill(x0, y0, x0 + w, y0 + h, contrast ? 0xF0000000 : 0xE6201810);
        g.renderOutline(x0, y0, w, h, contrast ? 0xFFFFFFFF : 0xFFB08A50);
        buttons.clear();
        tab(font, x0 + 6, y0 + 5, Component.translatable("workstation.immersive_jewelry.folio.designs"), page != Page.REWORK, () -> page = Page.DESIGNS);
        boolean piece = bench.bench() != null && bench.bench().clamp().getItem() instanceof JewelryItem;
        if (piece) tab(font, x0 + 86, y0 + 5, Component.translatable("workstation.immersive_jewelry.folio.rework"), page == Page.REWORK,
                () -> page = Page.REWORK);
        // Help: leaves the bench (nothing held, the work stays) and opens the handbook at the step under way.
        buttons.add(new Button(x0 + w - 78, y0 + 5, 14, 12, Component.literal("?"), () -> {
            JewelersBenchBlockEntity station = bench.bench();
            bench.closeFolio();
            if (station != null) com.otectus.immersivejewelry.client.guide.HandbookNavigator.help(station, bench::releaseAll, false, false);
        }, true));
        buttons.add(new Button(x0 + w - 60, y0 + 5, 54, 12, Component.translatable(favouritesOnly ? "workstation.immersive_jewelry.folio.starred"
                : "workstation.immersive_jewelry.folio.all"), () -> favouritesOnly = !favouritesOnly, true));
        if (page == Page.REWORK) reworkPage(g, font, x0, y0 + 22, w);
        else designsPage(g, font, x0, y0 + 22, w, h - 22, mouseX, mouseY, contrast, partialTick);
        for (Button b : buttons) drawButton(g, font, b, b.over(mouseX, mouseY), contrast);
        Component keys = search.isFocused() ? Component.translatable("workstation.immersive_jewelry.folio.typing")
                : Component.translatable("workstation.immersive_jewelry.folio.keys", WorkstationKeys.name(WorkstationKeys.CONFIRM),
                WorkstationKeys.name(WorkstationKeys.APPLY));
        g.drawString(font, keys, x0 + 6, y0 + h - 11, 0xFF8A8070, false);
    }

    private void designsPage(GuiGraphics g, Font font, int x0, int y0, int w, int h, int mouseX, int mouseY, boolean contrast, float partialTick) {
        int listW = 128;
        // Search and filters.
        search.setX(x0 + 5);
        search.setY(y0);
        search.setWidth(listW);
        search.render(g, mouseX, mouseY, partialTick);
        int fy = y0 + 15;
        chip(font, x0 + 5, fy, 40, "workstation.immersive_jewelry.folio.filter_known", knownOnly, () -> knownOnly = !knownOnly);
        chip(font, x0 + 47, fy, 40, "workstation.immersive_jewelry.folio.filter_frame", fitsFrame, () -> fitsFrame = !fitsFrame);
        chip(font, x0 + 89, fy, 44, "workstation.immersive_jewelry.folio.filter_stones", haveStones, () -> haveStones = !haveStones);
        List<JewelryDesignRecipe> designs = designs();
        List<? extends String> favourites = ClientConfig.FOLIO_FAVORITES.get();
        int listY = y0 + 30;
        int rows = Math.max(1, (h - 46) / 12);
        int at = selectedIndex(designs);
        scroll = Mth.clamp(scroll, 0, Math.max(0, designs.size() - rows));
        if (at >= 0 && at < scroll) scroll = at;
        if (at >= scroll + rows) scroll = at - rows + 1;
        for (int i = scroll; i < designs.size() && i < scroll + rows; i++) {
            JewelryDesignRecipe d = designs.get(i);
            int y = listY + (i - scroll) * 12;
            boolean known = WorkstationClient.knows(d);
            if (i == at) g.fill(x0 + 4, y - 1, x0 + 4 + listW, y + 10, contrast ? 0xFF404000 : 0x60E8C46A);
            String star = favourites.contains(d.getId().toString()) ? "★ " : "  ";
            int color = known ? (contrast ? 0xFFFFFFFF : 0xFFF0E6D0) : 0xFF8A8070;
            g.drawString(font, Component.literal(star).append(d.name()), x0 + 6, y, color, false);
            ResourceLocation id = d.getId();
            buttons.add(new Button(x0 + 4, y - 1, listW, 11, Component.empty(), () -> {
                selected = id;
                preferred.clear();
            }, true));
        }
        if (designs.isEmpty()) {
            g.drawString(font, Component.translatable(search.getValue().isBlank() ? "workstation.immersive_jewelry.folio.none"
                    : "workstation.immersive_jewelry.folio.no_match"), x0 + 6, listY, 0xFF8A8070, false);
        }
        JewelryDesignRecipe design = current();
        if (design == null) return;
        int dx = x0 + listW + 12;
        int dw = w - listW - 18;
        if (page == Page.PREPARE) preparePage(g, font, dx, y0, dw, design, contrast);
        else designPage(g, font, dx, y0, dw, design);
    }

    private void designPage(GuiGraphics g, Font font, int dx, int y0, int dw, JewelryDesignRecipe design) {
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
        lines.add(Component.translatable("workstation.immersive_jewelry.folio.tray", onBench(design), design.stones()));
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
        buttons.add(new Button(dx + 100, y, 96, 14, Component.translatable("workstation.immersive_jewelry.folio.stage"), this::openPrepare, true));
        y += 18;
        if (work != null) {
            if (!work.irreversible()) {
                buttons.add(new Button(dx, y, 96, 14, Component.translatable("workstation.immersive_jewelry.folio.cancel"),
                        () -> bench.send(WorkCommand.CANCEL_PLAN, new CompoundTag()), true));
            } else {
                buttons.add(new Button(dx, y, 96, 14, Component.translatable("workstation.immersive_jewelry.folio.pack"),
                        bench::pack, true));
            }
        }
        boolean starred = ClientConfig.FOLIO_FAVORITES.get().contains(design.getId().toString());
        buttons.add(new Button(dx + 100, y, 96, 14, Component.translatable(starred ? "workstation.immersive_jewelry.folio.unstar"
                : "workstation.immersive_jewelry.folio.star"), () -> star(design), true));
    }

    /**
     * The preparation preview: what the design still needs, the gems and stones that could go into the tray (the player's
     * preferred ones numbered in their order), the finishing material and when it is used. Drawing it changes nothing.
     */
    private void preparePage(GuiGraphics g, Font font, int dx, int y0, int dw, JewelryDesignRecipe design, boolean contrast) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        int y = y0;
        g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.prepare_title", design.name()).withStyle(ChatFormatting.BOLD),
                dx, y, 0xFFE8C46A, false);
        y += 12;
        int have = onBench(design);
        int need = Math.max(0, design.stones() - have);
        g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.prepare_need", design.stones(), have, need), dx, y, 0xFFE0D8C8, false);
        y += 12;
        ResourceLocation fixed = benchFamily(design);
        List<Candidate> all = candidates(design, fromStaging);
        // The gems to choose from: the bench's (once one is there), else every gem among the candidates, or the best cut.
        Map<ResourceLocation, Integer> families = new LinkedHashMap<>();
        for (Candidate c : all) if (fixed == null || fixed.equals(c.gem().gem())) families.merge(c.gem().gem(), c.count(), Integer::sum);
        if (fixed != null) gemChoice = fixed;
        else if (gemChoice != null && !families.containsKey(gemChoice)) gemChoice = null;
        int fx = dx;
        if (fixed == null) {
            Component best = Component.translatable("workstation.immersive_jewelry.folio.prepare_best");
            int bw = font.width(best) + 10;
            buttons.add(new Button(fx, y, bw, 12, gemChoice == null ? Component.literal("▶ ").append(best) : best, () -> {
                gemChoice = null;
                preferred.clear();
            }, true));
            fx += bw + 4;
        }
        for (Map.Entry<ResourceLocation, Integer> family : families.entrySet()) {
            Component label = Component.translatable("workstation.immersive_jewelry.folio.prepare_gem", JewelryNames.gem(defs, family.getKey()), family.getValue());
            int bw = font.width(label) + 10;
            if (fx + bw > dx + dw) {
                fx = dx;
                y += 14;
            }
            ResourceLocation gem = family.getKey();
            boolean on = gem.equals(gemChoice);
            buttons.add(new Button(fx, y, bw, 12, on ? Component.literal("▶ ").append(label) : label, () -> {
                gemChoice = gem;
                preferred.removeIf(slot -> !candidateGem(design, slot, gem));
            }, fixed == null));
            fx += bw + 4;
        }
        y += 16;
        // The stones themselves: click to prefer one (numbered), again to let it be.
        List<Candidate> shown = new ArrayList<>();
        for (Candidate c : all) if (gemChoice == null || gemChoice.equals(c.gem().gem())) shown.add(c);
        if (shown.isEmpty()) {
            g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.prepare_none"), dx, y, 0xFF8A8070, false);
            y += 11;
        }
        candidateCursor = Mth.clamp(candidateCursor, 0, Math.max(0, Math.min(shown.size(), MAX_CANDIDATES) - 1));
        for (int i = 0; i < shown.size() && i < MAX_CANDIDATES; i++) {
            Candidate c = shown.get(i);
            int order = c.staged() ? -1 : preferred.indexOf(c.index());
            if (i == candidateCursor) g.fill(dx - 2, y - 1, dx + dw, y + 10, contrast ? 0xFF404000 : 0x40E8C46A);
            Component where = Component.translatable(c.staged() ? "workstation.immersive_jewelry.folio.prepare_in_drawer"
                    : "workstation.immersive_jewelry.folio.prepare_in_inventory");
            Component line = Component.translatable("workstation.immersive_jewelry.folio.prepare_stone", order >= 0 ? (order + 1) + "." : "·",
                    JewelryNames.gem(defs, c.gem().gem()), JewelryNames.cut(defs, c.gem().cut()), Math.round(c.gem().cutQuality() * 100F),
                    c.gem().damage().displayName(), String.format(java.util.Locale.ROOT, "%.2f", c.gem().size()), where);
            g.drawString(font, font.plainSubstrByWidth(line.getString(), dw), dx, y, order >= 0 ? 0xFFE8C46A : 0xFFE0D8C8, false);
            if (!c.staged()) {
                int index = i;
                buttons.add(new Button(dx - 2, y - 1, dw + 2, 11, Component.empty(), () -> {
                    candidateCursor = index;
                    togglePreferred(c);
                }, true));
            }
            y += 11;
        }
        y += 3;
        buttons.add(new Button(dx, y, dw, 12, Component.translatable(fromStaging ? "workstation.immersive_jewelry.folio.prepare_staged_on"
                : "workstation.immersive_jewelry.folio.prepare_staged_off"), () -> fromStaging = !fromStaging, true));
        y += 16;
        // The finishing material: shown apart, with when it is used. Nothing is paid by preparing.
        FinishDefinition finish = chosenFinish(design);
        Component material;
        if (finish == null) {
            material = Component.translatable("workstation.immersive_jewelry.folio.prepare_finish_none");
        } else if (finish.cost() == null || finish.costCount() <= 0 || Inventories.isEmpty(finish.cost())) {
            material = Component.translatable("workstation.immersive_jewelry.folio.prepare_finish_free", JewelryNames.finish(defs, finish.id()));
        } else {
            int inJars = 0;
            JewelersBenchBlockEntity be = bench.bench();
            if (be != null) for (int i = 0; i < JewelersBenchBlockEntity.SUPPLIES; i++) if (finish.cost().test(be.supply(i))) inJars += be.supply(i).getCount();
            material = Component.translatable("workstation.immersive_jewelry.folio.prepare_finish", JewelryNames.finish(defs, finish.id()),
                    finish.costCount(), Inventories.display(finish.cost(), finish.costCount()).getHoverName(), inJars);
        }
        for (FormattedCharSequence part : font.split(material, dw)) {
            g.drawString(font, part, dx, y, 0xFFB0A898, false);
            y += 10;
        }
        y += 4;
        buttons.add(new Button(dx, y, 110, 14, Component.translatable("workstation.immersive_jewelry.folio.prepare_go", need), () -> prepare(design),
                need > 0));
        buttons.add(new Button(dx + 114, y, 60, 14, Component.translatable("workstation.immersive_jewelry.folio.back"), () -> page = Page.DESIGNS, true));
        y += 17;
        g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.prepare_note"), dx, y, 0xFF8A8070, false);
    }

    private boolean candidateGem(JewelryDesignRecipe design, int slot, ResourceLocation gem) {
        var player = Minecraft.getInstance().player;
        if (player == null || slot < 0 || slot >= player.getInventory().items.size()) return false;
        GemData g = CutGemItem.data(player.getInventory().items.get(slot)).orElse(null);
        return g != null && design.accepts(g) && gem.equals(g.gem());
    }

    private void togglePreferred(Candidate c) {
        if (c.staged()) return;
        if (!preferred.remove((Integer) c.index()) && preferred.size() < com.otectus.immersivejewelry.workstation.BenchOperations.MAX_PREFERRED) {
            preferred.add(c.index());
            if (gemChoice == null) gemChoice = c.gem().gem();
        }
    }

    @Nullable
    private FinishDefinition chosenFinish(JewelryDesignRecipe design) {
        BenchWork work = bench.work();
        if (work != null && work.finish() != null && design.getId().equals(work.design())) return JewelryDefinitions.client().finish(work.finish());
        List<FinishDefinition> finishes = finishes(design);
        if (finishChoice >= 0 && finishChoice < finishes.size()) return finishes.get(finishChoice);
        return finishes.isEmpty() ? null : finishes.get(0);
    }

    private void reworkPage(GuiGraphics g, Font font, int x0, int y0, int w) {
        JewelersBenchBlockEntity be = bench.bench();
        JewelryData data = be == null ? null : JewelryData.get(be.clamp()).orElse(null);
        if (data == null) {
            g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.no_piece"), x0 + 6, y0, 0xFF8A8070, false);
            return;
        }
        int y = y0;
        g.drawString(font, be.clamp().getHoverName(), x0 + 6, y, 0xFFE8C46A, false);
        y += 14;
        rework(g, font, x0 + 6, y, "retighten");
        rework(g, font, x0 + 6, y += 18, "remove");
        rework(g, font, x0 + 6, y += 18, "repair");
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

    private void rework(GuiGraphics g, Font font, int x, int y, String kind) {
        CompoundTag args = new CompoundTag();
        args.putString("Kind", kind);
        Component label = Component.translatable("workstation.immersive_jewelry.folio.rework." + kind);
        buttons.add(new Button(x, y, 150, 14, label, () -> {
            bench.send(WorkCommand.BEGIN, args);
            bench.closeFolio();
        }, true));
        g.drawString(font, Component.translatable("workstation.immersive_jewelry.folio.rework." + kind + ".about"), x + 158, y + 3, 0xFFB0A898, false);
    }

    private void tab(Font font, int x, int y, Component label, boolean on, Runnable action) {
        int w = font.width(label) + 12;
        buttons.add(new Button(x, y, w, 13, on ? label.copy().withStyle(ChatFormatting.UNDERLINE) : label, action, true));
    }

    private void chip(Font font, int x, int y, int w, String key, boolean on, Runnable action) {
        Component label = Component.translatable(key);
        buttons.add(new Button(x, y, w, 12, on ? label.copy().withStyle(ChatFormatting.UNDERLINE) : label.copy().withStyle(ChatFormatting.GRAY),
                action, true));
    }

    private static void drawButton(GuiGraphics g, Font font, Button b, boolean hover, boolean contrast) {
        if (b.label().getString().isEmpty()) return;
        int bg = !b.enabled() ? 0x60303030 : hover ? (contrast ? 0xFF606000 : 0xC0705030) : (contrast ? 0xFF202020 : 0x90503820);
        g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), bg);
        g.renderOutline(b.x(), b.y(), b.w(), b.h(), contrast ? 0xFFFFFFFF : 0xFF8A6A40);
        int color = b.enabled() ? (contrast ? 0xFFFFFFFF : 0xFFF0E6D0) : 0xFF7A7A7A;
        String text = font.plainSubstrByWidth(b.label().getString(), b.w() - 4);
        Component shown = text.length() == b.label().getString().length() ? b.label() : Component.literal(text);
        g.drawString(font, shown, b.x() + (b.w() - font.width(shown)) / 2, b.y() + (b.h() - 8) / 2, color, false);
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

    private void openPrepare() {
        page = Page.PREPARE;
        candidateCursor = 0;
    }

    /**
     * One request for the design's stones: the design, the chosen gem (none: the best cut decides), the preferred inventory
     * slots in order and whether staged stones may be used. The server counts what is still needed and moves exactly that.
     */
    private void prepare(JewelryDesignRecipe design) {
        CompoundTag args = new CompoundTag();
        args.putString("Design", design.getId().toString());
        if (gemChoice != null) args.putString("Gem", gemChoice.toString());
        int[] prefer = preferred.stream().mapToInt(Integer::intValue).limit(com.otectus.immersivejewelry.workstation.BenchOperations.MAX_PREFERRED).toArray();
        args.put("Prefer", new IntArrayTag(prefer));
        args.putBoolean("FromStaging", fromStaging);
        bench.send(WorkCommand.PREPARE, args);
        preferred.clear();
    }

    private void star(JewelryDesignRecipe design) {
        List<String> favourites = new ArrayList<>(ClientConfig.FOLIO_FAVORITES.get());
        String id = design.getId().toString();
        if (!favourites.remove(id)) favourites.add(id);
        ClientConfig.FOLIO_FAVORITES.set(favourites);
        ClientConfig.SPEC.save();
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page != Page.REWORK && search.isMouseOver(mouseX, mouseY)) {
            focusSearch(true);
            search.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        focusSearch(false);
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
        if (page == Page.PREPARE) candidateCursor = Math.max(0, candidateCursor - (int) Math.signum(amount));
        else move(-(int) Math.signum(amount));
    }

    /** Whether the search field has the keyboard. */
    boolean typing() {
        return search.isFocused();
    }

    private void focusSearch(boolean focused) {
        if (focused && !search.isFocused()) bench.releaseAll();
        search.setFocused(focused);
        if (focused) page = page == Page.REWORK ? Page.DESIGNS : page;
    }

    /** A typed character: text for the search field while it has the keyboard (characters outside the BMP as their two halves). */
    boolean typed(int codePoint, int modifiers) {
        if (!search.isFocused()) return false;
        if (Character.charCount(codePoint) == 1) {
            search.charTyped((char) codePoint, modifiers);
        } else {
            for (char c : Character.toChars(codePoint)) search.charTyped(c, modifiers);
        }
        return true;
    }

    /**
     * Keys while the folio is open. With the search field focused every key is the field's: Escape and Enter leave it, the
     * rest edit (and repeat as text does). Otherwise the folio's keys; one-shot actions run on the first press of a key only.
     */
    boolean key(int key, int scan, int modifiers, boolean repeat) {
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (!repeat) focusSearch(false);
                return true;
            }
            search.keyPressed(key, scan, modifiers);
            return true;
        }
        if (page != Page.REWORK && (key == GLFW.GLFW_KEY_SLASH || key == GLFW.GLFW_KEY_F && Screen.hasControlDown())) {
            if (!repeat) focusSearch(true);
            return true;
        }
        if (page == Page.PREPARE) return prepareKey(key, scan, repeat);
        if (WorkstationKeys.RAISE.matches(key, scan)) {
            move(-1);
            return true;
        }
        if (WorkstationKeys.LOWER.matches(key, scan)) {
            move(1);
            return true;
        }
        JewelryDesignRecipe design = current();
        if (WorkstationKeys.LEFT.matches(key, scan) || WorkstationKeys.RIGHT.matches(key, scan)) {
            List<FinishDefinition> finishes = finishes(design);
            if (!finishes.isEmpty() && !repeat) {
                int next = Math.floorMod(Math.max(finishChoice, 0) + (WorkstationKeys.RIGHT.matches(key, scan) ? 1 : -1), finishes.size());
                chooseFinish(finishes, next);
            }
            return true;
        }
        Runnable once = null;
        if (WorkstationKeys.CONFIRM.matches(key, scan) && design != null) once = () -> use(design);
        else if (WorkstationKeys.APPLY.matches(key, scan) && design != null) once = () -> star(design);
        else if (WorkstationKeys.TOOL.matches(key, scan)) once = () -> page = page == Page.DESIGNS ? Page.REWORK : Page.DESIGNS;
        else if (WorkstationKeys.NEXT.matches(key, scan) && design != null) once = this::openPrepare;
        else if (WorkstationKeys.FOLIO.matches(key, scan)) once = bench::closeFolio;
        if (once == null) return false;
        if (!repeat) once.run();
        return true;
    }

    private boolean prepareKey(int key, int scan, boolean repeat) {
        JewelryDesignRecipe design = current();
        if (design == null) return false;
        List<Candidate> shown = new ArrayList<>();
        for (Candidate c : candidates(design, fromStaging)) if (gemChoice == null || gemChoice.equals(c.gem().gem())) shown.add(c);
        if (WorkstationKeys.RAISE.matches(key, scan)) {
            candidateCursor = Math.max(0, candidateCursor - 1);
            return true;
        }
        if (WorkstationKeys.LOWER.matches(key, scan)) {
            candidateCursor = Math.min(Math.max(0, Math.min(shown.size(), MAX_CANDIDATES) - 1), candidateCursor + 1);
            return true;
        }
        Runnable once = null;
        if (WorkstationKeys.APPLY.matches(key, scan) && candidateCursor < shown.size()) {
            Candidate c = shown.get(candidateCursor);
            once = () -> togglePreferred(c);
        } else if (WorkstationKeys.CONFIRM.matches(key, scan) || WorkstationKeys.NEXT.matches(key, scan)) {
            once = () -> prepare(design);
        } else if (WorkstationKeys.TOOL.matches(key, scan)) {
            once = () -> page = Page.DESIGNS;
        } else if (WorkstationKeys.FOLIO.matches(key, scan)) {
            once = bench::closeFolio;
        }
        if (once == null) return false;
        if (!repeat) once.run();
        return true;
    }

    @Override
    public void onClosed() {
        search.setFocused(false);
        onClosed.run();
    }
}
