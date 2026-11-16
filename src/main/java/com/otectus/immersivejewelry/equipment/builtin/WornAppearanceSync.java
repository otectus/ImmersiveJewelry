package com.otectus.immersivejewelry.equipment.builtin;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.WornAppearancePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps other clients' picture of what a player wears in the built-in slots: a sanitized snapshot
 * ({@link WornAppearancePacket}) sent at most once a tick after a change, and to each player who starts tracking them.
 * Nothing is sent while Curios is the equipment system (Curios syncs its own slots).
 */
@Mod.EventBusSubscriber(modid = ImmersiveJewelry.MOD_ID)
public final class WornAppearanceSync {
    private static final Set<Player> DIRTY = Collections.newSetFromMap(new WeakHashMap<>());

    /** The built-in slots of {@code player} changed (server). */
    public static void markDirty(Player player) {
        if (!player.level().isClientSide) DIRTY.add(player);
    }

    static WornAppearancePacket snapshot(Player player) {
        List<ItemStack> slots = new ArrayList<>();
        BuiltinSlots.get(player).ifPresent(s -> {
            for (int i = 0; i < s.getSlots() && i < WornAppearancePacket.MAX_SLOTS; i++) slots.add(WornAppearancePacket.sanitize(s.getStackInSlot(i)));
        });
        return new WornAppearancePacket(player.getId(), slots);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !DIRTY.remove(player)) return;
        if (!JewelryEquipment.builtinActive()) return;
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), snapshot(player));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!JewelryEquipment.builtinActive() || !(event.getTarget() instanceof Player target) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), snapshot(target));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        markDirty(event.getEntity());
    }

    private WornAppearanceSync() {}
}
