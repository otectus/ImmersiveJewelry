package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The server granted ({@code token} not 0) or ended ({@code token} 0) the player's operator lease at a station. Input
 * and commands carry the token; the server ignores any that do not match the lease it holds.
 */
public record WorkLeasePacket(BlockPos pos, int token) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(token);
    }

    public static WorkLeasePacket decode(FriendlyByteBuf buf) {
        return new WorkLeasePacket(buf.readBlockPos(), buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.workLease(this));
    }
}
