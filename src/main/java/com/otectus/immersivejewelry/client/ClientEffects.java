package com.otectus.immersivejewelry.client;

import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.network.packet.StationEffectPacket;
import com.otectus.immersivejewelry.particle.GemParticleOptions;
import com.otectus.immersivejewelry.registry.ModParticles;
import com.otectus.immersivejewelry.util.StationEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

/** Station and wearer particles, kept modest (spec 54) and scaled by the client's density setting. */
public final class ClientEffects {

    public static void spawn(StationEffectPacket p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        float density = (float) ClientConfig.get(ClientConfig.PARTICLE_DENSITY);
        if (density <= 0F) return;
        RandomSource r = level.random;
        int color = p.color();
        switch (p.type()) {
            case StationEffects.GRIND -> {
                int n = count(1 + 3 * p.strength(), density);
                for (int i = 0; i < n; i++) dust(level, p.x() + (r.nextFloat() - 0.5) * 0.3, p.y() - 0.05, p.z() + (r.nextFloat() - 0.5) * 0.3,
                        (r.nextFloat() - 0.5) * 0.12, 0.05 + r.nextFloat() * 0.06, (r.nextFloat() - 0.5) * 0.12, color);
            }
            case StationEffects.CHIP -> {
                for (int i = 0; i < count(10, density); i++) dust(level, p.x(), p.y(), p.z(), (r.nextFloat() - 0.5) * 0.3,
                        0.1 + r.nextFloat() * 0.15, (r.nextFloat() - 0.5) * 0.3, color);
                for (int i = 0; i < count(3, density); i++) sparkle(level, p.x(), p.y(), p.z(), 0xFFFFFF, 0.7F);
            }
            case StationEffects.FACET -> {
                for (int i = 0; i < count(2, density); i++) sparkle(level, p.x() + (r.nextFloat() - 0.5) * 0.3, p.y(), p.z() + (r.nextFloat() - 0.5) * 0.3, color, 0.8F);
            }
            case StationEffects.SPARKLE -> {
                for (int i = 0; i < count(p.strength(), density); i++) {
                    sparkle(level, p.x() + (r.nextFloat() - 0.5) * 0.6, p.y() + (r.nextFloat() - 0.5) * 0.5, p.z() + (r.nextFloat() - 0.5) * 0.6, color, 1F);
                }
            }
            case StationEffects.POLISH -> {
                for (int i = 0; i < count(5, density); i++) sparkle(level, p.x() + (r.nextFloat() - 0.5) * 0.5, p.y() - 0.1, p.z() + (r.nextFloat() - 0.5) * 0.5, color, 0.7F);
            }
            case StationEffects.PRONG -> {
                for (int i = 0; i < count(2, density); i++) level.addParticle(ModParticles.METAL_SPARK.get(), p.x() + (r.nextFloat() - 0.5) * 0.2,
                        p.y() - 0.1, p.z() + (r.nextFloat() - 0.5) * 0.2, (r.nextFloat() - 0.5) * 0.08, 0.08, (r.nextFloat() - 0.5) * 0.08);
            }
            case StationEffects.FINISHED -> {
                for (int i = 0; i < count(4 + 8 * p.strength(), density); i++) {
                    sparkle(level, p.x() + (r.nextFloat() - 0.5) * 0.7, p.y() + r.nextFloat() * 0.4, p.z() + (r.nextFloat() - 0.5) * 0.7, color, 1.1F);
                }
            }
            default -> {}
        }
    }

    private static int count(float base, float density) {
        return Math.max(1, Math.round(base * density));
    }

    private static void dust(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int color) {
        level.addParticle(new GemParticleOptions(ModParticles.GEM_DUST.get(), color, 1F), x, y, z, vx, vy, vz);
    }

    private static void sparkle(ClientLevel level, double x, double y, double z, int color, float scale) {
        level.addParticle(new GemParticleOptions(ModParticles.GEM_SPARKLE.get(), color, scale), x, y, z, 0, 0.01, 0);
    }

    private ClientEffects() {}
}
