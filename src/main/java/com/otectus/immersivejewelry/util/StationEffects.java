package com.otectus.immersivejewelry.util;

import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.StationEffectPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.PacketDistributor;

/** Cosmetic particles sent to players near a station or wearer. */
public final class StationEffects {
    public static final int GRIND = 0, CHIP = 1, FACET = 2, SPARKLE = 3, POLISH = 4, PRONG = 5, FINISHED = 6;

    public static void send(ServerLevel level, BlockPos pos, int type, int color, float strength) {
        send(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, type, color, strength);
    }

    public static void send(ServerLevel level, double x, double y, double z, int type, int color, float strength) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, 32.0, level.dimension())),
                new StationEffectPacket(x, y, z, type, color, strength));
    }

    public static void sparkleAt(ServerLevel level, double x, double y, double z, int color, int count) {
        send(level, x, y, z, SPARKLE, color, count);
    }

    private StationEffects() {}
}
