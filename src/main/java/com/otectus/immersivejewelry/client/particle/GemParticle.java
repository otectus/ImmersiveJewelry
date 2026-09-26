package com.otectus.immersivejewelry.client.particle;

import com.otectus.immersivejewelry.particle.GemParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Gem dust (falls and fades), gem sparkles (twinkle in place) and metal sparks (short, bright, falling). */
public class GemParticle extends TextureSheetParticle {
    public enum Kind { DUST, SPARKLE, SPARK }

    private final SpriteSet sprites;
    private final Kind kind;

    protected GemParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int color, float scale,
                          SpriteSet sprites, Kind kind) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.kind = kind;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.rCol = ((color >> 16) & 255) / 255F;
        this.gCol = ((color >> 8) & 255) / 255F;
        this.bCol = (color & 255) / 255F;
        switch (kind) {
            case DUST -> {
                this.quadSize = 0.035F * scale * (0.7F + random.nextFloat() * 0.6F);
                this.lifetime = 14 + random.nextInt(12);
                this.gravity = 0.35F;
                this.friction = 0.9F;
            }
            case SPARKLE -> {
                this.quadSize = 0.07F * scale * (0.8F + random.nextFloat() * 0.4F);
                this.lifetime = 12 + random.nextInt(10);
                this.gravity = -0.01F;
                this.friction = 0.85F;
            }
            case SPARK -> {
                this.quadSize = 0.025F * scale;
                this.lifetime = 6 + random.nextInt(6);
                this.gravity = 0.8F;
                this.friction = 0.94F;
                this.rCol = 1F;
                this.gCol = 0.85F + random.nextFloat() * 0.15F;
                this.bCol = 0.5F + random.nextFloat() * 0.2F;
            }
        }
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        float life = age / (float) lifetime;
        if (kind == Kind.SPARKLE) alpha = Mth.sin(life * Mth.PI);
        else alpha = 1F - life * life;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return kind == Kind.DUST ? super.getLightColor(partialTick) : 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<GemParticleOptions> {
        @Override
        public Particle createParticle(GemParticleOptions o, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new GemParticle(level, x, y, z, vx, vy, vz, o.color(), o.scale(), sprites, kind);
        }
    }

    public record SparkProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new GemParticle(level, x, y, z, vx, vy, vz, 0xFFE0A0, 1F, sprites, Kind.SPARK);
        }
    }
}
