package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.minigame.SessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A run of consecutive setting steps: whether the squeeze was held, one bit per 20 ms step. */
public record SettingInputPacket(int sessionId, int firstStep, int count, long squeezing) {
    public static final int MAX_STEPS = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(firstStep);
        buf.writeVarInt(Math.max(0, Math.min(MAX_STEPS, count)));
        buf.writeLong(squeezing);
    }

    public static SettingInputPacket decode(FriendlyByteBuf buf) {
        return new SettingInputPacket(buf.readVarInt(), buf.readVarInt(), Math.max(0, Math.min(MAX_STEPS, buf.readVarInt())), buf.readLong());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.settingInput(player, this);
        });
        context.setPacketHandled(true);
    }
}
