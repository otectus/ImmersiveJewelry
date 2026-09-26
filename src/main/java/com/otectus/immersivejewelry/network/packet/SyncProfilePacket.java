package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.effect.ClientProfile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** The wearer's summed profile values (mining speed, jump, quickdraw) for client prediction. */
public record SyncProfilePacket(Map<String, Float> values) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeMap(values, (b, k) -> b.writeUtf(k, 64), FriendlyByteBuf::writeFloat);
    }

    public static SyncProfilePacket decode(FriendlyByteBuf buf) {
        Map<String, Float> map = buf.readMap(HashMap::new, b -> b.readUtf(64), FriendlyByteBuf::readFloat);
        return new SyncProfilePacket(map);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ClientProfile.set(values);
    }
}
