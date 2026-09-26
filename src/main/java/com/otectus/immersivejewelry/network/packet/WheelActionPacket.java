package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.minigame.StationInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** "Take the stone" from the cut screen. */
public record WheelActionPacket(BlockPos pos) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static WheelActionPacket decode(FriendlyByteBuf buf) {
        return new WheelActionPacket(buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) StationInteractions.takeStone(player, pos);
        });
        context.setPacketHandled(true);
    }
}
