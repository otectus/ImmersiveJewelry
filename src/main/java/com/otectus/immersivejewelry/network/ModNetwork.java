package com.otectus.immersivejewelry.network;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.network.packet.BenchActionPacket;
import com.otectus.immersivejewelry.network.packet.CancelSessionPacket;
import com.otectus.immersivejewelry.network.packet.CloseSessionPacket;
import com.otectus.immersivejewelry.network.packet.CutInputPacket;
import com.otectus.immersivejewelry.network.packet.CutResultPacket;
import com.otectus.immersivejewelry.network.packet.CutStartPacket;
import com.otectus.immersivejewelry.network.packet.OpenBenchPacket;
import com.otectus.immersivejewelry.network.packet.OpenCutSelectPacket;
import com.otectus.immersivejewelry.network.packet.OpenJewelrySlotsPacket;
import com.otectus.immersivejewelry.network.packet.SelectCutPacket;
import com.otectus.immersivejewelry.network.packet.SettingInputPacket;
import com.otectus.immersivejewelry.network.packet.SettingResultPacket;
import com.otectus.immersivejewelry.network.packet.SettingStartPacket;
import com.otectus.immersivejewelry.network.packet.StartSettingPacket;
import com.otectus.immersivejewelry.network.packet.StationEffectPacket;
import com.otectus.immersivejewelry.network.packet.SyncDefinitionsPacket;
import com.otectus.immersivejewelry.network.packet.SyncProfilePacket;
import com.otectus.immersivejewelry.network.packet.WheelActionPacket;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Small, purpose-specific packets. Clients report inputs and choices only; the server owns every outcome. */
public final class ModNetwork {
    public static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ImmersiveJewelry.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        // Server -> client
        CHANNEL.messageBuilder(SyncDefinitionsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncDefinitionsPacket::encode).decoder(SyncDefinitionsPacket::decode).consumerMainThread(SyncDefinitionsPacket::handle).add();
        CHANNEL.messageBuilder(SyncProfilePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncProfilePacket::encode).decoder(SyncProfilePacket::decode).consumerMainThread(SyncProfilePacket::handle).add();
        CHANNEL.messageBuilder(OpenCutSelectPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenCutSelectPacket::encode).decoder(OpenCutSelectPacket::decode).consumerMainThread(OpenCutSelectPacket::handle).add();
        CHANNEL.messageBuilder(CutStartPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CutStartPacket::encode).decoder(CutStartPacket::decode).consumerMainThread(CutStartPacket::handle).add();
        CHANNEL.messageBuilder(CutResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CutResultPacket::encode).decoder(CutResultPacket::decode).consumerMainThread(CutResultPacket::handle).add();
        CHANNEL.messageBuilder(OpenBenchPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenBenchPacket::encode).decoder(OpenBenchPacket::decode).consumerMainThread(OpenBenchPacket::handle).add();
        CHANNEL.messageBuilder(SettingStartPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SettingStartPacket::encode).decoder(SettingStartPacket::decode).consumerMainThread(SettingStartPacket::handle).add();
        CHANNEL.messageBuilder(SettingResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SettingResultPacket::encode).decoder(SettingResultPacket::decode).consumerMainThread(SettingResultPacket::handle).add();
        CHANNEL.messageBuilder(CloseSessionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CloseSessionPacket::encode).decoder(CloseSessionPacket::decode).consumerMainThread(CloseSessionPacket::handle).add();
        CHANNEL.messageBuilder(StationEffectPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationEffectPacket::encode).decoder(StationEffectPacket::decode).consumerMainThread(StationEffectPacket::handle).add();
        // Client -> server
        CHANNEL.messageBuilder(SelectCutPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectCutPacket::encode).decoder(SelectCutPacket::decode).consumerNetworkThread(SelectCutPacket::handle).add();
        CHANNEL.messageBuilder(CutInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CutInputPacket::encode).decoder(CutInputPacket::decode).consumerNetworkThread(CutInputPacket::handle).add();
        CHANNEL.messageBuilder(WheelActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WheelActionPacket::encode).decoder(WheelActionPacket::decode).consumerNetworkThread(WheelActionPacket::handle).add();
        CHANNEL.messageBuilder(StartSettingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StartSettingPacket::encode).decoder(StartSettingPacket::decode).consumerNetworkThread(StartSettingPacket::handle).add();
        CHANNEL.messageBuilder(SettingInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SettingInputPacket::encode).decoder(SettingInputPacket::decode).consumerNetworkThread(SettingInputPacket::handle).add();
        CHANNEL.messageBuilder(BenchActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BenchActionPacket::encode).decoder(BenchActionPacket::decode).consumerNetworkThread(BenchActionPacket::handle).add();
        CHANNEL.messageBuilder(CancelSessionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CancelSessionPacket::encode).decoder(CancelSessionPacket::decode).consumerNetworkThread(CancelSessionPacket::handle).add();
        CHANNEL.messageBuilder(OpenJewelrySlotsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenJewelrySlotsPacket::encode).decoder(OpenJewelrySlotsPacket::decode).consumerNetworkThread(OpenJewelrySlotsPacket::handle).add();
    }

    private ModNetwork() {}
}
