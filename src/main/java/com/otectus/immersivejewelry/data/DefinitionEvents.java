package com.otectus.immersivejewelry.data;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.config.CommonConfig;
import com.otectus.immersivejewelry.effect.JewelryWearers;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.SyncDefinitionsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads definitions with the datapacks, builds the server snapshot once tags are bound, and sends it to clients. */
@Mod.EventBusSubscriber(modid = ImmersiveJewelry.MOD_ID)
public final class DefinitionEvents {

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new JewelryDataLoader(event.getConditionContext()));
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            rebuildServer();
        } else {
            JewelryDefinitions.client().invalidateSources();
        }
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SyncDefinitionsPacket packet = SyncDefinitionsPacket.of(JewelryDefinitions.server());
        if (event.getPlayer() != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
        } else {
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
            JewelryWearers.invalidateAll();
        }
    }

    public static void rebuildServer() {
        Map<DefinitionKind, Map<ResourceLocation, JsonObject>> json = new EnumMap<>(DefinitionKind.class);
        JewelryDataLoader.files().forEach((kind, map) -> json.put(kind, new LinkedHashMap<>(map)));
        Map<ResourceLocation, JsonObject> gems = json.computeIfAbsent(DefinitionKind.GEMS, k -> new LinkedHashMap<>());
        GemDetector.addAutomaticGems(gems);
        List<String> errors = new ArrayList<>();
        JewelryDefinitions definitions = JewelryDefinitions.build(json, errors::add);
        JewelryDefinitions.logErrors(errors, "server");
        JewelryDefinitions.setServer(definitions);
        if (CommonConfig.get(CommonConfig.DEBUG_LOGGING)) {
            ImmersiveJewelry.LOGGER.info("Jewelry definitions: {} gems, {} cuts, {} finishes, {} forms, {} traits, {} metals",
                    definitions.gems().size(), definitions.cuts().size(), definitions.finishes().size(),
                    definitions.forms().size(), definitions.traits().size(), definitions.metals().size());
        }
    }

    private DefinitionEvents() {}
}
