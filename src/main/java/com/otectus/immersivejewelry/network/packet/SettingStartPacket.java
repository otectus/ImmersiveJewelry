package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.client.ClientPacketHandler;
import com.otectus.immersivejewelry.minigame.setting.SettingParams;
import com.otectus.immersivejewelry.minigame.setting.SettingSession;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Starts the setting (or removal) minigame with the parameters both sides will simulate. */
public record SettingStartPacket(int sessionId, BlockPos pos, SettingSession.Mode mode, SettingParams params, List<GemData> stones,
                                 ItemStack workpiece, int readyMs) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeBlockPos(pos);
        buf.writeEnum(mode);
        params.write(buf);
        buf.writeCollection(stones, (b, g) -> g.write(b));
        buf.writeItem(workpiece);
        buf.writeVarInt(readyMs);
    }

    public static SettingStartPacket decode(FriendlyByteBuf buf) {
        return new SettingStartPacket(buf.readVarInt(), buf.readBlockPos(), buf.readEnum(SettingSession.Mode.class), SettingParams.read(buf),
                buf.readList(GemData::read), buf.readItem(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.settingStart(this));
    }
}
