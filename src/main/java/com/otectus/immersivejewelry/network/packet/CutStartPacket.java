package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.client.ClientPacketHandler;
import com.otectus.immersivejewelry.minigame.cutting.CutParams;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Starts the cutting minigame with the parameters both sides will simulate. */
public record CutStartPacket(int sessionId, BlockPos pos, GemData stone, ResourceLocation cut, CutParams params, int readyMs) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeBlockPos(pos);
        stone.write(buf);
        buf.writeResourceLocation(cut);
        params.write(buf);
        buf.writeVarInt(readyMs);
    }

    public static CutStartPacket decode(FriendlyByteBuf buf) {
        return new CutStartPacket(buf.readVarInt(), buf.readBlockPos(), GemData.read(buf), buf.readResourceLocation(), CutParams.read(buf), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.cutStart(this));
    }
}
