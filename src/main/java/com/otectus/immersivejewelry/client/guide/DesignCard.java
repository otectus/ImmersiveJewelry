package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * One design as the server's recipes define it: the form and its blank, how many stones (each its own appraised stone; a
 * multi-stone design needs the same gem, never the same stone data), prongs or bezel and how many points per stone, the
 * cuts, gems and finishes it allows, and whether this player may make it now (known, learned from a sketch, or locked by
 * the server's sketch policy). Reading the card never teaches a design: only using a sketch does. Page JSON:
 * {@code "design": "immersive_jewelry:designs/halo_amulet"}; {@code "design": "others"} lists designs added by packs.
 */
public class DesignCard extends GuideCard {
    String design;

    @Override
    protected void resolve(UnaryOperator<IVariable> lookup) {
        design = variable(lookup, design);
    }

    static List<JewelryDesignRecipe> designs() {
        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get()).stream()
                .sorted(java.util.Comparator.comparingInt(JewelryDesignRecipe::sortOrder).thenComparing(r -> r.getId().toString())).toList();
    }

    @Nullable
    static JewelryDesignRecipe find(@Nullable ResourceLocation id) {
        if (id == null) return null;
        for (JewelryDesignRecipe r : designs()) if (r.getId().equals(id)) return r;
        return null;
    }

    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        if ("others".equals(design)) {
            others(g, context);
            return;
        }
        JewelryDesignRecipe recipe = find(id(design));
        if (recipe == null) {
            line(g, context, Component.translatable("book.immersive_jewelry.card.design_missing", String.valueOf(design)), y);
            return;
        }
        JewelryDefinitions defs = JewelryDefinitions.client();
        ItemStack blank = recipe.blankItem(defs);
        if (!blank.isEmpty()) context.renderItemStack(g, x, y, mouseX, mouseY, blank);
        int top = text(g, context, recipe.name().copy().withStyle(ChatFormatting.BOLD), x + 20, y + 4, PAGE_WIDTH - 20, context.getHeaderColor()) + 6;
        FormDefinition form = defs.form(recipe.form());
        top = fact(g, context, "form", form != null ? form.name() : Component.literal(recipe.form().getPath()), top);
        top = fact(g, context, "stones", Component.literal(String.valueOf(recipe.stones())), top);
        top = fact(g, context, "setting", Component.translatable("book.immersive_jewelry.card.setting." + recipe.style().name().toLowerCase(java.util.Locale.ROOT),
                recipe.pointsPerStone()), top);
        top = fact(g, context, "cuts", recipe.allowedCuts().isEmpty() ? Component.translatable("book.immersive_jewelry.card.any")
                : names(recipe.allowedCuts(), defs, 0), top);
        if (!recipe.allowedGems().isEmpty()) top = fact(g, context, "gems", names(recipe.allowedGems(), defs, 1), top);
        top = fact(g, context, "finishes", recipe.allowedFinishes().isEmpty() ? Component.translatable("book.immersive_jewelry.card.any")
                : names(recipe.allowedFinishes(), defs, 2), top);
        top = fact(g, context, "status", status(recipe), top);
        if (recipe.stones() > 1) top = text(g, context, Component.translatable("book.immersive_jewelry.card.same_gem"), x, top + 1, PAGE_WIDTH, MUTED);
        provenance(g, context, false, top + 2);
    }

    static Component status(JewelryDesignRecipe recipe) {
        if (!recipe.sketch()) return Component.translatable("book.immersive_jewelry.card.status.basic").withStyle(ChatFormatting.DARK_GREEN);
        if (!ServerConfig.get(ServerConfig.REQUIRE_DESIGN_SKETCHES)) {
            return Component.translatable("book.immersive_jewelry.card.status.open").withStyle(ChatFormatting.DARK_GREEN);
        }
        return com.otectus.immersivejewelry.client.workstation.WorkstationClient.knows(recipe)
                ? Component.translatable("book.immersive_jewelry.card.status.known").withStyle(ChatFormatting.DARK_GREEN)
                : Component.translatable("book.immersive_jewelry.card.status.locked").withStyle(ChatFormatting.DARK_RED);
    }

    private static Component names(List<ResourceLocation> ids, JewelryDefinitions defs, int kind) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) out.append(Component.literal(", "));
            ResourceLocation id = ids.get(i);
            Component name = switch (kind) {
                case 0 -> {
                    CutDefinition c = defs.cut(id);
                    yield c != null ? c.name() : Component.literal(id.getPath());
                }
                case 1 -> {
                    GemDefinition gem = defs.gem(id);
                    yield gem != null ? gem.name() : Component.literal(id.getPath());
                }
                default -> {
                    FinishDefinition f = defs.finish(id);
                    yield f != null ? f.name() : Component.literal(id.getPath());
                }
            };
            out.append(name);
        }
        return out;
    }

    /** Designs from packs or other mods (not one of the ten built in): listed with their state. */
    private void others(GuiGraphics g, IComponentRenderContext context) {
        int top = header(g, context, Component.translatable("book.immersive_jewelry.card.other_designs"), y);
        int n = 0;
        for (JewelryDesignRecipe recipe : designs()) {
            if (recipe.getId().getNamespace().equals("immersive_jewelry")) continue;
            if (n++ >= 10) break;
            top = text(g, context, recipe.name().copy().append(Component.literal(" (" + recipe.stones() + ") ")).append(status(recipe)),
                    x, top, PAGE_WIDTH, context.getTextColor());
        }
        if (n == 0) top = line(g, context, Component.translatable("book.immersive_jewelry.card.other_designs_none"), top);
        provenance(g, context, false, top + 2);
    }
}
