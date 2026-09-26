package com.otectus.immersivejewelry.registry;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.particle.GemParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ImmersiveJewelry.MOD_ID);

    /** Fine coloured dust thrown off the wheel. */
    public static final RegistryObject<ParticleType<GemParticleOptions>> GEM_DUST = PARTICLES.register("gem_dust",
            () -> GemParticleOptions.type(false));
    /** A four-pointed twinkle in the gem's colour. */
    public static final RegistryObject<ParticleType<GemParticleOptions>> GEM_SPARKLE = PARTICLES.register("gem_sparkle",
            () -> GemParticleOptions.type(false));
    /** A tiny warm spark from a prong or a polishing wheel. */
    public static final RegistryObject<SimpleParticleType> METAL_SPARK = PARTICLES.register("metal_spark",
            () -> new SimpleParticleType(false));

    private ModParticles() {}
}
