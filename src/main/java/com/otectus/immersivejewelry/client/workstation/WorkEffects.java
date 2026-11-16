package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.ParticleStyle;
import net.minecraft.resources.ResourceLocation;

/**
 * Scene particles of the workstations, drawn inside the station's scene (they turn with it and stay put on it). Purely
 * cosmetic and local: density follows the client's particle option and never changes an outcome.
 */
public final class WorkEffects {
    /** Wet grit thrown off where the stone meets the lap. */
    public static final ResourceLocation SLURRY = ImmersiveJewelry.id("slurry");
    /** Fine stone dust in the gem's colour. */
    public static final ResourceLocation STONE_DUST = ImmersiveJewelry.id("stone_dust");
    /** A chip flying off. */
    public static final ResourceLocation CHIP = ImmersiveJewelry.id("chip");
    /** A brief glint on a new face or a polished edge. */
    public static final ResourceLocation GLINT = ImmersiveJewelry.id("glint");
    /** Flecks of compound or patina lifted by the cloth. */
    public static final ResourceLocation FLECK = ImmersiveJewelry.id("fleck");

    static void register() {
        ResourceLocation[] dust = {ImmersiveJewelry.id("gem_dust_0"), ImmersiveJewelry.id("gem_dust_1"), ImmersiveJewelry.id("gem_dust_2"),
                ImmersiveJewelry.id("gem_dust_3")};
        ResourceLocation[] sparkle = {ImmersiveJewelry.id("gem_sparkle_0"), ImmersiveJewelry.id("gem_sparkle_1"),
                ImmersiveJewelry.id("gem_sparkle_2"), ImmersiveJewelry.id("gem_sparkle_3")};
        DiegesisClient.registerParticle(SLURRY, ParticleStyle.builder(dust).color(0xC09A9488).size(0.12F, 0.22F).life(0.35F, 0.7F)
                .gravity(-30F).drag(0.6F).stretch().build());
        DiegesisClient.registerParticle(STONE_DUST, ParticleStyle.builder(dust).size(0.08F, 0.16F).life(0.6F, 1.3F)
                .gravity(-4F).drag(0.85F).wobble(0.15F).build());
        DiegesisClient.registerParticle(CHIP, ParticleStyle.builder(dust).size(0.18F, 0.3F).life(0.4F, 0.8F)
                .gravity(-45F).drag(0.3F).build());
        DiegesisClient.registerParticle(GLINT, ParticleStyle.builder(sparkle).size(0.2F, 0.38F).life(0.25F, 0.55F)
                .emissive().pop().build());
        DiegesisClient.registerParticle(FLECK, ParticleStyle.builder(dust).color(0xD0E8E0D0).size(0.06F, 0.12F).life(0.4F, 0.9F)
                .gravity(-6F).drag(0.8F).wobble(0.1F).build());
    }

    private WorkEffects() {}
}
