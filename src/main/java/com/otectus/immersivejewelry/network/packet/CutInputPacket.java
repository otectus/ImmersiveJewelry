package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.minigame.SessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A run of consecutive cutting steps: where the stone was pressed (12-bit fixed point) and whether the grind was
 * held, one entry per 20 ms step. Inputs only; the server replays them.
 */
public record CutInputPacket(int sessionId, int firstStep, short[] xs, short[] ys, long grinding) {
    public static final int MAX_STEPS = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(firstStep);
        int n = Math.min(MAX_STEPS, Math.min(xs.length, ys.length));
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeShort(xs[i]);
            buf.writeShort(ys[i]);
        }
        buf.writeLong(grinding);
    }

    public static CutInputPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int first = buf.readVarInt();
        int n = Math.max(0, Math.min(MAX_STEPS, buf.readVarInt()));
        short[] xs = new short[n];
        short[] ys = new short[n];
        for (int i = 0; i < n; i++) {
            xs[i] = buf.readShort();
            ys[i] = buf.readShort();
        }
        return new CutInputPacket(id, first, xs, ys, buf.readLong());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.cutInput(player, this);
        });
        context.setPacketHandled(true);
    }
}
