package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IVariable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The base of the Jeweler's Handbook's live cards (Patchouli custom components, built from page JSON with Gson). Fields
 * read from JSON are plain; everything computed at runtime is {@code transient}. Cards draw inside the page (116 x 156) from
 * what the client really has: the definitions the server synced (auto-detected gems included), the synced recipes, the
 * server's synced config and this player's known designs. Reading a card never appraises, crafts, equips, ticks an effect
 * or sends anything: it is the same read-only view the tooltips use.
 *
 * <p>A card that cannot draw (a resource pack's broken page, a missing definition) logs one error and shows a short
 * explanation in its place, so the rest of the entry stays readable.</p>
 */
public abstract class GuideCard implements ICustomComponent {
    public static final int PAGE_WIDTH = 116;
    public static final int PAGE_HEIGHT = 156;
    public static final int MUTED = 0xFF6F6253;
    private static final Set<String> REPORTED = new HashSet<>();

    protected transient int x;
    protected transient int y;
    protected transient int page;
    private transient boolean failed;

    @Override
    public void build(int componentX, int componentY, int pageNum) {
        x = componentX;
        y = componentY;
        page = pageNum;
    }

    @Override
    public void onVariablesAvailable(UnaryOperator<IVariable> lookup) {
        resolve(lookup);
    }

    /** Resolves {@code #variables} of the template in the card's string fields. */
    protected void resolve(UnaryOperator<IVariable> lookup) {}

    protected static String variable(UnaryOperator<IVariable> lookup, @Nullable String value) {
        if (value == null || value.isEmpty()) return value;
        try {
            return lookup.apply(IVariable.wrap(value)).asString(value);
        } catch (RuntimeException e) {
            return value;
        }
    }

    @Nullable
    protected static ResourceLocation id(@Nullable String value) {
        return value == null || value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }

    @Override
    public final void render(GuiGraphics graphics, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        if (failed) {
            text(graphics, context, Component.translatable("book.immersive_jewelry.card.unavailable"), x, y, PAGE_WIDTH, context.getTextColor());
            return;
        }
        try {
            draw(graphics, context, partialTick, mouseX, mouseY);
        } catch (RuntimeException e) {
            failed = true;
            if (REPORTED.add(getClass().getSimpleName())) {
                ImmersiveJewelry.LOGGER.error("Jeweler's Handbook card {} could not be drawn; an explanation is shown instead", getClass().getSimpleName(), e);
            }
        }
    }

    protected abstract void draw(GuiGraphics graphics, IComponentRenderContext context, float partialTick, int mouseX, int mouseY);

    protected static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Wrapped text in the book's font; returns the y below it. */
    protected static int text(GuiGraphics graphics, IComponentRenderContext context, Component text, int left, int top, int width, int color) {
        List<FormattedCharSequence> lines = font().split(text.copy().withStyle(context.getFont()), width);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font(), line, left, top, color, false);
            top += 9;
        }
        return top;
    }

    protected int line(GuiGraphics graphics, IComponentRenderContext context, Component text, int top) {
        return text(graphics, context, text, x, top, PAGE_WIDTH, context.getTextColor());
    }

    /** "Label: value" on one wrapped line, the label muted. */
    protected int fact(GuiGraphics graphics, IComponentRenderContext context, String label, Component value, int top) {
        Component row = Component.translatable("book.immersive_jewelry.card." + label).withStyle(s -> s.withColor(MUTED & 0xFFFFFF))
                .append(Component.literal(" ")).append(value.copy().withStyle(s -> s.withColor(context.getTextColor() & 0xFFFFFF)));
        return text(graphics, context, row, x, top, PAGE_WIDTH, context.getTextColor());
    }

    /** A bold header in the book's header colour; returns the y below it. */
    protected int header(GuiGraphics graphics, IComponentRenderContext context, Component text, int top) {
        return text(graphics, context, text.copy().withStyle(ChatFormatting.BOLD), x, top, PAGE_WIDTH, context.getHeaderColor()) + 1;
    }

    /** "Live: this server's data" or "Example": where the numbers on the card come from. */
    protected int provenance(GuiGraphics graphics, IComponentRenderContext context, boolean estimated, int top) {
        Component label = Component.translatable(estimated ? "book.immersive_jewelry.card.estimated" : "book.immersive_jewelry.card.live")
                .withStyle(ChatFormatting.ITALIC);
        return text(graphics, context, label, x, top, PAGE_WIDTH, MUTED);
    }

    /** A small colour swatch with a dark border (never the only cue: a name always goes with it). */
    protected static void swatch(GuiGraphics graphics, int left, int top, int rgb) {
        graphics.fill(left, top, left + 9, top + 9, 0xFF3A3026);
        graphics.fill(left + 1, top + 1, left + 8, top + 8, 0xFF000000 | rgb);
    }

    /** A word for a 0..1 value in five steps (very low .. very high), so no number is needed to compare. */
    protected static Component scale(float value) {
        int step = Math.max(0, Math.min(4, (int) (value * 5F)));
        return Component.translatable("book.immersive_jewelry.card.scale." + step);
    }

    protected static Component percent(float value) {
        return Component.literal(Math.round(value * 100F) + "%");
    }

    protected static Component times(float value) {
        return Component.literal(String.format(java.util.Locale.ROOT, "×%.2f", value));
    }
}
