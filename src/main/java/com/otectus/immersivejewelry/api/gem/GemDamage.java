package com.otectus.immersivejewelry.api.gem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Physical damage to a stone. Only recutting at the Lapidary Wheel clears it (at a cost in size). */
public enum GemDamage {
    NONE("none", 1.0F, ChatFormatting.GRAY),
    SCRATCHED("scratched", 0.92F, ChatFormatting.YELLOW),
    CHIPPED("chipped", 0.75F, ChatFormatting.GOLD),
    FRACTURED("fractured", 0.45F, ChatFormatting.RED);

    private final String name;
    private final float potency;
    private final ChatFormatting color;

    GemDamage(String name, float potency, ChatFormatting color) {
        this.name = name;
        this.potency = potency;
        this.color = color;
    }

    public String id() { return name; }

    /** Multiplier on the stone's contribution to jewelry power. */
    public float potency() { return potency; }

    public GemDamage worse() {
        return this == FRACTURED ? FRACTURED : values()[ordinal() + 1];
    }

    public MutableComponent displayName() {
        return Component.translatable("gem_damage.immersive_jewelry." + name).withStyle(color);
    }

    public static GemDamage byId(String id) {
        for (GemDamage d : values()) {
            if (d.name.equals(id)) return d;
        }
        return NONE;
    }
}
