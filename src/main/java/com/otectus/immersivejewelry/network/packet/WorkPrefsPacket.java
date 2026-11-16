package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The player's gameplay-assist wishes (wider windows, a gentler work rate), sent on joining and when they change. The
 * server applies them to new plans only where its own assist policy allows.
 */
public record WorkPrefsPacket(boolean widerWindows, boolean gentlerRate) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(widerWindows);
        buf.writeBoolean(gentlerRate);
    }

    public static WorkPrefsPacket decode(FriendlyByteBuf buf) {
        return new WorkPrefsPacket(buf.readBoolean(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) WorkSessionManager.setPreferences(player, new WorkSessionManager.Preferences(widerWindows, gentlerRate));
        });
        context.setPacketHandled(true);
    }
}
