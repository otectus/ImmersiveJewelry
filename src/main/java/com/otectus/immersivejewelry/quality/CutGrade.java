package com.otectus.immersivejewelry.quality;

import com.otectus.immersivejewelry.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Player-facing bands of cut quality (spec 10.4). Thresholds come from the server config. */
public enum CutGrade {
    RUINED("ruined", ChatFormatting.DARK_RED),
    CHIPPED("chipped", ChatFormatting.GRAY),
    ACCEPTABLE("acceptable", ChatFormatting.WHITE),
    FINE("fine", ChatFormatting.AQUA),
    EXCELLENT("excellent", ChatFormatting.LIGHT_PURPLE),
    FLAWLESS("flawless", ChatFormatting.GOLD);

    private static final float[] DEFAULTS = {0.20F, 0.40F, 0.60F, 0.78F, 0.92F};

    private final String name;
    private final ChatFormatting color;

    CutGrade(String name, ChatFormatting color) {
        this.name = name;
        this.color = color;
    }

    public String id() { return name; }
    public ChatFormatting color() { return color; }

    public MutableComponent displayName() {
        return Component.translatable("cut_grade.immersive_jewelry." + name).withStyle(color);
    }

    public static CutGrade of(float quality) {
        return of(quality, thresholds());
    }

    public static CutGrade of(float quality, float[] thresholds) {
        CutGrade grade = RUINED;
        for (int i = 0; i < thresholds.length && i < 5; i++) {
            if (quality >= thresholds[i]) grade = values()[i + 1];
        }
        return grade;
    }

    public static float[] thresholds() {
        List<Double> configured = ServerConfig.doubles(ServerConfig.CUT_GRADE_THRESHOLDS);
        if (configured.size() != 5) return DEFAULTS.clone();
        float[] out = new float[5];
        for (int i = 0; i < 5; i++) out[i] = configured.get(i).floatValue();
        return out;
    }
}
