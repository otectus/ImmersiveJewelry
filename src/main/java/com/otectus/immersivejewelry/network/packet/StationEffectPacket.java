package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Particles at a station or around a wearer; purely cosmetic. */
public record StationEffectPacket(double x, double y, double z, int type, int color, float strength) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeVarInt(type);
        buf.writeInt(color);
        buf.writeFloat(strength);
    }

    public static StationEffectPacket decode(FriendlyByteBuf buf) {
        return new StationEffectPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readInt(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.stationEffect(this));
    }
}
