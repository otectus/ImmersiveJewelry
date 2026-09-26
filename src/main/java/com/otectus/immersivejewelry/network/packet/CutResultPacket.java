package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** The server's verdict on a cut. {@code after} is null when the stone shattered. */
public record CutResultPacket(int sessionId, GemData before, @Nullable GemData after, float quality, int chips, boolean timedOut, int xp) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        before.write(buf);
        buf.writeNullable(after, (b, g) -> g.write(b));
        buf.writeFloat(quality);
        buf.writeVarInt(chips);
        buf.writeBoolean(timedOut);
        buf.writeVarInt(xp);
    }

    public static CutResultPacket decode(FriendlyByteBuf buf) {
        return new CutResultPacket(buf.readVarInt(), GemData.read(buf), buf.readNullable(GemData::read), buf.readFloat(), buf.readVarInt(),
                buf.readBoolean(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.cutResult(this));
    }
}
