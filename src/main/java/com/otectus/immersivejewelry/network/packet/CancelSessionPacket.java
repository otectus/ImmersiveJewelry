package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.minigame.SessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The player closed a minigame: nothing is lost (the stone stays on the wheel, escrow is refunded). */
public record CancelSessionPacket(int sessionId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
    }

    public static CancelSessionPacket decode(FriendlyByteBuf buf) {
        return new CancelSessionPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.cancelFromClient(player, sessionId);
        });
        context.setPacketHandled(true);
    }
}
