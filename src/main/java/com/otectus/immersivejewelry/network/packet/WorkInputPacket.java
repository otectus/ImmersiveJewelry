package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.workstation.WorkInputService;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A batch of consecutive fixed-step control samples from the operator: the station, the lease token, the work it was
 * recorded against, a strictly increasing batch number, the index of the first step and at most {@value #MAX_SAMPLES}
 * quantized samples. Inputs only; the server reconstructs every tool position and contact itself.
 */
public record WorkInputPacket(BlockPos pos, int token, long workId, int seq, int firstStep, List<WorkSample> samples) {
    public static final int MAX_SAMPLES = 10;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(token);
        buf.writeLong(workId);
        buf.writeVarInt(seq);
        buf.writeVarInt(firstStep);
        int n = Math.min(MAX_SAMPLES, samples.size());
        buf.writeByte(n);
        for (int i = 0; i < n; i++) samples.get(i).write(buf);
    }

    public static WorkInputPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int token = buf.readInt();
        long work = buf.readLong();
        int seq = buf.readVarInt();
        int first = buf.readVarInt();
        int n = buf.readUnsignedByte();
        if (n > MAX_SAMPLES) throw new IllegalArgumentException("Too many samples: " + n);
        List<WorkSample> samples = new ArrayList<>(n);
        for (int i = 0; i < n; i++) samples.add(WorkSample.read(buf));
        return new WorkInputPacket(pos, token, work, seq, first, samples);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) WorkInputService.input(player, this);
        });
        context.setPacketHandled(true);
    }
}
