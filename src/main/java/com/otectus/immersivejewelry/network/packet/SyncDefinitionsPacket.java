package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.client.ClientPacketHandler;
import com.otectus.immersivejewelry.data.DefinitionKind;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Every resolved definition as JSON text; the client parses its own copy. Sent on join and after each reload. */
public record SyncDefinitionsPacket(Map<DefinitionKind, Map<ResourceLocation, String>> json) {
    private static final int MAX_JSON = 1 << 18;

    public static SyncDefinitionsPacket of(JewelryDefinitions defs) {
        return new SyncDefinitionsPacket(defs.raw());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(json.size());
        json.forEach((kind, map) -> {
            buf.writeEnum(kind);
            buf.writeVarInt(map.size());
            map.forEach((id, text) -> {
                buf.writeResourceLocation(id);
                buf.writeUtf(text, MAX_JSON);
            });
        });
    }

    public static SyncDefinitionsPacket decode(FriendlyByteBuf buf) {
        Map<DefinitionKind, Map<ResourceLocation, String>> out = new EnumMap<>(DefinitionKind.class);
        int kinds = buf.readVarInt();
        for (int i = 0; i < kinds; i++) {
            DefinitionKind kind = buf.readEnum(DefinitionKind.class);
            int n = buf.readVarInt();
            Map<ResourceLocation, String> map = new LinkedHashMap<>();
            for (int j = 0; j < n; j++) map.put(buf.readResourceLocation(), buf.readUtf(MAX_JSON));
            out.put(kind, map);
        }
        return new SyncDefinitionsPacket(out);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.syncDefinitions(this));
    }
}
