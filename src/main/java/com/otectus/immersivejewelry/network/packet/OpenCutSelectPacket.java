package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.client.ClientPacketHandler;
import com.otectus.immersivejewelry.item.LapItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Opens the cut choice for the stone on a wheel, with each cut's numbers as the server computed them. */
public record OpenCutSelectPacket(BlockPos pos, GemData stone, boolean recut, LapItem.Tier tier, List<Option> options) {

    /** @param difficulty 0..1 for display; @param size resulting size in carats; @param potency trait strength modifier */
    public record Option(ResourceLocation cut, float difficulty, float size, float potency, float stability, int facets) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        stone.write(buf);
        buf.writeBoolean(recut);
        buf.writeEnum(tier);
        buf.writeCollection(options, (b, o) -> {
            b.writeResourceLocation(o.cut);
            b.writeFloat(o.difficulty);
            b.writeFloat(o.size);
            b.writeFloat(o.potency);
            b.writeFloat(o.stability);
            b.writeVarInt(o.facets);
        });
    }

    public static OpenCutSelectPacket decode(FriendlyByteBuf buf) {
        return new OpenCutSelectPacket(buf.readBlockPos(), GemData.read(buf), buf.readBoolean(), buf.readEnum(LapItem.Tier.class),
                buf.readList(b -> new Option(b.readResourceLocation(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt())));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openCutSelect(this));
    }
}
