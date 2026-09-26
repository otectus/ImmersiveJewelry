package com.otectus.immersivejewelry.event;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.command.JewelryCommands;
import com.otectus.immersivejewelry.minigame.SessionManager;
import com.otectus.immersivejewelry.villager.JewelerTrades;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus upkeep: minigame sessions, commands and trades. */
@Mod.EventBusSubscriber(modid = ImmersiveJewelry.MOD_ID)
public final class CommonEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) SessionManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SessionManager.onLogout(player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SessionManager.onDeath(player);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SessionManager.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        JewelryCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        JewelerTrades.onVillagerTrades(event);
    }

    private CommonEvents() {}
}
