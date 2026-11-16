package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The folio's catalogue: every design once, in the folio's order, with the text a search looks through (its name, form, setting
 * style, accepted gems and cuts and finishes, in the player's language and as resource ids). Built when first asked for and kept
 * until something it was built from changes: the recipes or tags (a datapack reload), the definitions, the language or the
 * player's known sketches ({@link #invalidate}, and a stamp of what can be compared). Nothing here is sent anywhere; a search is
 * only a way to find a design.
 */
final class FolioCatalogue {
    /** A design and its search text (lower case). */
    record Entry(JewelryDesignRecipe design, String text) {
        ResourceLocation id() {
            return design.getId();
        }

        /** Every word of the query appears somewhere in the design's text. */
        boolean matches(String[] words) {
            for (String word : words) if (!word.isEmpty() && !text.contains(word)) return false;
            return true;
        }
    }

    private static int invalidations;
    private static int builds;
    private static List<Entry> entries = List.of();
    private static Object stamp;

    /** Something the catalogue was built from changed (recipes, tags, definitions, known sketches). */
    static void invalidate() {
        invalidations++;
    }

    /** The current catalogue version: a cache built on the catalogue keeps it to know when to start over. */
    static int version() {
        all();
        return builds;
    }

    static List<Entry> all() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        JewelryDefinitions defs = JewelryDefinitions.client();
        Object now = List.of(invalidations, System.identityHashCode(mc.level.getRecipeManager()), System.identityHashCode(defs),
                mc.getLanguageManager().getSelected());
        if (Objects.equals(now, stamp)) return entries;
        List<JewelryDesignRecipe> designs = new ArrayList<>(mc.level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get()));
        designs.sort(Comparator.comparingInt(JewelryDesignRecipe::sortOrder).thenComparing(r -> r.getId().toString()));
        List<Entry> built = new ArrayList<>(designs.size());
        for (JewelryDesignRecipe d : designs) built.add(new Entry(d, text(defs, d)));
        entries = List.copyOf(built);
        stamp = now;
        builds++;
        return entries;
    }

    private static String text(JewelryDefinitions defs, JewelryDesignRecipe d) {
        StringBuilder out = new StringBuilder();
        add(out, d.name().getString(), d.getId());
        add(out, JewelryNames.formName(defs, d.form()).getString(), d.form());
        out.append(d.style().id()).append('\n');
        out.append(net.minecraft.network.chat.Component.translatable("workstation.immersive_jewelry.folio.style." + d.style().id()).getString()).append('\n');
        if (d.allowedGems().isEmpty()) defs.gems().forEach(g -> add(out, JewelryNames.gem(defs, g.id()).getString(), g.id()));
        else d.allowedGems().forEach(g -> add(out, JewelryNames.gem(defs, g).getString(), g));
        if (d.allowedCuts().isEmpty()) defs.cuts().forEach(c -> add(out, JewelryNames.cut(defs, c.id()).getString(), c.id()));
        else d.allowedCuts().forEach(c -> add(out, JewelryNames.cut(defs, c).getString(), c));
        for (FinishDefinition f : defs.finishes()) if (d.allowsFinish(f.id())) add(out, JewelryNames.finish(defs, f.id()).getString(), f.id());
        return out.toString().toLowerCase(Locale.ROOT);
    }

    private static void add(StringBuilder out, String name, ResourceLocation id) {
        out.append(name).append('\n').append(id).append('\n').append(id.getPath().replace('_', ' ')).append('\n');
    }

    /** A query split into lower-case words. */
    static String[] words(String query) {
        return query.trim().toLowerCase(Locale.ROOT).split("\\s+");
    }

    private FolioCatalogue() {}
}
