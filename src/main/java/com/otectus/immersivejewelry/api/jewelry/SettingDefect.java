package com.otectus.immersivejewelry.api.jewelry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Flaws a stone setting can leave behind. Each costs setting quality; LOOSE can be fixed by retightening. */
public enum SettingDefect {
    LOOSE("loose"),
    CROOKED("crooked"),
    BENT_PRONG("bent_prong"),
    SCRATCHED_METAL("scratched_metal"),
    CHIPPED_STONE("chipped_stone");

    private final String name;

    SettingDefect(String name) {
        this.name = name;
    }

    public String id() { return name; }

    public MutableComponent displayName() {
        return Component.translatable("setting_defect.immersive_jewelry." + name).withStyle(ChatFormatting.GOLD);
    }

    public static SettingDefect byId(String id) {
        for (SettingDefect d : values()) {
            if (d.name.equals(id)) return d;
        }
        return null;
    }
}
