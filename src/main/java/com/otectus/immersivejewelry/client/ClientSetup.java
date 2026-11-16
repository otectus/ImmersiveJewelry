package com.otectus.immersivejewelry.client;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.client.particle.GemParticle;
import com.otectus.immersivejewelry.client.render.JewelersBenchRenderer;
import com.otectus.immersivejewelry.client.render.LapidaryWheelRenderer;
import com.otectus.immersivejewelry.client.screen.JewelrySlotsScreen;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.jewelry.ClientHooks;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModBlockEntities;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModMenus;
import com.otectus.immersivejewelry.registry.ModParticles;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Client-only mod-bus registration. Reached only through DistExecutor, so it never loads on a server. */
public final class ClientSetup {
    public static final ResourceLocation CUT_PROPERTY = ImmersiveJewelry.id("cut");
    public static final ResourceLocation FORM_PROPERTY = ImmersiveJewelry.id("form");
    public static final KeyMapping OPEN_SLOTS = new KeyMapping("key.immersive_jewelry.jewelry_slots", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.immersive_jewelry");

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::onClientSetup);
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(StationNotices::register);
        modBus.addListener(JewelryColors::register);
        modBus.addListener(ClientSetup::onParticles);
        modBus.addListener(ClientSetup::onModelsReloaded);
        modBus.addListener(ClientSetup::onKeys);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        com.otectus.immersivejewelry.client.workstation.WorkstationClient.init(modBus);
        com.otectus.immersivejewelry.client.presentation.Presentation.init(modBus);
        com.otectus.immersivejewelry.client.guide.Handbook.init();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        ClientHooks.shiftDown = Screen::hasShiftDown;
        ClientHooks.advancedTooltipsEnabled = () -> ClientConfig.get(ClientConfig.SHOW_ADVANCED_TOOLTIPS);
        ClientHooks.sketchDesigns = () -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return List.of();
            return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get()).stream()
                    .filter(JewelryDesignRecipe::sketch).map(JewelryDesignRecipe::getId).sorted().toList();
        };
        // The demonstrations: the plugin, the handbook's Watch buttons and Ponder's scene time (client/compat/ponder only).
        event.enqueueWork(com.otectus.immersivejewelry.client.compat.ponder.JewelryPonder::init);
        event.enqueueWork(() -> {
            for (Item item : new Item[]{ModItems.RING.get(), ModItems.AMULET.get(), ModItems.BRACELET.get(), ModItems.CIRCLET.get()}) {
                ItemProperties.register(item, CUT_PROPERTY, (stack, level, entity, seed) -> jewelryCut(stack));
            }
            ItemProperties.register(ModItems.CUT_GEM.get(), CUT_PROPERTY, (stack, level, entity, seed) -> stoneCut(stack));
            ItemProperties.register(ModItems.UNFINISHED_JEWELRY.get(), FORM_PROPERTY, (stack, level, entity, seed) -> carrierForm(stack));
            MenuScreens.register(ModMenus.JEWELRY_SLOTS.get(), JewelrySlotsScreen::new);
        });
    }

    /** 0 without a stone, else the cut's model index / 10 (round 0.1, brilliant 0.2, emerald 0.3, cabochon 0.4). */
    private static float jewelryCut(ItemStack stack) {
        JewelryData data = JewelryData.get(stack).orElse(null);
        if (data == null || data.principal() == null) return 0F;
        return cutIndex(data.principal().cut());
    }

    /** Unfinished jewelry draws its frame's form: ring 0.1, amulet 0.2, bracelet 0.3, circlet 0.4. */
    private static float carrierForm(ItemStack stack) {
        return com.otectus.immersivejewelry.item.UnfinishedJewelryItem.contents(stack).map(c -> {
            ResourceLocation form = c.frame().getItem() instanceof com.otectus.immersivejewelry.item.JewelryBlankItem blank ? blank.form()
                    : c.frame().getItem() instanceof com.otectus.immersivejewelry.item.JewelryItem item ? item.form() : null;
            if (form == null) return 0.1F;
            return switch (form.getPath()) {
                case "amulet" -> 0.2F;
                case "bracelet" -> 0.3F;
                case "circlet" -> 0.4F;
                default -> 0.1F;
            };
        }).orElse(0.1F);
    }

    private static float stoneCut(ItemStack stack) {
        GemData data = GemData.load(JewelryNbt.root(stack)).orElse(null);
        return data == null ? 0.1F : cutIndex(data.cut());
    }

    private static float cutIndex(ResourceLocation cut) {
        CutDefinition def = JewelryDefinitions.client().cut(cut);
        int index = def != null ? def.modelIndex() : cut == null ? 1 : CutDefinition.modelIndex(cut.getPath());
        return index / 10F;
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LAPIDARY_WHEEL.get(), LapidaryWheelRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.JEWELERS_BENCH.get(), JewelersBenchRenderer::new);
    }

    private static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GEM_DUST.get(), sprites -> new GemParticle.Provider(sprites, GemParticle.Kind.DUST));
        event.registerSpriteSet(ModParticles.GEM_SPARKLE.get(), sprites -> new GemParticle.Provider(sprites, GemParticle.Kind.SPARKLE));
        event.registerSpriteSet(ModParticles.METAL_SPARK.get(), GemParticle.SparkProvider::new);
    }

    private static void onModelsReloaded(ModelEvent.BakingCompleted event) {
        JewelryColors.clearSamples();
    }

    private static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SLOTS);
    }

    private ClientSetup() {}
}
