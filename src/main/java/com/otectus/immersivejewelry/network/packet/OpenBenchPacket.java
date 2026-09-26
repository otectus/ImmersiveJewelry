package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.client.ClientPacketHandler;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Opens the bench screen. For a blank: the designs the player knows for its form, the cut stones they carry and the
 * finishes. For a finished piece: the rework actions. All of it is re-validated when the player chooses.
 */
public record OpenBenchPacket(BlockPos pos, ItemStack workpiece, boolean rework, List<Design> designs, List<Stone> stones,
                              List<Finish> finishes, boolean canRetighten, boolean canRemove, ItemStack repairCost,
                              boolean repairAffordable, float repairCap) {
    public static final int MAX_ENTRIES = 64;

    public record Design(ResourceLocation id, Component name, int stones, SettingStyle style, int points, float tightness,
                         List<ResourceLocation> cuts, List<ResourceLocation> gems, List<ResourceLocation> finishes) {}

    public record Stone(int slot, GemData data, int count) {}

    public record Finish(ResourceLocation id, ItemStack cost, boolean affordable) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeItem(workpiece);
        buf.writeBoolean(rework);
        buf.writeCollection(designs.subList(0, Math.min(MAX_ENTRIES, designs.size())), (b, d) -> {
            b.writeResourceLocation(d.id);
            b.writeComponent(d.name);
            b.writeVarInt(d.stones);
            b.writeEnum(d.style);
            b.writeVarInt(d.points);
            b.writeFloat(d.tightness);
            b.writeCollection(d.cuts, FriendlyByteBuf::writeResourceLocation);
            b.writeCollection(d.gems, FriendlyByteBuf::writeResourceLocation);
            b.writeCollection(d.finishes, FriendlyByteBuf::writeResourceLocation);
        });
        buf.writeCollection(stones.subList(0, Math.min(MAX_ENTRIES, stones.size())), (b, s) -> {
            b.writeVarInt(s.slot);
            s.data.write(b);
            b.writeVarInt(s.count);
        });
        buf.writeCollection(finishes, (b, f) -> {
            b.writeResourceLocation(f.id);
            b.writeItem(f.cost);
            b.writeBoolean(f.affordable);
        });
        buf.writeBoolean(canRetighten);
        buf.writeBoolean(canRemove);
        buf.writeItem(repairCost);
        buf.writeBoolean(repairAffordable);
        buf.writeFloat(repairCap);
    }

    public static OpenBenchPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        ItemStack workpiece = buf.readItem();
        boolean rework = buf.readBoolean();
        List<Design> designs = buf.readList(b -> new Design(b.readResourceLocation(), b.readComponent(), b.readVarInt(),
                b.readEnum(SettingStyle.class), b.readVarInt(), b.readFloat(), b.readList(FriendlyByteBuf::readResourceLocation),
                b.readList(FriendlyByteBuf::readResourceLocation), b.readList(FriendlyByteBuf::readResourceLocation)));
        List<Stone> stones = buf.readList(b -> new Stone(b.readVarInt(), GemData.read(b), b.readVarInt()));
        List<Finish> finishes = buf.readList(b -> new Finish(b.readResourceLocation(), b.readItem(), b.readBoolean()));
        return new OpenBenchPacket(pos, workpiece, rework, designs, stones, finishes, buf.readBoolean(), buf.readBoolean(), buf.readItem(),
                buf.readBoolean(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openBench(this));
    }
}
