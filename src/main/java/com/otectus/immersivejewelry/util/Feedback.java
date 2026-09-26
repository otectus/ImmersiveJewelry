package com.otectus.immersivejewelry.util;

import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Short action-bar messages for station interactions. */
public final class Feedback {

    public static void info(Player player, String key, Object... args) {
        player.displayClientMessage(message(key, args), true);
    }

    public static void fail(Player player, String key, Object... args) {
        player.displayClientMessage(message(key, args), true);
        player.playNotifySound(ModSounds.ACTION_FAIL.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    public static Component message(String key, Object... args) {
        return Component.translatable("message.immersive_jewelry." + key, args);
    }

    private Feedback() {}
}
