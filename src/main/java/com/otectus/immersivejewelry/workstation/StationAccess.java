package com.otectus.immersivejewelry.workstation;

import com.otectus.immersivejewelry.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Whether a player may work a station: from their real body, never from where the camera floats. Alive, not a
 * spectator, in the station's dimension, within reach, allowed to interact there (spawn protection, adventure mode,
 * claims that hook the vanilla check) and, for discrete actions, with a line of sight to the station.
 */
public final class StationAccess {

    public static boolean inReach(ServerPlayer player, BlockPos pos) {
        double max = ServerConfig.get(ServerConfig.MAX_STATION_DISTANCE);
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= max * max;
    }

    /** The checks for continuous input (cheap enough for every batch). */
    public static boolean mayWork(ServerPlayer player, BlockPos pos) {
        return player.isAlive() && !player.isSpectator() && !player.hasDisconnected() && player.level().isLoaded(pos)
                && inReach(player, pos) && player.mayInteract(player.level(), pos);
    }

    /** The checks for discrete actions: {@link #mayWork} and a line of sight from the player's eyes to the station. */
    public static boolean mayAct(ServerPlayer player, BlockPos pos) {
        return mayWork(player, pos) && canSee(player, pos);
    }

    public static boolean canSee(ServerPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 centre = Vec3.atCenterOf(pos);
        BlockHitResult hit = player.level().clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private StationAccess() {}
}
