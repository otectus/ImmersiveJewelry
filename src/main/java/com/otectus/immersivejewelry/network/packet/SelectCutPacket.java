package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.minigame.SessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The player chose a cut for the stone on a wheel (with their accessibility assists). */
public record SelectCutPacket(BlockPos pos, ResourceLocation cut, boolean largerTargets, boolean slower) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(cut);
        buf.writeBoolean(largerTargets);
        buf.writeBoolean(slower);
    }

    public static SelectCutPacket decode(FriendlyByteBuf buf) {
        return new SelectCutPacket(buf.readBlockPos(), buf.readResourceLocation(), buf.readBoolean(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.selectCut(player, this);
        });
        context.setPacketHandled(true);
    }
}
