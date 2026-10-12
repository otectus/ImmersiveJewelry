package com.otectus.immersivejewelry.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.immersivejewelry.client.screen.BenchScreen;
import com.otectus.immersivejewelry.client.screen.CutSelectScreen;
import com.otectus.immersivejewelry.client.screen.CuttingScreen;
import com.otectus.immersivejewelry.client.screen.MinigameScreen;
import com.otectus.immersivejewelry.client.screen.SettingScreen;
import com.otectus.immersivejewelry.data.DefinitionKind;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.network.packet.CloseSessionPacket;
import com.otectus.immersivejewelry.network.packet.CutResultPacket;
import com.otectus.immersivejewelry.network.packet.CutStartPacket;
import com.otectus.immersivejewelry.network.packet.OpenBenchPacket;
import com.otectus.immersivejewelry.network.packet.OpenCutSelectPacket;
import com.otectus.immersivejewelry.network.packet.SettingResultPacket;
import com.otectus.immersivejewelry.network.packet.SettingStartPacket;
import com.otectus.immersivejewelry.network.packet.StationEffectPacket;
import com.otectus.immersivejewelry.network.packet.StationNoticePacket;
import com.otectus.immersivejewelry.network.packet.SyncDefinitionsPacket;
import net.minecraft.client.Minecraft;
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
    }

    public static void openCutSelect(OpenCutSelectPacket packet) {
        Minecraft.getInstance().setScreen(new CutSelectScreen(packet));
    }

    public static void cutStart(CutStartPacket packet) {
        Minecraft.getInstance().setScreen(new CuttingScreen(packet));
    }

    public static void cutResult(CutResultPacket packet) {
        if (Minecraft.getInstance().screen instanceof CuttingScreen screen && screen.sessionId() == packet.sessionId()) screen.onResult(packet);
    }

    public static void openBench(OpenBenchPacket packet) {
        if (Minecraft.getInstance().screen instanceof BenchScreen screen && screen.pos().equals(packet.pos())) screen.update(packet);
        else Minecraft.getInstance().setScreen(new BenchScreen(packet));
    }

    public static void settingStart(SettingStartPacket packet) {
        Minecraft.getInstance().setScreen(new SettingScreen(packet));
    }

    public static void settingResult(SettingResultPacket packet) {
        if (Minecraft.getInstance().screen instanceof SettingScreen screen && screen.sessionId() == packet.sessionId()) screen.onResult(packet);
    }

    public static void closeSession(CloseSessionPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MinigameScreen screen && screen.sessionId() == packet.sessionId()) screen.closeByServer();
        if (mc.player != null && !packet.reason().getString().isEmpty()) StationNotices.show(packet.reason(), true);
    }

    public static void stationNotice(StationNoticePacket packet) {
        StationNotices.show(packet.message(), packet.failure());
    }

    public static void stationEffect(StationEffectPacket packet) {
        ClientEffects.spawn(packet);
    }

    private ClientPacketHandler() {}
}
