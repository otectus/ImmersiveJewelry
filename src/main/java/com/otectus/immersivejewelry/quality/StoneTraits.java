package com.otectus.immersivejewelry.quality;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Words for a stone's natural character: size class, clarity (inclusions) and luster (natural quality). */
public final class StoneTraits {
    private static final String[] SIZES = {"tiny", "small", "medium", "large", "exceptional"};
    private static final float[] SIZE_BOUNDS = {0.35F, 0.7F, 1.05F, 1.4F};
    private static final String[] CLARITY = {"clean", "lightly_included", "included", "heavily_included"};
    private static final float[] CLARITY_BOUNDS = {0.06F, 0.2F, 0.42F};
    private static final String[] LUSTER = {"dull", "fair", "bright", "radiant"};
    private static final float[] LUSTER_BOUNDS = {0.3F, 0.55F, 0.8F};

    public static MutableComponent size(float carats) {
        return Component.translatable("stone.immersive_jewelry.size." + SIZES[band(carats, SIZE_BOUNDS)]);
    }

    public static MutableComponent clarity(float inclusions) {
        int band = band(inclusions, CLARITY_BOUNDS);
        ChatFormatting color = band == 0 ? ChatFormatting.AQUA : band == 3 ? ChatFormatting.GRAY : ChatFormatting.WHITE;
        return Component.translatable("stone.immersive_jewelry.clarity." + CLARITY[band]).withStyle(color);
    }

    public static MutableComponent luster(float natural) {
        int band = band(natural, LUSTER_BOUNDS);
        ChatFormatting color = band == 3 ? ChatFormatting.GOLD : band == 0 ? ChatFormatting.GRAY : ChatFormatting.WHITE;
        return Component.translatable("stone.immersive_jewelry.luster." + LUSTER[band]).withStyle(color);
    }

    private static int band(float value, float[] bounds) {
        int i = 0;
        while (i < bounds.length && value >= bounds[i]) i++;
        return i;
    }

    private StoneTraits() {}
}
