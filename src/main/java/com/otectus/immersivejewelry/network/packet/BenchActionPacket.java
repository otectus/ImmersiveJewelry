package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.minigame.SessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** A rework choice at the bench: remove stones, retighten, repair, refinish, or take the piece. */
public record BenchActionPacket(BlockPos pos, Action action, @Nullable ResourceLocation finish, boolean largerTargets, boolean slower) {
    public enum Action { REMOVE, RETIGHTEN, REPAIR, REFINISH, TAKE }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(action);
        buf.writeNullable(finish, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(largerTargets);
        buf.writeBoolean(slower);
    }

    public static BenchActionPacket decode(FriendlyByteBuf buf) {
        return new BenchActionPacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readNullable(FriendlyByteBuf::readResourceLocation),
                buf.readBoolean(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.benchAction(player, this);
        });
        context.setPacketHandled(true);
    }
}
