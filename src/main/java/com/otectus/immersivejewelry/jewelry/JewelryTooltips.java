package com.otectus.immersivejewelry.jewelry;

import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.quality.CutGrade;
import com.otectus.immersivejewelry.quality.MetalworkGrade;
import com.otectus.immersivejewelry.quality.StoneTraits;
import com.otectus.immersivejewelry.quality.WorkmanshipGrade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.List;

/**
 * Tooltips (spec 39): informative words by default, the percentages behind them while Shift is held (if both the
 * server and the client allow it). Runs on the client with its synced definitions.
 */
public final class JewelryTooltips {
    private static final String K = "tooltip.immersive_jewelry.";

    public static void jewelry(JewelryData data, JewelryDefinitions defs, List<Component> lines) {
        ResolvedJewelry resolved = JewelryResolver.resolve(data, defs);
        MetalworkGrade metalwork = MetalworkGrade.of(data.smithingQuality());
        lines.add(dot(JewelryNames.metal(defs, data.metal()).copy().withStyle(ChatFormatting.GRAY),
                Component.translatable(K + "metalwork", metalwork.displayName())));

        GemData stone = data.principal();
        if (stone != null) {
            MutableComponent gemName = JewelryNames.gem(defs, stone.gem()).copy().withStyle(style(JewelryNames.gemColor(defs, stone.gem())));
            if (data.gems().size() > 1) gemName = Component.translatable(K + "stones", data.gems().size(), gemName);
            lines.add(dot(gemName, cutLine(defs, stone.cut(), data.cutQuality())));
            GemDamage worst = GemDamage.NONE;
            for (GemData g : data.gems()) if (g.damage().ordinal() > worst.ordinal()) worst = g.damage();
            if (worst != GemDamage.NONE) lines.add(Component.translatable(K + "stone_damage", worst.displayName()).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable(K + "no_stone").withStyle(ChatFormatting.DARK_GRAY));
        }

        WorkmanshipGrade grade = WorkmanshipGrade.of(data.workmanship());
        MutableComponent work = Component.translatable(K + "workmanship", grade.displayName());
        lines.add(data.finish() != null ? dot(work, JewelryNames.finish(defs, data.finish()).copy().withStyle(ChatFormatting.GRAY)) : work);

        for (SettingDefect d : data.defects()) {
            lines.add(Component.translatable(K + "defect", d.displayName()).withStyle(ChatFormatting.GRAY));
        }
        if (data.isBroken()) {
            lines.add(Component.translatable(K + "broken").withStyle(ChatFormatting.RED));
        } else if (data.integrity() < 0.999F) {
            lines.add(Component.translatable(K + "integrity", integrityWord(data.integrity())).withStyle(ChatFormatting.GRAY));
        }
        if (data.legacyConverted()) lines.add(Component.translatable(K + "legacy").withStyle(ChatFormatting.DARK_GRAY));

        effects(resolved, lines);

        if (advanced()) {
            lines.add(Component.translatable(K + "numbers", pct(data.smithingQuality()), pct(data.cutQuality()), pct(data.settingQuality()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable(K + "numbers2", pct(data.workmanship()), pct(data.integrity()),
                    DescriptionBuilder.trim(resolved.basePower())).withStyle(ChatFormatting.DARK_GRAY));
            if (data.maker() != null) lines.add(Component.translatable(K + "maker", data.maker()).withStyle(ChatFormatting.DARK_GRAY));
        } else if (canShowAdvanced()) {
            lines.add(Component.translatable(K + "hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** The behaviours a piece expresses, with its own numbers. */
    public static void effects(ResolvedJewelry resolved, List<Component> lines) {
        if (resolved.inert() != null) {
            lines.add(Component.translatable(K + "inert." + resolved.inert()).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (resolved.expressions().isEmpty()) return;
        lines.add(Component.empty());
        if (resolved.trait() != null) {
            MutableComponent header = resolved.trait().name().copy().withStyle(style(resolved.trait().color()));
            if (!resolved.trait().theme().getString().isEmpty()) {
                header.append(Component.literal(" – ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(resolved.trait().theme().copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            lines.add(header);
        }
        for (ResolvedJewelry.Expression e : resolved.expressions()) {
            if (e.definition().name() != null && !e.definition().name().getString().isEmpty()) {
                MutableComponent name = Component.literal(e.fromMetal() ? "◇ " : "◆ ").append(e.definition().name());
                lines.add(name.withStyle(e.fromMetal() ? ChatFormatting.GRAY : ChatFormatting.WHITE));
            }
            e.definition().effect().describe(new DescriptionBuilder(e.power(), resolved.stability(), resolved.responsiveness(), lines));
        }
    }

    public static void blank(BlankData data, JewelryDefinitions defs, List<Component> lines) {
        lines.add(dot(JewelryNames.metal(defs, data.metal()).copy().withStyle(ChatFormatting.GRAY),
                Component.translatable(K + "metalwork", MetalworkGrade.of(data.smithingQuality()).displayName())));
        if (data.fallback()) lines.add(Component.translatable(K + "fallback_blank").withStyle(ChatFormatting.DARK_GRAY));
        if (data.integrity() < 0.999F) lines.add(Component.translatable(K + "integrity", integrityWord(data.integrity())).withStyle(ChatFormatting.GRAY));
        if (data.reworks() > 0) lines.add(Component.translatable(K + "reworks", data.reworks()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(K + "blank_hint").withStyle(ChatFormatting.DARK_GRAY));
        if (advanced()) {
            lines.add(Component.translatable(K + "blank_numbers", pct(data.smithingQuality()), pct(data.integrity())).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static void gem(GemData data, JewelryDefinitions defs, List<Component> lines) {
        GemDefinition def = defs.gem(data.gem());
        if (data.isCut()) {
            lines.add(cutLine(defs, data.cut(), data.cutQuality()));
        }
        lines.add(dot(Component.translatable(K + "size", StoneTraits.size(data.size())).withStyle(ChatFormatting.GRAY),
                StoneTraits.clarity(data.inclusions())));
        lines.add(Component.translatable(K + "luster", StoneTraits.luster(data.naturalQuality())).withStyle(ChatFormatting.GRAY));
        if (data.damage() != GemDamage.NONE) lines.add(Component.translatable(K + "stone_damage", data.damage().displayName()).withStyle(ChatFormatting.GRAY));
        if (def != null && defs.traitFor(def) != null) {
            var trait = defs.traitFor(def);
            lines.add(Component.translatable(K + "trait", trait.name().copy().withStyle(style(trait.color()))).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(data.isCut() ? K + "cut_hint" : K + "rough_hint").withStyle(ChatFormatting.DARK_GRAY));
        if (advanced()) {
            lines.add(Component.translatable(K + "gem_numbers", DescriptionBuilder.trim(data.size()), pct(data.naturalQuality()),
                    pct(data.inclusions()), pct(data.cutQuality())).withStyle(ChatFormatting.DARK_GRAY));
            if (def != null) {
                lines.add(Component.translatable(K + "gem_hardness", DescriptionBuilder.trim(def.hardness()), pct(def.brittleness()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    public static Component cutLine(JewelryDefinitions defs, net.minecraft.resources.ResourceLocation cut, float quality) {
        CutGrade grade = CutGrade.of(quality);
        return Component.translatable(K + "cut", grade.displayName(), JewelryNames.cut(defs, cut)).withStyle(grade.color());
    }

    public static Component integrityWord(float integrity) {
        String key = integrity >= 0.75F ? "sound" : integrity >= 0.45F ? "worn" : integrity >= 0.2F ? "fragile" : "failing";
        ChatFormatting color = integrity >= 0.75F ? ChatFormatting.WHITE : integrity >= 0.45F ? ChatFormatting.YELLOW
                : integrity >= 0.2F ? ChatFormatting.GOLD : ChatFormatting.RED;
        return Component.translatable("integrity.immersive_jewelry." + key).withStyle(color);
    }

    private static boolean canShowAdvanced() {
        return ServerConfig.get(ServerConfig.SHOW_NUMERIC_QUALITY) && ClientHooks.advancedTooltipsEnabled.getAsBoolean();
    }

    private static boolean advanced() {
        return canShowAdvanced() && ClientHooks.shiftDown.getAsBoolean();
    }

    private static MutableComponent dot(Component a, Component b) {
        return Component.empty().append(a).append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(b);
    }

    private static String pct(float value) {
        return Math.round(value * 100F) + "%";
    }

    public static net.minecraft.network.chat.Style style(int rgb) {
        return net.minecraft.network.chat.Style.EMPTY.withColor(TextColor.fromRgb(readable(rgb)));
    }

    /** Lifts very dark colours so they stay legible on the dark tooltip background. */
    public static int readable(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int luma = (r * 3 + g * 6 + b) / 10;
        if (luma >= 96) return rgb;
        float lift = (96F - luma) / 255F;
        r = Math.min(255, Math.round(r + (255 - r) * lift));
        g = Math.min(255, Math.round(g + (255 - g) * lift));
        b = Math.min(255, Math.round(b + (255 - b) * lift));
        return (r << 16) | (g << 8) | b;
    }

    private JewelryTooltips() {}
}
