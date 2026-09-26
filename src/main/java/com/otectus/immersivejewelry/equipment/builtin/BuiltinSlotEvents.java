package com.otectus.immersivejewelry.equipment.builtin;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Attaches, copies and drops the built-in slots. Inactive while Curios is the equipment system. */
@Mod.EventBusSubscriber(modid = ImmersiveJewelry.MOD_ID)
public final class BuiltinSlotEvents {

    @SubscribeEvent
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (!JewelryEquipment.builtinActive() || !(event.getObject() instanceof Player player)) return;
        BuiltinSlots.Provider provider = new BuiltinSlots.Provider(player);
        event.addCapability(BuiltinSlots.KEY, provider);
        event.addListener(provider::invalidate);
    }

    /** Respawn and return from the End: the new player entity takes over the slots (after death only with keepInventory). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!JewelryEquipment.builtinActive()) return;
        Player original = event.getOriginal();
        original.reviveCaps();
        BuiltinSlots.get(original).ifPresent(from -> BuiltinSlots.get(event.getEntity()).ifPresent(to -> {
            for (int i = 0; i < from.getSlots() && i < to.getSlots(); i++) to.setStackInSlot(i, from.getStackInSlot(i).copy());
        }));
        original.invalidateCaps();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        if (!JewelryEquipment.builtinActive() || !(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || player.isSpectator()) return;
        BuiltinSlots.get(player).ifPresent(slots -> {
            for (int i = 0; i < slots.getSlots(); i++) {
                ItemStack stack = slots.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), stack.copy());
                item.setPickUpDelay(40);
                event.getDrops().add(item);
                slots.setStackInSlot(i, ItemStack.EMPTY);
            }
        });
    }

    private BuiltinSlotEvents() {}
}
