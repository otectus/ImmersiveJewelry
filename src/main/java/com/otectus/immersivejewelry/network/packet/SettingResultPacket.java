package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.client.ClientPacketHandler;
import com.otectus.immersivejewelry.minigame.setting.SettingSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/** The server's verdict on a setting, retightening or removal, with the resulting piece (or frame). */
public record SettingResultPacket(int sessionId, SettingSession.Mode mode, float quality, float workmanship, Set<SettingDefect> defects,
                                  boolean timedOut, ItemStack result, int xp) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeEnum(mode);
        buf.writeFloat(quality);
        buf.writeFloat(workmanship);
        buf.writeCollection(defects, FriendlyByteBuf::writeEnum);
        buf.writeBoolean(timedOut);
        buf.writeItem(result);
        buf.writeVarInt(xp);
    }

    public static SettingResultPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        SettingSession.Mode mode = buf.readEnum(SettingSession.Mode.class);
        float quality = buf.readFloat();
        float work = buf.readFloat();
        EnumSet<SettingDefect> defects = EnumSet.noneOf(SettingDefect.class);
        defects.addAll(buf.readList(b -> b.readEnum(SettingDefect.class)));
        return new SettingResultPacket(id, mode, quality, work, defects, buf.readBoolean(), buf.readItem(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.settingResult(this));
    }
}
