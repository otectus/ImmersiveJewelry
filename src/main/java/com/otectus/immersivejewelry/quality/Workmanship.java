package com.otectus.immersivejewelry.quality;

import com.otectus.immersivejewelry.config.ServerConfig;
import net.minecraft.util.Mth;

/**
 * Overall workmanship (spec 14.4): a weighted sum of the three stages, weights from the server config and
 * normalised. Every stage keeps a real share, so no weak stage becomes irrelevant.
 */
public final class Workmanship {

    public static float compute(float smithing, float cut, float setting) {
        return compute(smithing, cut, setting, (float) ServerConfig.get(ServerConfig.WEIGHT_SMITHING),
                (float) ServerConfig.get(ServerConfig.WEIGHT_CUT), (float) ServerConfig.get(ServerConfig.WEIGHT_SETTING));
    }

    public static float compute(float smithing, float cut, float setting, float ws, float wc, float wt) {
        float total = ws + wc + wt;
        if (total <= 0F) {
            ws = 0.35F;
            wc = 0.35F;
            wt = 0.30F;
            total = 1F;
        }
        return Mth.clamp((smithing * ws + cut * wc + setting * wt) / total, 0F, 1F);
    }

    private Workmanship() {}
}
