package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.jewelry.JewelryResolver;
import com.otectus.immersivejewelry.jewelry.JewelryTooltips;
import com.otectus.immersivejewelry.jewelry.ResolvedJewelry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import vazkii.patchouli.api.IComponentRenderContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * A bounded "what would it do" comparison: one gem on one form, in plain gold with a round (or the gem's first) cut, Fine
 * workmanship and a polished finish, resolved by the game's own resolver and described by its tooltip text. Two buttons
 * step through the gems and forms this server defines. Marked Estimated: a real piece's metal, cut quality, finish,
 * integrity and outside contributions change the numbers, and its own tooltip (or the inspection card) is the authority.
 */
public class EffectCard extends GuideCard {
    private transient int gemIndex;
    private transient int formIndex;

    private static List<GemDefinition> gems(JewelryDefinitions defs) {
        return defs.gems().stream().filter(g -> !g.auto()).sorted(Comparator.comparing(g -> g.id().toString())).toList();
    }

    private static List<FormDefinition> forms(JewelryDefinitions defs) {
        return defs.forms().stream().sorted(Comparator.comparingInt(FormDefinition::sortOrder)).toList();
    }

    @Override
    public void onDisplayed(IComponentRenderContext context) {
        context.addWidget(Button.builder(Component.translatable("book.immersive_jewelry.card.next_gem"), b -> gemIndex++)
                .bounds(x, y + PAGE_HEIGHT - 22, 56, 16).build(), page);
        context.addWidget(Button.builder(Component.translatable("book.immersive_jewelry.card.next_form"), b -> formIndex++)
                .bounds(x + 60, y + PAGE_HEIGHT - 22, 56, 16).build(), page);
    }

    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        List<GemDefinition> gems = gems(defs);
        List<FormDefinition> forms = forms(defs);
        if (gems.isEmpty() || forms.isEmpty()) {
            line(g, context, Component.translatable("book.immersive_jewelry.card.unavailable"), y);
            return;
        }
        GemDefinition gem = gems.get(Math.floorMod(gemIndex, gems.size()));
        FormDefinition form = forms.get(Math.floorMod(formIndex, forms.size()));
        List<CutDefinition> cuts = defs.cutsFor(gem);
        ResourceLocation cut = cuts.stream().map(CutDefinition::id).filter(id -> id.getPath().equals("round")).findFirst()
                .orElse(cuts.isEmpty() ? null : cuts.get(0).id());
        GemData stone = new GemData(gem.id(), 1F, 1.25F, 0.6F, 0.2F, cut, 0.75F, GemDamage.NONE, 1F);
        JewelryData piece = new JewelryData(form.id(), MetalRef.of(new ResourceLocation("minecraft", "gold")), List.of(stone), null,
                new ResourceLocation("immersive_jewelry", "polished"), 0.7F, 0.75F, 0.72F, 1F, 0, Set.of(), null, false, null);
        int top = header(g, context, Component.translatable("book.immersive_jewelry.card.effect_of", gem.name(), form.name()), y);
        List<Component> lines = new ArrayList<>();
        ResolvedJewelry resolved = JewelryResolver.resolve(piece, defs);
        JewelryTooltips.effects(resolved, lines);
        int bottom = y + PAGE_HEIGHT - 34;
        for (Component line : lines) {
            if (line.getString().isEmpty()) continue;
            if (top > bottom - 9) break;
            top = text(g, context, line, x, top, PAGE_WIDTH, context.getTextColor());
        }
        provenance(g, context, true, Math.min(top + 2, bottom));
    }
}
