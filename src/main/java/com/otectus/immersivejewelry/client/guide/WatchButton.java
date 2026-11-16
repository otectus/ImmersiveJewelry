package com.otectus.immersivejewelry.client.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.function.UnaryOperator;

/**
 * "Watch the demonstration": a real button (keyboard focus, Enter or Space, narration) that opens the lesson in Ponder and
 * returns to this page when it closes. Opening a lesson changes nothing in the world and sends nothing to the server. Page
 * JSON: {@code "lesson": "first_piece", "entry": "immersive_jewelry:start/first_piece"}.
 */
public class WatchButton extends GuideCard {
    String lesson;
    @Nullable
    String entry;

    @Override
    protected void resolve(UnaryOperator<IVariable> lookup) {
        lesson = variable(lookup, lesson);
        entry = variable(lookup, entry);
    }

    @Override
    public void onDisplayed(IComponentRenderContext context) {
        Lessons target = Lessons.byId(lesson);
        Component label = Component.translatable("book.immersive_jewelry.watch",
                Component.translatable("lesson.immersive_jewelry." + (target != null ? target.id() : "missing")));
        boolean available = target != null && HandbookNavigator.tutorialsAvailable();
        Button button = Button.builder(label, b -> {
                    if (target != null) HandbookNavigator.watch(target, entry != null ? ResourceLocation.tryParse(entry) : null, page);
                })
                .bounds(x, y, PAGE_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable(available ? "book.immersive_jewelry.watch.tooltip"
                        : "message.immersive_jewelry.tutorials_unavailable")))
                .build();
        button.active = available;
        context.addWidget(button, page);
    }

    @Override
    protected void draw(GuiGraphics graphics, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        if (Lessons.byId(lesson) == null) {
            text(graphics, context, Component.translatable("book.immersive_jewelry.card.lesson_missing", String.valueOf(lesson))
                    .withStyle(ChatFormatting.DARK_RED), x, y + 22, PAGE_WIDTH, context.getTextColor());
        }
    }
}
