package com.otectus.immersivejewelry.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.immersivejewelry.data.DefinitionKind;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.network.packet.StationEffectPacket;
import com.otectus.immersivejewelry.network.packet.StationNoticePacket;
import com.otectus.immersivejewelry.network.packet.SyncDefinitionsPacket;
import com.otectus.immersivejewelry.network.packet.WorkLeasePacket;
import com.otectus.immersivejewelry.network.packet.KnownDesignsPacket;
import com.otectus.immersivejewelry.client.workstation.WorkstationClient;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client handlers for server packets. Runs on the client main thread. */
public final class ClientPacketHandler {

    /** Parses the server's definitions into the client's own snapshot (never shared with an integrated server). */
    public static void syncDefinitions(SyncDefinitionsPacket packet) {
        Map<DefinitionKind, Map<ResourceLocation, JsonObject>> json = new EnumMap<>(DefinitionKind.class);
        packet.json().forEach((kind, map) -> {
            Map<ResourceLocation, JsonObject> parsed = new LinkedHashMap<>();
            map.forEach((id, text) -> {
                try {
                    parsed.put(id, JsonParser.parseString(text).getAsJsonObject());
                } catch (RuntimeException ignored) {
                    // Reported by the server when it built the definition.
                }
            });
            json.put(kind, parsed);
        });
        List<String> errors = new ArrayList<>();
        JewelryDefinitions.setClient(JewelryDefinitions.build(json, errors::add));
        JewelryDefinitions.logErrors(errors, "client");
        JewelryColors.clearSamples();
        com.otectus.immersivejewelry.client.workstation.WorkstationClient.onDefinitionsChanged();
    }

    public static void knownDesigns(KnownDesignsPacket packet) {
        WorkstationClient.onKnownDesigns(packet.learned(), packet.requireSketches());
    }

    public static void workLease(WorkLeasePacket packet) {
        WorkstationClient.onLease(packet.pos(), packet.token());
    }

    public static void stationNotice(StationNoticePacket packet) {
        StationNotices.show(packet.message(), packet.failure());
    }

    public static void stationEffect(StationEffectPacket packet) {
        ClientEffects.spawn(packet);
    }

    private ClientPacketHandler() {}
}
