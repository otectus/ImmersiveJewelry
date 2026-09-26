package com.otectus.immersivejewelry.client.sound;

import com.otectus.immersivejewelry.client.screen.CuttingScreen;
import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The cutter's own grinding: a loop whose volume and pitch follow the pressure on the stone (spec 53). */
public class GrindLoopSound extends AbstractTickableSoundInstance {
    private final CuttingScreen screen;

    public GrindLoopSound(CuttingScreen screen) {
        super(ModSounds.WHEEL_GRIND.get(), SoundSource.BLOCKS, RandomSource.create());
        this.screen = screen;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public void tick() {
        if (Minecraft.getInstance().screen != screen) {
            stop();
            return;
        }
        float p = screen.pressure();
        float target = screen.grinding() ? 0.15F + 0.45F * p : 0.001F;
        volume += (target - volume) * 0.35F;
        pitch = 0.75F + 0.5F * p;
    }

    public void stopLoop() {
        stop();
    }
}
