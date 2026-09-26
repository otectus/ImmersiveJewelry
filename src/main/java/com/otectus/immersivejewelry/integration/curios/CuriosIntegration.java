package com.otectus.immersivejewelry.integration.curios;

import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.effect.JewelryWearers;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Curios as the equipment layer (spec 20). Jewelry registers as a curio from here, so the item classes never touch
 * Curios. Slots are Curios data ({@code data/immersive_jewelry/curios}): ring x2, necklace, bracelet, head. The amulet
 * is also accepted in the {@code charm} slot, where Jewelcraft Reforged kept amulets.
 */
public final class CuriosIntegration {

    public static void init() {
        ICurioItem curio = new JewelryCurio();
        for (var item : new JewelryItem[]{ModItems.RING.get(), ModItems.AMULET.get(), ModItems.BRACELET.get(), ModItems.CIRCLET.get()}) {
            CuriosApi.registerCurio(item, curio);
        }
        ImmersiveJewelryAPI.registerEquipmentProvider((player, out) -> CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
            for (SlotResult r : inv.findCurios(stack -> stack.getItem() instanceof JewelryItem)) {
                SlotContext c = r.slotContext();
                if (!c.cosmetic()) out.accept("curios:" + c.identifier() + "/" + c.index(), r.stack());
            }
        }));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, CurioChangeEvent.class, e -> {
            if (e.getEntity() instanceof Player player) JewelryWearers.markDirty(player);
        });
    }

    /** Behaviour of every jewelry item as a curio. Effects are applied by the jewelry runtime, not per curio tick. */
    private static final class JewelryCurio implements ICurioItem {

        @Override
        public boolean canEquip(SlotContext slot, ItemStack stack) {
            if (!(stack.getItem() instanceof JewelryItem item) || JewelryData.get(stack).isEmpty()) return false;
            FormDefinition form = JewelryDefinitions.current().form(item.form());
            String type = form != null ? form.slot() : FormDefinition.fallback(item.form(), item.form()).slot();
            String id = slot.identifier();
            return id.equals(type) || ("necklace".equals(type) && "charm".equals(id)) || "curio".equals(id);
        }

        @Override
        public boolean canEquipFromUse(SlotContext slot, ItemStack stack) {
            return true;
        }

        @Override
        public ICurio.SoundInfo getEquipSound(SlotContext slot, ItemStack stack) {
            return new ICurio.SoundInfo(ModSounds.JEWELRY_EQUIP.get(), 0.8F, 1.0F);
        }

        @Override
        public boolean makesPiglinsNeutral(SlotContext slot, ItemStack stack) {
            return JewelryData.get(stack).map(d -> JewelryDefinitions.current().metal(d.metal()).piglinNeutral()).orElse(false);
        }

        @Override
        public void onEquip(SlotContext slot, ItemStack previous, ItemStack stack) {
            if (slot.entity() instanceof Player player) JewelryWearers.markDirty(player);
        }

        @Override
        public void onUnequip(SlotContext slot, ItemStack next, ItemStack stack) {
            if (slot.entity() instanceof Player player) JewelryWearers.markDirty(player);
        }
    }

    private CuriosIntegration() {}
}
