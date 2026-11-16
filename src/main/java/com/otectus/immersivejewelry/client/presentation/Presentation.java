package com.otectus.immersivejewelry.client.presentation;

import com.otectus.immersivejewelry.client.presentation.anim.JewelerPoses;
import com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client wiring of the stations' presentation libraries: the GeckoLib rigs (resource reload, forgetting a world) and the
 * jeweler's body poses on playerAnimator (the per-player layer factory, the tick that picks clips). Reached only from
 * {@code ClientSetup}.
 */
public final class Presentation {
    public static void init(IEventBus modBus) {
        modBus.addListener(Presentation::onClientSetup);
        modBus.addListener(Presentation::onReloadListeners);
        modBus.addListener(Presentation::onAddLayers);
        MinecraftForge.EVENT_BUS.addListener(JewelerPoses::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(Presentation::onLogout);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(JewelerPoses::register);
        // Curios draws its own slots through the shared worn-jewelry renderer.
        if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
            event.enqueueWork(com.otectus.immersivejewelry.client.presentation.worn.CuriosWornRenderers::register);
        }
    }

    /** The built-in slots' worn-jewelry layer on both player models (wide and slim). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onAddLayers(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.LivingEntityRenderer renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) {
                player.addLayer(new com.otectus.immersivejewelry.client.presentation.worn.WornJewelryLayer(player));
            }
        }
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((barrier, manager, prep, apply, prepExec, applyExec) ->
                barrier.wait(null).thenRunAsync(() -> {
                    JewelryRigs.reload();
                    JewelerPoses.reload();
                }, applyExec));
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        JewelerPoses.reset();
        JewelryRigs.forget();
        com.otectus.immersivejewelry.client.presentation.worn.WornAppearance.clear();
    }

    private Presentation() {}
}
