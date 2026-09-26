package com.otectus.immersivejewelry.quality;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Bands of metalwork quality, matching Immersive Smithing's Crude / Standard / Fine / Masterwork score bands. */
public enum MetalworkGrade {
    CRUDE("crude", ChatFormatting.GRAY, 0F),
    STANDARD("standard", ChatFormatting.WHITE, 0.35F),
    FINE("fine", ChatFormatting.AQUA, 0.70F),
    MASTERWORK("masterwork", ChatFormatting.GOLD, 0.90F);

    private final String name;
    private final ChatFormatting color;
    private final float min;

    MetalworkGrade(String name, ChatFormatting color, float min) {
        this.name = name;
        this.color = color;
        this.min = min;
    }

    public String id() { return name; }

    public MutableComponent displayName() {
        return Component.translatable("metalwork.immersive_jewelry." + name).withStyle(color);
    }

    public static MetalworkGrade of(float quality) {
        MetalworkGrade grade = CRUDE;
        for (MetalworkGrade g : values()) {
            if (quality >= g.min) grade = g;
        }
        return grade;
    }
}
