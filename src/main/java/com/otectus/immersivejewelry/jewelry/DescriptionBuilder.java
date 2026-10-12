package com.otectus.immersivejewelry.jewelry;

import com.otectus.immersivejewelry.api.effect.EffectDescription;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** {@link EffectDescription} writing indented tooltip lines. */
public final class DescriptionBuilder implements EffectDescription {
    private final float power;
    private final float stability;
    private final float responsiveness;
    private final List<Component> out;

    public DescriptionBuilder(float power, float stability, float responsiveness, List<Component> out) {
        this.power = power;
        this.stability = stability;
        this.responsiveness = responsiveness;
        this.out = out;
    }

    @Override public float power() { return power; }
    @Override public float stability() { return stability; }
    @Override public float responsiveness() { return responsiveness; }

    @Override
    public void line(String key, Object... args) {
        line(Component.translatable(key, args));
    }

    @Override
    public void line(Component component) {
        out.add(Component.literal("  ").append(component.copy().withStyle(ChatFormatting.GRAY)));
    }

    @Override
    public Component percent(float fraction) {
        return value(Math.round(fraction * 100F) + "%");
    }

    @Override
    public Component seconds(float ticks) {
        return Component.translatable("tooltip.immersive_jewelry.seconds", trim(ticks / 20F)).withStyle(ChatFormatting.WHITE);
    }

    @Override
    public Component number(float value) {
        return value(trim(value));
    }

    @Override
    public Component hearts(float health) {
        return value(trim(health / 2F) + "❤");
    }

    private static MutableComponent value(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    public static String trim(float value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
