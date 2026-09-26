package com.otectus.immersivejewelry;

import com.mojang.logging.LogUtils;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.client.ClientSetup;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.config.CommonConfig;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.effect.BuiltinEffects;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.integration.Integrations;
import com.otectus.immersivejewelry.migration.LegacyJewelcraft;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.recipe.ModConditions;
import com.otectus.immersivejewelry.registry.ModBlockEntities;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.registry.ModCreativeTabs;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModLootModifiers;
import com.otectus.immersivejewelry.registry.ModMenus;
import com.otectus.immersivejewelry.registry.ModParticles;
import com.otectus.immersivejewelry.registry.ModRecipeSerializers;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import com.otectus.immersivejewelry.registry.ModSounds;
import com.otectus.immersivejewelry.registry.ModVillagers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ImmersiveJewelry.MOD_ID)
public class ImmersiveJewelry {
    public static final String MOD_ID = "immersive_jewelry";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImmersiveJewelry() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModRecipeTypes.RECIPE_TYPES.register(modBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModLootModifiers.LOOT_MODIFIERS.register(modBus);
        ModVillagers.POI_TYPES.register(modBus);
        ModVillagers.PROFESSIONS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        BuiltinEffects.register();
        modBus.addListener(LegacyJewelcraft::onRegister);
        modBus.addListener(ModConditions::onRegister);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::processIMC);
        MinecraftForge.EVENT_BUS.addListener(LegacyJewelcraft::onMissingMappings);
        Integrations.init(modBus);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            JewelryEquipment.init();
            Integrations.commonSetup();
        });
    }

    private void processIMC(final InterModProcessEvent event) {
        ImmersiveJewelryAPI.processIMC(event);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
