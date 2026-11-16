package com.otectus.immersivejewelry.event;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.command.JewelryCommands;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
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

/** Forge-bus upkeep: operator leases and every way out of a workstation, commands and trades. */
@Mod.EventBusSubscriber(modid = ImmersiveJewelry.MOD_ID)
public final class CommonEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) WorkSessionManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WorkSessionManager.onLogout(player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WorkSessionManager.onDeath(player);
    }

    /** Damage releases tool contact and leaves the station at once. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!event.isCanceled() && event.getAmount() > 0F && event.getEntity() instanceof ServerPlayer player) WorkSessionManager.onInterrupted(player);
    }

    /** Displacement: teleports and dimension changes end the work at once (the real body left the station). */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onTeleport(net.minecraftforge.event.entity.EntityTeleportEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) WorkSessionManager.onInterrupted(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        com.otectus.immersivejewelry.recipe.DesignKnowledge.sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WorkSessionManager.onInterrupted(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WorkSessionManager.onInterrupted(player);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WorkSessionManager.onServerStopping(event.getServer());
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
