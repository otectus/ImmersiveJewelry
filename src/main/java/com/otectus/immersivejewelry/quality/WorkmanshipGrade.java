package com.otectus.immersivejewelry.quality;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Player-facing bands of overall workmanship (spec 14.5). */
public enum WorkmanshipGrade {
    FAULTY("faulty", ChatFormatting.DARK_RED, 0F),
    COMMON("common", ChatFormatting.WHITE, 0.35F),
    FINE("fine", ChatFormatting.AQUA, 0.60F),
    EXCEPTIONAL("exceptional", ChatFormatting.LIGHT_PURPLE, 0.80F),
    MASTERWORK("masterwork", ChatFormatting.GOLD, 0.92F);

    private final String name;
    private final ChatFormatting color;
    private final float min;

    WorkmanshipGrade(String name, ChatFormatting color, float min) {
        this.name = name;
        this.color = color;
        this.min = min;
    }

    public String id() { return name; }
    public ChatFormatting color() { return color; }
    public float min() { return min; }

    public MutableComponent displayName() {
        return Component.translatable("workmanship.immersive_jewelry." + name).withStyle(color);
    }

    public static WorkmanshipGrade of(float workmanship) {
        WorkmanshipGrade grade = FAULTY;
        for (WorkmanshipGrade g : values()) {
            if (workmanship >= g.min) grade = g;
        }
        return grade;
    }
}
