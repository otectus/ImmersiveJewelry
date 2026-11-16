package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Which sketch designs the player has learned, and whether the server requires sketches at all: the design folio shows
 * every design honestly (known, or where its sketch comes from). The server still checks knowledge on every use.
 */
public record KnownDesignsPacket(List<ResourceLocation> learned, boolean requireSketches) {
    public static final int MAX = 512;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(Math.min(MAX, learned.size()));
        for (int i = 0; i < learned.size() && i < MAX; i++) buf.writeResourceLocation(learned.get(i));
        buf.writeBoolean(requireSketches);
    }

    public static KnownDesignsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX) throw new IllegalArgumentException("Too many designs: " + n);
        List<ResourceLocation> list = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readResourceLocation());
        return new KnownDesignsPacket(list, buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.knownDesigns(this));
    }
}
