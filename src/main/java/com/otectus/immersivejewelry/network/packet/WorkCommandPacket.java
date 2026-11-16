package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.WorkInputService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One discrete action at a station the player operates: pick a plan, move a real stack, seat a stone, finish, pack. The
 * arguments are a small compound (at most {@value #MAX_ARGS_BYTES} bytes) that the server validates field by field for
 * the action; nothing in it is ever trusted as an outcome. {@code seq} numbers the lease's commands from 1 (protocol 5): the
 * server drops a repeated or older one, and paces them.
 */
public record WorkCommandPacket(BlockPos pos, int token, int seq, long workId, WorkCommand command, CompoundTag args) {
    /**
     * The arguments' budget in {@code CompoundTag.sizeInBytes} terms (which charges about 80 bytes per entry). 1024 since
     * protocol 5: a seat with its generation and a preparation naming 36 preferred slots fit with room to spare
     * ({@code StorageProtocolTest}); the decoder's accounter allows four times this, as before.
     */
    public static final int MAX_ARGS_BYTES = 1024;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(token);
        buf.writeVarInt(seq);
        buf.writeLong(workId);
        buf.writeEnum(command);
        buf.writeNbt(args);
    }

    public static WorkCommandPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int token = buf.readInt();
        int seq = buf.readVarInt();
        long work = buf.readLong();
        WorkCommand command = buf.readEnum(WorkCommand.class);
        CompoundTag args = buf.readNbt(new NbtAccounter(MAX_ARGS_BYTES * 4L));
        return new WorkCommandPacket(pos, token, seq, work, command, args == null ? new CompoundTag() : args);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) WorkInputService.command(player, this);
        });
        context.setPacketHandled(true);
    }
}
