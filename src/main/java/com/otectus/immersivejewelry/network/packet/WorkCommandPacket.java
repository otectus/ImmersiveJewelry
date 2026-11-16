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
 * the action; nothing in it is ever trusted as an outcome.
 */
public record WorkCommandPacket(BlockPos pos, int token, long workId, WorkCommand command, CompoundTag args) {
    public static final int MAX_ARGS_BYTES = 512;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(token);
        buf.writeLong(workId);
        buf.writeEnum(command);
        buf.writeNbt(args);
    }

    public static WorkCommandPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int token = buf.readInt();
        long work = buf.readLong();
        WorkCommand command = buf.readEnum(WorkCommand.class);
        CompoundTag args = buf.readNbt(new NbtAccounter(MAX_ARGS_BYTES * 4L));
        return new WorkCommandPacket(pos, token, work, command, args == null ? new CompoundTag() : args);
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
