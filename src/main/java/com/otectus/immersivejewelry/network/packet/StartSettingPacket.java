package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.minigame.SessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** The player chose a design, the inventory slots of the stones to set, and a finish. */
public record StartSettingPacket(BlockPos pos, ResourceLocation design, int[] slots, @Nullable ResourceLocation finish,
                                 boolean largerTargets, boolean slower) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(design);
        buf.writeVarIntArray(slots);
        buf.writeNullable(finish, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(largerTargets);
        buf.writeBoolean(slower);
    }

    public static StartSettingPacket decode(FriendlyByteBuf buf) {
        return new StartSettingPacket(buf.readBlockPos(), buf.readResourceLocation(), buf.readVarIntArray(8),
                buf.readNullable(FriendlyByteBuf::readResourceLocation), buf.readBoolean(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.startSetting(player, this);
        });
        context.setPacketHandled(true);
    }
}
