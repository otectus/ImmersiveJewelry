package com.otectus.immersivejewelry.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/** A particle tinted in a gem's colour (gem dust, sparkles). */
public record GemParticleOptions(ParticleType<GemParticleOptions> type, int color, float scale) implements ParticleOptions {

    @SuppressWarnings("deprecation")
    private static final Deserializer<GemParticleOptions> DESERIALIZER = new Deserializer<>() {
        @Override
        public GemParticleOptions fromCommand(ParticleType<GemParticleOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            int color = reader.readInt();
            reader.expect(' ');
            float scale = reader.readFloat();
            return new GemParticleOptions(type, color, scale);
        }

        @Override
        public GemParticleOptions fromNetwork(ParticleType<GemParticleOptions> type, FriendlyByteBuf buf) {
            return new GemParticleOptions(type, buf.readInt(), buf.readFloat());
        }
    };

    public static ParticleType<GemParticleOptions> type(boolean alwaysShow) {
        return new ParticleType<>(alwaysShow, DESERIALIZER) {
            @Override
            public Codec<GemParticleOptions> codec() {
                return makeCodec(this);
            }
        };
    }

    private static Codec<GemParticleOptions> makeCodec(ParticleType<GemParticleOptions> type) {
        return RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("color").forGetter(GemParticleOptions::color),
                Codec.FLOAT.fieldOf("scale").forGetter(GemParticleOptions::scale)
        ).apply(inst, (color, scale) -> new GemParticleOptions(type, color, scale)));
    }

    @Override
    public ParticleType<?> getType() {
        return type;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(color);
        buf.writeFloat(scale);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %d %.2f", BuiltInRegistries.PARTICLE_TYPE.getKey(type), color, scale);
    }
}
