package com.otectus.immersivejewelry.network;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.network.packet.KnownDesignsPacket;
import com.otectus.immersivejewelry.network.packet.OpenJewelrySlotsPacket;
import com.otectus.immersivejewelry.network.packet.StationEffectPacket;
import com.otectus.immersivejewelry.network.packet.StationNoticePacket;
import com.otectus.immersivejewelry.network.packet.SyncDefinitionsPacket;
import com.otectus.immersivejewelry.network.packet.SyncProfilePacket;
import com.otectus.immersivejewelry.network.packet.WorkCommandPacket;
import com.otectus.immersivejewelry.network.packet.WorkInputPacket;
import com.otectus.immersivejewelry.network.packet.WorkLeasePacket;
import com.otectus.immersivejewelry.network.packet.WorkPrefsPacket;
import com.otectus.immersivejewelry.network.packet.WornAppearancePacket;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Small, purpose-specific packets. Clients report inputs and choices only; the server owns every outcome.
 *
 * <p>Protocol 3 (1.2.0): the minigame session packets of protocols 1 and 2 are gone. Work at a station is an operator
 * lease ({@link WorkLeasePacket}), batches of fixed-step control samples ({@link WorkInputPacket}), discrete actions
 * ({@link WorkCommandPacket}) and the player's assist wishes ({@link WorkPrefsPacket}); the work's public state reaches
 * the players tracking a station through its block entity updates. A 1.1 client or server is refused at login.</p>
 *
 * <p>Protocol 4 (1.2.2): {@link WornAppearancePacket}, the sanitized look of what a player wears in the built-in slots, so
 * other clients can draw it. A 1.2.0 or 1.2.1 client or server is refused at login.</p>
 */
public final class ModNetwork {
    public static final String VERSION = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ImmersiveJewelry.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        // Server -> client
        CHANNEL.messageBuilder(SyncDefinitionsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncDefinitionsPacket::encode).decoder(SyncDefinitionsPacket::decode).consumerMainThread(SyncDefinitionsPacket::handle).add();
        CHANNEL.messageBuilder(SyncProfilePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncProfilePacket::encode).decoder(SyncProfilePacket::decode).consumerMainThread(SyncProfilePacket::handle).add();
        CHANNEL.messageBuilder(StationEffectPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationEffectPacket::encode).decoder(StationEffectPacket::decode).consumerMainThread(StationEffectPacket::handle).add();
        CHANNEL.messageBuilder(StationNoticePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationNoticePacket::encode).decoder(StationNoticePacket::decode).consumerMainThread(StationNoticePacket::handle).add();
        CHANNEL.messageBuilder(WorkLeasePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WorkLeasePacket::encode).decoder(WorkLeasePacket::decode).consumerMainThread(WorkLeasePacket::handle).add();
        CHANNEL.messageBuilder(KnownDesignsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KnownDesignsPacket::encode).decoder(KnownDesignsPacket::decode).consumerMainThread(KnownDesignsPacket::handle).add();
        CHANNEL.messageBuilder(WornAppearancePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WornAppearancePacket::encode).decoder(WornAppearancePacket::decode).consumerMainThread(WornAppearancePacket::handle).add();
        // Client -> server
        CHANNEL.messageBuilder(OpenJewelrySlotsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenJewelrySlotsPacket::encode).decoder(OpenJewelrySlotsPacket::decode).consumerNetworkThread(OpenJewelrySlotsPacket::handle).add();
        CHANNEL.messageBuilder(WorkInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorkInputPacket::encode).decoder(WorkInputPacket::decode).consumerNetworkThread(WorkInputPacket::handle).add();
        CHANNEL.messageBuilder(WorkCommandPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorkCommandPacket::encode).decoder(WorkCommandPacket::decode).consumerNetworkThread(WorkCommandPacket::handle).add();
        CHANNEL.messageBuilder(WorkPrefsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorkPrefsPacket::encode).decoder(WorkPrefsPacket::decode).consumerNetworkThread(WorkPrefsPacket::handle).add();
    }

    private ModNetwork() {}
}
